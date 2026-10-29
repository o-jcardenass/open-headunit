cmake_minimum_required(VERSION 3.13)

include(pico_sdk_import.cmake)
project(ohu_pico_usb_stand_in C CXX ASM)
set(CMAKE_C_STANDARD 11)
pico_sdk_init()

# The board's LED and user key, measured on P-1. On another board, find the key with the console's
# pin scan and override with -DKEY_PIN=n -DKEY_ACTIVE_HIGH=0|1.
set(LED_PIN 25 CACHE STRING "GPIO of the board LED")
set(KEY_PIN 23 CACHE STRING "GPIO of the KEY/USR button")
set(KEY_ACTIVE_HIGH 1 CACHE STRING "1 if a KEY press reads high, 0 if it reads low")

add_executable(ohu_pico_usb_stand_in main.c usb_descriptors.c)
target_include_directories(ohu_pico_usb_stand_in PRIVATE ${CMAKE_CURRENT_LIST_DIR})
target_compile_definitions(ohu_pico_usb_stand_in PRIVATE LED_PIN=${LED_PIN} KEY_PIN=${KEY_PIN}
    KEY_ACTIVE_HIGH=${KEY_ACTIVE_HIGH})
target_link_libraries(ohu_pico_usb_stand_in PRIVATE
    pico_stdlib tinyusb_device hardware_flash hardware_watchdog)
pico_enable_stdio_usb(ohu_pico_usb_stand_in 0)
pico_enable_stdio_uart(ohu_pico_usb_stand_in 0)
pico_add_extra_outputs(ohu_pico_usb_stand_in)
