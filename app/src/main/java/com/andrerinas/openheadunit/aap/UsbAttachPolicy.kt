package com.andrerinas.openheadunit.aap

/**
 * Decides whether an attached Android phone should be switched into accessory mode.
 *
 * The gate this replaces asked only whether the vendor id was Google's (0x18D1). That is the
 * vendor of a Pixel and of a phone that has *already* re-enumerated into accessory mode, so on
 * every other make the automatic paths refused the phone and only the manual button in the
 * running app could connect it. A reporter's capture shows the whole ritual it produces: the
 * attach is handed to us, "Skipping device SAMSUNG SAMSUNG_Android (04E8:6860)" is logged 5 ms
 * later, the phone is never claimed, and the user goes to Settings to clear the app's defaults
 * so the chooser comes back and they can drive it by hand.
 *
 * The allow list keeps its job, which is to stop us switching a head unit's *internal*
 * Android-based module rather than the driver's phone. It only has that job once the user has
 * actually configured it: an empty list means "never configured", not "nothing is permitted".
 * That fallback is the same one the single-USB auto-connect already makes.
 *
 * The caller is expected to have established that this is an Android device at all
 * (`UsbDeviceCompat.isAndroidDevice`, an interface class check rather than a vendor list).
 */
object UsbAttachPolicy {

    /**
     * @param isGoogleVendor        vendor id is 0x18D1; a Pixel, or an Android Auto dongle
     * @param autoStartOnUsb        the user asked us to start on any USB attach
     * @param allowListConfigured   the user has marked at least one device as allowed
     * @param deviceAllowed         this device is on that list
     */
    fun shouldAttemptAoaSwitch(
        isGoogleVendor: Boolean,
        autoStartOnUsb: Boolean,
        allowListConfigured: Boolean,
        deviceAllowed: Boolean,
    ): Boolean = when {
        isGoogleVendor -> true
        autoStartOnUsb -> true
        !allowListConfigured -> true
        else -> deviceAllowed
    }
}
