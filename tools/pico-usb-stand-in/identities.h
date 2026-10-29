// USB identities the stand-in can present. The numbers are what the diag console takes and what the
// LED blinks, so do not renumber them; add new ones at the end.
#pragma once

#include <stdint.h>

enum {
    ID_DIAG = 0,           // CDC serial console, 2E8A:000A
    ID_CCPA_ACCESSORY = 1, // stock Carlinkit CCPA, 1314:1520, interface FF/F0/00
    ID_CCPA_NCM = 2,       // stock Carlinkit CCPA in NCM mode, 1314:1520, CDC-NCM only
    ID_AOA_ACCESSORY = 3,  // 18D1:2D00, FF/FF/00 named "Android Accessory Interface"
    ID_NAMELESS = 4,       // 1314:1521, nameless FF/FF/00, refuses the AOA switch
    ID_SWITCHER = 5,       // 1314:1521, nameless FF/FF/00, takes the AOA switch and becomes 3
    ID_COUNT
};

extern uint8_t g_identity;

const char *identity_name(uint8_t id);
