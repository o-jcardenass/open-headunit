// Descriptors for each identity. Full speed only, so bulk endpoints are 64 bytes where the real
// Carlinkit box, a high-speed gadget, uses 512. Host-side identity checks do not read the size.
#include <string.h>

#include "identities.h"
#include "tusb.h"

#define VID_CARLINKIT 0x1314
#define VID_GOOGLE 0x18D1
#define VID_RPI 0x2E8A

enum {
    STR_LANG = 0,
    STR_MANUFACTURER,
    STR_PRODUCT,
    STR_SERIAL,
    STR_INTERFACE,
    STR_MAC,
};

static const tusb_desc_device_t device_template = {
    .bLength = sizeof(tusb_desc_device_t),
    .bDescriptorType = TUSB_DESC_DEVICE,
    .bcdUSB = 0x0200,
    .bMaxPacketSize0 = CFG_TUD_ENDPOINT0_SIZE,
    .bcdDevice = 0x0100,
    .iManufacturer = STR_MANUFACTURER,
    .iProduct = STR_PRODUCT,
    .iSerialNumber = STR_SERIAL,
    .bNumConfigurations = 1,
};

static tusb_desc_device_t device_desc;

const uint8_t *tud_descriptor_device_cb(void) {
    device_desc = device_template;
    device_desc.bcdDevice = 0x0100 + g_identity; // lets lsusb tell identities 4 and 5 apart
    switch (g_identity) {
    case ID_CCPA_ACCESSORY:
    case ID_CCPA_NCM:
        // The stock box reports Miscellaneous / IAD on the device descriptor.
        device_desc.bDeviceClass = TUSB_CLASS_MISC;
        device_desc.bDeviceSubClass = MISC_SUBCLASS_COMMON;
        device_desc.bDeviceProtocol = MISC_PROTOCOL_IAD;
        device_desc.idVendor = VID_CARLINKIT;
        device_desc.idProduct = 0x1520;
        break;
    case ID_AOA_ACCESSORY:
        device_desc.idVendor = VID_GOOGLE;
        device_desc.idProduct = 0x2D00;
        break;
    case ID_NAMELESS:
    case ID_SWITCHER:
        device_desc.idVendor = VID_CARLINKIT;
        device_desc.idProduct = 0x1521;
        break;
    default: // ID_DIAG
        device_desc.bDeviceClass = TUSB_CLASS_MISC;
        device_desc.bDeviceSubClass = MISC_SUBCLASS_COMMON;
        device_desc.bDeviceProtocol = MISC_PROTOCOL_IAD;
        device_desc.idVendor = VID_RPI;
        device_desc.idProduct = 0x000A;
        break;
    }
    return (const uint8_t *)&device_desc;
}

#define CONFIG_ATTR 0
#define CONFIG_MA 100

// A vendor interface with a bulk pair and a chosen subclass and name; TinyUSB's own template fixes
// the subclass at 0x00.
#define VENDOR_ITF_DESC(_subclass, _stridx)                                                        \
    9, TUSB_DESC_INTERFACE, 0, 0, 2, TUSB_CLASS_VENDOR_SPECIFIC, _subclass, 0x00, _stridx,         \
    7, TUSB_DESC_ENDPOINT, 0x02, TUSB_XFER_BULK, U16_TO_U8S_LE(64), 0,                             \
    7, TUSB_DESC_ENDPOINT, 0x83, TUSB_XFER_BULK, U16_TO_U8S_LE(64), 0

#define VENDOR_CONFIG_LEN (TUD_CONFIG_DESC_LEN + TUD_VENDOR_DESC_LEN)

static const uint8_t config_ccpa_accessory[] = {
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, VENDOR_CONFIG_LEN, CONFIG_ATTR, CONFIG_MA),
    VENDOR_ITF_DESC(0xF0, 0),
};

static const uint8_t config_aoa_accessory[] = {
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, VENDOR_CONFIG_LEN, CONFIG_ATTR, CONFIG_MA),
    VENDOR_ITF_DESC(0xFF, STR_INTERFACE),
};

static const uint8_t config_nameless[] = {
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, VENDOR_CONFIG_LEN, CONFIG_ATTR, CONFIG_MA),
    VENDOR_ITF_DESC(0xFF, 0),
};

#define NCM_CONFIG_LEN (TUD_CONFIG_DESC_LEN + TUD_CDC_NCM_DESC_LEN)

static const uint8_t config_ccpa_ncm[] = {
    TUD_CONFIG_DESCRIPTOR(1, 2, 0, NCM_CONFIG_LEN, CONFIG_ATTR, CONFIG_MA),
    TUD_CDC_NCM_DESCRIPTOR(0, 0, STR_MAC, 0x81, 64, 0x02, 0x82, 64, CFG_TUD_NET_MTU),
};

#define CDC_CONFIG_LEN (TUD_CONFIG_DESC_LEN + TUD_CDC_DESC_LEN)

static const uint8_t config_diag[] = {
    TUD_CONFIG_DESCRIPTOR(1, 2, 0, CDC_CONFIG_LEN, CONFIG_ATTR, CONFIG_MA),
    TUD_CDC_DESCRIPTOR(0, STR_INTERFACE, 0x81, 8, 0x02, 0x82, 64),
};

const uint8_t *tud_descriptor_configuration_cb(uint8_t index) {
    (void)index;
    switch (g_identity) {
    case ID_CCPA_ACCESSORY: return config_ccpa_accessory;
    case ID_CCPA_NCM: return config_ccpa_ncm;
    case ID_AOA_ACCESSORY: return config_aoa_accessory;
    case ID_NAMELESS:
    case ID_SWITCHER: return config_nameless;
    default: return config_diag;
    }
}

static const char *string_for(uint8_t index) {
    bool carlinkit = g_identity != ID_DIAG;
    switch (index) {
    case STR_MANUFACTURER: return carlinkit ? "Magic Communication Tec." : "OHU rig";
    case STR_PRODUCT: return carlinkit ? "Auto Box" : "OHU Pico USB stand-in";
    case STR_SERIAL: return "OHUPICO0001";
    case STR_INTERFACE:
        return g_identity == ID_AOA_ACCESSORY ? "Android Accessory Interface" : "OHU stand-in console";
    case STR_MAC: return "02A0B1C2D3E4"; // locally administered, matches tud_network_mac_address
    default: return NULL;
    }
}

static uint16_t string_buf[40];

const uint16_t *tud_descriptor_string_cb(uint8_t index, uint16_t langid) {
    (void)langid;
    size_t count;
    if (index == STR_LANG) {
        string_buf[1] = 0x0409;
        count = 1;
    } else {
        const char *s = string_for(index);
        if (s == NULL) return NULL;
        count = strlen(s);
        if (count > TU_ARRAY_SIZE(string_buf) - 1) count = TU_ARRAY_SIZE(string_buf) - 1;
        for (size_t i = 0; i < count; i++) string_buf[1 + i] = (uint8_t)s[i];
    }
    string_buf[0] = (uint16_t)((TUSB_DESC_STRING << 8) | (2 * count + 2));
    return string_buf;
}
