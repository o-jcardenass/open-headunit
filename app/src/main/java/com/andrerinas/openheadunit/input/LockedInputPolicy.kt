package com.andrerinas.openheadunit.input

/**
 * What a person at the unit may still do while the projection is shown over a locked unit
 * ("Show over lock screen" on, and the device needs its PIN, pattern or password).
 *
 * The answer is: nothing that reaches the phone. Android Auto treats the head unit as trusted, so
 * any touch, key or scroll that gets through could open calls, messages or contacts on the phone.
 * Let through: Android Auto's navigation key, which this app sends itself when it locks, and the
 * volume keys, which open nothing. [engaged] is written by the projection activity and read by the
 * transport when it sends, hence volatile; the activity that holds it is its [owner].
 */
object LockedInputPolicy {

    /** Android Auto's "show the map" key; the Keymap screen lists it as Navigation. */
    const val KEYCODE_AA_NAVIGATION = 65538

    /** The first Android version where Back can ask for the unlock ([supported]). */
    private const val MIN_SDK = 26

    /** Android keycodes of the volume keys; they open no call, message or contact. */
    private const val KEYCODE_VOLUME_UP = 24
    private const val KEYCODE_VOLUME_DOWN = 25

    /** True while input to the phone is held back. */
    @Volatile
    var engaged: Boolean = false
        private set

    /** The screen that drives the hold; a relaunch claims it before the old one is destroyed. */
    @Volatile
    private var owner: Any? = null

    /** The option needs `requestDismissKeyguard`, so Back can unlock only from API 26. */
    fun supported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    /**
     * Sets the hold for [by]; true when [engaged] flipped. [claim] (create, resume) makes [by] the
     * owner; any other call from a screen that is not the owner changes nothing, so a replaced
     * screen's late broadcast can neither take the hold back nor clear it.
     */
    fun update(by: Any, engage: Boolean, claim: Boolean = false): Boolean {
        if (claim || owner == null) owner = by
        else if (owner !== by) return false
        val flipped = engaged != engage
        engaged = engage
        return flipped
    }

    /** Clears the hold only when [by] still owns it; a replaced screen's destroy keeps the new one's. */
    fun release(by: Any): Boolean {
        if (owner !== by) return false
        owner = null
        if (!engaged) return false
        engaged = false
        return true
    }

    enum class MapKey { SEND, OWE }

    /** The map key goes out now when the transport runs; otherwise the activity owes it. */
    fun mapKey(transportStarted: Boolean): MapKey = if (transportStarted) MapKey.SEND else MapKey.OWE

    /**
     * Whether the projection should hold input back.
     *
     * @param enabled      the "Show over lock screen" setting.
     * @param deviceLocked the unit needs authentication (PIN, pattern, password). Null when the
     *                     system would not say; that counts as locked.
     * @param screenOff    the panel just went dark. Counts as locked until the next check, so a
     *                     unit that locks a few seconds after the screen goes off is never open in
     *                     between.
     */
    fun shouldEngage(enabled: Boolean, deviceLocked: Boolean?, screenOff: Boolean): Boolean =
        enabled && (screenOff || deviceLocked != false)

    /**
     * Whether [shouldEngage] needs the lock state at all. Asking for it is a call into the system,
     * and the projection asks on every new touch; with the setting off, or with the panel just
     * gone dark, the answer changes nothing.
     */
    fun needsLockState(enabled: Boolean, screenOff: Boolean): Boolean = enabled && !screenOff

    /**
     * Whether one input event may go to the phone.
     *
     * @param keycode the Android Auto keycode for a key event, null for touch, scroll and any other
     *                input event.
     */
    fun allows(engaged: Boolean, keycode: Int?): Boolean =
        !engaged || keycode == KEYCODE_AA_NAVIGATION ||
            keycode == KEYCODE_VOLUME_UP || keycode == KEYCODE_VOLUME_DOWN

    /**
     * Whether a key bound to an action on this unit may run it. Opening another app would take
     * the projection off a locked unit's screen, so it waits for the unlock. Changes to this
     * screen (dimmer, full screen, overlay) stay allowed; the exit menu has its own check, and
     * Android Auto keys go through [allows] when they are sent.
     */
    fun allowsBoundAction(engaged: Boolean, action: InputAction): Boolean =
        !engaged || action !is InputAction.LaunchApp
}
