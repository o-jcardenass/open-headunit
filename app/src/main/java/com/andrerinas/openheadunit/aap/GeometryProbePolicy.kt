package com.andrerinas.openheadunit.aap

/**
 * The rotation re-negotiation probe: which lever a rig round fires when the panel rotates under a
 * live session. Every mode is off in shipped configurations, and the whole probe comes out once the
 * round has answered. Google's own schema says a resolution change belongs to service rediscovery
 * and that a later Media.Config supersedes the earlier one; neither has ever been measured.
 */
object GeometryProbePolicy {

    const val OFF = 0

    /** ServiceDiscoveryUpdate, control message 26, re-advertising the video service. */
    const val SERVICE_REDISCOVERY = 1

    /** Media.Config STATUS_READY selecting index 1 straight into a live stream. */
    const val CONFIG_READY = 2

    /** Media.Config STATUS_WAIT, then STATUS_READY selecting index 1. */
    const val CONFIG_WAIT_THEN_READY = 3

    /** Media.Config selecting index 1, then the focus cycle that already restarts the sink. */
    const val CONFIG_THEN_FOCUS_CYCLE = 4

    /** UpdateUiConfigRequest carrying ui_theme, to separate "inert message" from "inert margins". */
    const val UI_THEME = 5

    fun isActive(mode: Int): Boolean = mode != OFF

    /** Only the Config arms need a second configuration to select between. */
    fun announcesSecondConfig(mode: Int): Boolean =
        mode == CONFIG_READY || mode == CONFIG_WAIT_THEN_READY || mode == CONFIG_THEN_FOCUS_CYCLE

    /** A probe has to let the panel rotate, so it lifts the orientation pin that normally holds it. */
    fun liftsOrientationPin(mode: Int): Boolean = isActive(mode)

    /** A probe re-derives the geometry for the new orientation, so it drops the resolution lock. */
    fun renegotiates(mode: Int): Boolean = isActive(mode) && mode != UI_THEME
}
