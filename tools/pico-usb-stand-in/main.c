// OHU Pico USB stand-in: presents one of several USB identities to a head unit, so the app's USB
// identity checks and blacklist can be tested without the real device. See README.md.
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "hardware/flash.h"
#include "hardware/gpio.h"
#include "hardware/structs/ioqspi.h"
#include "hardware/structs/sio.h"
#include "hardware/sync.h"
#include "hardware/watchdog.h"
#include "identities.h"
#include "pico/bootrom.h"
#include "pico/stdlib.h"
#include "tusb.h"

#ifndef LED_PIN
#define LED_PIN 25
#endif
#ifndef KEY_PIN
#define KEY_PIN 23
#endif
// P-1's KEY ties GP23 to 3V3 against a pull-down, so a press reads high. Set 0 for a key to ground.
#ifndef KEY_ACTIVE_HIGH
#define KEY_ACTIVE_HIGH 1
#endif

#define LONG_PRESS_MS 3000
#define PIN_SCAN_MASK (0x3FFFFFFFu & ~(1u << LED_PIN))
// The last sector of the smallest board (2 MB), so one image runs on every flash size.
#define STORE_OFFSET (2u * 1024 * 1024 - FLASH_SECTOR_SIZE)
#define STORE_MAGIC 0x5055484Fu // "OHUP"
// A watchdog scratch word that survives the reboot after an AOA switch but not a power loss, so an
// unplug returns the switcher to its own identity the way a real phone leaves accessory mode.
#define ONESHOT_MAGIC 0xA0A00000u

uint8_t g_identity = ID_DIAG;
static uint8_t stored_identity = ID_DIAG;
static bool oneshot_accessory = false;
static uint32_t reboot_at_ms = 0;

static const char *const names[ID_COUNT] = {
    "diag console (2E8A:000A, CDC)",
    "CCPA accessory (1314:1520, FF/F0/00)",
    "CCPA NCM (1314:1520, CDC-NCM)",
    "AOA accessory (18D1:2D00, FF/FF/00 named)",
    "nameless vendor (1314:1521, FF/FF/00, refuses AOA)",
    "AOA switcher (1314:1521, FF/FF/00, switches to 3)",
};

const char *identity_name(uint8_t id) { return id < ID_COUNT ? names[id] : "unknown"; }

// A stored identity is tied to the build that stored it, so a new flash always starts on the console.
static uint32_t build_stamp(void) {
    const char *s = __DATE__ __TIME__;
    uint32_t h = 2166136261u;
    while (*s) h = (h ^ (uint8_t)*s++) * 16777619u;
    return h;
}

static uint8_t load_identity(void) {
    const uint32_t *p = (const uint32_t *)(XIP_BASE + STORE_OFFSET);
    if (p[0] == STORE_MAGIC && p[1] < ID_COUNT && p[2] == build_stamp()) return (uint8_t)p[1];
    return ID_DIAG;
}

static void save_identity_and_reboot(uint8_t id) {
    static uint8_t page[FLASH_PAGE_SIZE];
    memset(page, 0xFF, sizeof page);
    uint32_t words[3] = {STORE_MAGIC, id, build_stamp()};
    memcpy(page, words, sizeof words);
    uint32_t irq = save_and_disable_interrupts();
    flash_range_erase(STORE_OFFSET, FLASH_SECTOR_SIZE);
    flash_range_program(STORE_OFFSET, page, FLASH_PAGE_SIZE);
    restore_interrupts(irq);
    watchdog_hw->scratch[0] = 0;
    watchdog_reboot(0, 0, 10);
    while (true) tight_loop_contents();
}

// Reads BOOTSEL by floating the flash chip select, so it must run from RAM with interrupts off.
static bool __no_inline_not_in_flash_func(bootsel_pressed)(void) {
    const uint CS_PIN_INDEX = 1;
    uint32_t irq = save_and_disable_interrupts();
    hw_write_masked(&ioqspi_hw->io[CS_PIN_INDEX].ctrl,
                    GPIO_OVERRIDE_LOW << IO_QSPI_GPIO_QSPI_SS_CTRL_OEOVER_LSB,
                    IO_QSPI_GPIO_QSPI_SS_CTRL_OEOVER_BITS);
    for (volatile int i = 0; i < 1000; ++i);
    bool high = sio_hw->gpio_hi_in & (1u << 1);
    hw_write_masked(&ioqspi_hw->io[CS_PIN_INDEX].ctrl,
                    GPIO_OVERRIDE_NORMAL << IO_QSPI_GPIO_QSPI_SS_CTRL_OEOVER_LSB,
                    IO_QSPI_GPIO_QSPI_SS_CTRL_OEOVER_BITS);
    restore_interrupts(irq);
    return !high;
}

static uint32_t flash_capacity_bytes(uint8_t jedec[3]) {
    uint8_t tx[4] = {0x9F, 0, 0, 0}, rx[4] = {0};
    uint32_t irq = save_and_disable_interrupts();
    flash_do_cmd(tx, rx, 4);
    restore_interrupts(irq);
    memcpy(jedec, rx + 1, 3);
    return (rx[3] >= 16 && rx[3] <= 28) ? (1u << rx[3]) : 0;
}

//--------------------------------------------------------------------+
// Buttons and LED
//--------------------------------------------------------------------+

static void buttons_task(uint32_t now) {
    static uint32_t last_poll = 0, pressed_since = 0;
    // tracking stays false for a press that began before boot, such as the hold that led here.
    static bool stable = true, last_raw = true, tracking = false;
    static uint8_t same = 0;
    if (now - last_poll < 20) return;
    last_poll = now;

    // A contact gap mid-hold would read as a release, so a change counts only after 3 equal reads.
    bool raw = gpio_get(KEY_PIN) == KEY_ACTIVE_HIGH || bootsel_pressed();
    same = raw == last_raw ? (same < 3 ? same + 1 : 3) : 1;
    last_raw = raw;
    if (same < 3 || raw == stable) {
        if (tracking && now - pressed_since >= LONG_PRESS_MS) save_identity_and_reboot(ID_DIAG);
        return;
    }
    stable = raw;
    if (stable) {
        pressed_since = now;
        tracking = true;
    } else if (tracking) {
        // A short press steps through the device identities and never lands on the console.
        uint8_t next = stored_identity + 1;
        if (next >= ID_COUNT || stored_identity == ID_DIAG) next = ID_CCPA_ACCESSORY;
        save_identity_and_reboot(next);
    }
}

// The identity number as that many short blinks, then a pause; the console blinks slowly instead.
static void led_task(uint32_t now) {
    if (g_identity == ID_DIAG) {
        gpio_put(LED_PIN, (now / 1000) % 2);
        return;
    }
    uint32_t slot = (now / 200) % (2 * g_identity + 8);
    gpio_put(LED_PIN, slot < 2u * g_identity && slot % 2 == 0);
}

//--------------------------------------------------------------------+
// AOA control requests (ID_NAMELESS refuses them, ID_SWITCHER takes them)
//--------------------------------------------------------------------+

#define AOA_GET_PROTOCOL 51
#define AOA_SEND_STRING 52
#define AOA_START 53

static uint8_t aoa_buf[256];
static uint16_t aoa_protocol = 2;

bool tud_vendor_control_xfer_cb(uint8_t rhport, uint8_t stage, tusb_control_request_t const *req) {
    if (g_identity != ID_SWITCHER) return false; // stall, as a device without AOA does
    switch (req->bRequest) {
    case AOA_GET_PROTOCOL:
        if (stage == CONTROL_STAGE_SETUP) {
            return tud_control_xfer(rhport, req, &aoa_protocol, 2);
        }
        return true;
    case AOA_SEND_STRING:
        if (stage == CONTROL_STAGE_SETUP) {
            uint16_t len = req->wLength < sizeof aoa_buf ? req->wLength : sizeof aoa_buf;
            return tud_control_xfer(rhport, req, aoa_buf, len);
        }
        return true;
    case AOA_START:
        if (stage == CONTROL_STAGE_SETUP) {
            return tud_control_status(rhport, req);
        }
        if (stage == CONTROL_STAGE_ACK) {
            // Leave the bus after the status stage, then come back as the accessory.
            watchdog_hw->scratch[0] = ONESHOT_MAGIC | ID_AOA_ACCESSORY;
            reboot_at_ms = to_ms_since_boot(get_absolute_time()) + 50;
        }
        return true;
    default:
        return false;
    }
}

//--------------------------------------------------------------------+
// Vendor data: drain whatever the host sends, answer nothing
//--------------------------------------------------------------------+

void tud_vendor_rx_cb(uint8_t itf, uint8_t const *buffer, uint16_t bufsize) {
    (void)buffer;
    (void)bufsize;
    tud_vendor_n_read_flush(itf);
}

//--------------------------------------------------------------------+
// NCM: enumerate only, drop every frame
//--------------------------------------------------------------------+

uint8_t tud_network_mac_address[6] = {0x02, 0xA0, 0xB1, 0xC2, 0xD3, 0xE4};

bool tud_network_recv_cb(const uint8_t *src, uint16_t size) {
    (void)src;
    (void)size;
    tud_network_recv_renew();
    return true;
}

uint16_t tud_network_xmit_cb(uint8_t *dst, void *ref, uint16_t arg) {
    (void)dst;
    (void)ref;
    (void)arg;
    return 0;
}

void tud_network_init_cb(void) {}

//--------------------------------------------------------------------+
// Diag console
//--------------------------------------------------------------------+

static void console_printf(const char *fmt, ...) {
    char line[160];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(line, sizeof line, fmt, ap);
    va_end(ap);
    if (n <= 0) return;
    tud_cdc_write(line, (uint32_t)(n < (int)sizeof line ? n : (int)sizeof line - 1));
    tud_cdc_write_flush();
}

static void console_info(void) {
    uint8_t jedec[3];
    uint32_t cap = flash_capacity_bytes(jedec);
    uint8_t uid[8];
    uint32_t irq = save_and_disable_interrupts();
    flash_get_unique_id(uid);
    restore_interrupts(irq);
    console_printf("\r\nOHU Pico USB stand-in, built %s %s\r\n", __DATE__, __TIME__);
    console_printf("flash: JEDEC %02x %02x %02x, %lu KB\r\n", jedec[0], jedec[1], jedec[2],
                   (unsigned long)(cap / 1024));
    console_printf("board id: %02x%02x%02x%02x%02x%02x%02x%02x\r\n", uid[0], uid[1], uid[2],
                   uid[3], uid[4], uid[5], uid[6], uid[7]);
    console_printf("stored identity: %u, %s\r\n", stored_identity, identity_name(stored_identity));
    console_printf("pins: LED GP%d, KEY GP%d active %s (KEY now %s)\r\n", LED_PIN, KEY_PIN,
                   KEY_ACTIVE_HIGH ? "high" : "low",
                   gpio_get(KEY_PIN) == KEY_ACTIVE_HIGH ? "pressed" : "released");
    for (uint8_t i = 0; i < ID_COUNT; i++) console_printf("  %u  %s\r\n", i, identity_name(i));
    console_printf("type 1-5 to switch and reboot, b for BOOTSEL, i for this text; GPIO changes print as they happen\r\n");
}

// Reports every GPIO that changes, so a board's KEY pin can be found by pressing it.
static void pin_scan_task(void) {
    static uint32_t last = 0xFFFFFFFFu;
    uint32_t now = gpio_get_all() & PIN_SCAN_MASK;
    if (last != 0xFFFFFFFFu) {
        uint32_t changed = now ^ last;
        for (uint pin = 0; pin < 30; pin++) {
            if (changed & (1u << pin)) console_printf("GP%u %s\r\n", pin, (now >> pin) & 1 ? "high" : "low");
        }
    }
    last = now;
}

static void console_task(void) {
    static bool was_connected = false;
    bool connected = tud_cdc_connected();
    if (connected && !was_connected) console_info();
    was_connected = connected;
    if (connected) pin_scan_task();

    while (tud_cdc_available()) {
        char c = (char)tud_cdc_read_char();
        if (c >= '1' && c < '0' + ID_COUNT) {
            console_printf("switching to %c and rebooting\r\n", c);
            sleep_ms(20);
            save_identity_and_reboot((uint8_t)(c - '0'));
        } else if (c == 'b') {
            console_printf("rebooting to BOOTSEL\r\n");
            sleep_ms(20);
            reset_usb_boot(0, 0);
        } else if (c == 'i') {
            console_info();
        }
    }
}

int main(void) {
    gpio_init(LED_PIN);
    gpio_set_dir(LED_PIN, GPIO_OUT);
    gpio_init(KEY_PIN);
    gpio_set_dir(KEY_PIN, GPIO_IN);
    if (KEY_ACTIVE_HIGH) gpio_pull_down(KEY_PIN); else gpio_pull_up(KEY_PIN);

    stored_identity = load_identity();
    uint32_t scratch = watchdog_hw->scratch[0];
    watchdog_hw->scratch[0] = 0;
    oneshot_accessory = (scratch & 0xFFFF0000u) == ONESHOT_MAGIC && stored_identity == ID_SWITCHER;
    g_identity = oneshot_accessory ? ID_AOA_ACCESSORY : stored_identity;

    if (stored_identity == ID_DIAG) {
        // Only the console scans for the KEY pin; device identities leave the other pins alone.
        for (uint pin = 0; pin < 30; pin++) {
            if (pin == LED_PIN || pin == KEY_PIN) continue;
            gpio_init(pin);
            gpio_pull_up(pin);
        }
    }

    tusb_init();
    while (true) {
        tud_task();
        uint32_t now = to_ms_since_boot(get_absolute_time());
        if (reboot_at_ms && now >= reboot_at_ms) {
            watchdog_reboot(0, 0, 10); // re-arming it every pass would postpone it forever
            while (true) tight_loop_contents();
        }
        buttons_task(now);
        led_task(now);
        if (g_identity == ID_DIAG) console_task();
    }
}
