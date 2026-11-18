package com.andrerinas.openheadunit.location

/**
 * Speed-dependent volume: how many volume steps above the driver's own level the current speed is
 * worth, like the speed-compensated volume of a factory head unit.
 *
 * Steps rather than a gain curve, because the same steps drive both targets: this head unit's own
 * stream, or the phone's volume through Android Auto volume keys, where a key press is the only
 * lever there is. Steps are relative, so the driver's own level is left alone: whatever was added
 * comes back off as the car slows, whatever the driver changed in between.
 *
 * Pure and unit-tested; [SpeedVolume] feeds it GPS speed and applies the steps.
 */
object SpeedVolumePolicy {

    /**
     * User choice, stored as [value]. Each level adds one step per [kmhPerStep], up to [maxSteps].
     * The speed_volume_help string quotes these numbers in every locale; change them together.
     */
    enum class Mode(val value: Int, val kmhPerStep: Int, val maxSteps: Int) {
        /** The value of an unset preference, so existing installs keep their volume untouched. */
        OFF(0, 0, 0),
        LOW(1, 30, 3),
        HIGH(2, 20, 5);

        companion object {
            private val map = values().associateBy(Mode::value)
            fun fromInt(value: Int): Mode = map[value] ?: OFF
        }
    }

    /**
     * How far below a step's threshold the speed must fall before that step comes off. GPS speed
     * wanders by a few km/h, so without this a car cruising right at a threshold pumps the volume
     * up and down every second.
     */
    const val HYSTERESIS_KMH = 5f

    /**
     * The step count [speedKmh] is worth, given the [current] one. Rising takes the step at its
     * threshold; falling gives it back only [HYSTERESIS_KMH] below it. Turning the mode down or
     * off mid-drive returns the surplus.
     */
    fun targetSteps(mode: Mode, speedKmh: Float, current: Int): Int {
        if (mode == Mode.OFF) return 0
        val up = (speedKmh / mode.kmhPerStep).toInt()
        val down = ((speedKmh + HYSTERESIS_KMH) / mode.kmhPerStep).toInt()
        val target = when {
            up > current -> up
            down < current -> down
            else -> current
        }
        return target.coerceIn(0, mode.maxSteps)
    }

    /** Where the added steps go: the phone's volume keys, or one stream of this head unit. */
    data class Placement(val onPhone: Boolean, val stream: Int, val phone: String? = null)

    enum class Carry { KEEP, DROP, WAIT }

    /**
     * Phone steps carry only to the same phone; another phone starts at 0. A step bound for the
     * phone waits until the session names it, so no step records a null phone. [toPhoneNow] is the setting.
     */
    fun carry(steps: Int, pinned: Placement?, phoneNow: String?, toPhoneNow: Boolean): Carry {
        val onPhone = if (steps != 0 && pinned != null) pinned.onPhone else toPhoneNow
        return when {
            !onPhone -> Carry.KEEP
            phoneNow == null -> Carry.WAIT
            steps == 0 || pinned?.phone == phoneNow -> Carry.KEEP
            else -> Carry.DROP
        }
    }

    /**
     * Where the next step goes. While steps are on, the target and stream they went to stay, so a
     * setting changed mid-drive still takes them off the same place.
     */
    fun placement(steps: Int, pinned: Placement?, phoneNow: Boolean, streamNow: Int, phone: String? = null): Placement =
        if (steps != 0 && pinned != null) pinned else Placement(phoneNow, streamNow, phone)
}
