package com.andrerinas.openheadunit.connection.wifi.direct

/**
 * A soft AP left up holds a single radio, so P2P answers BUSY to every create and the ladder
 * cannot win. This decides when to free the radio once before the ladder runs on.
 */
object P2pBusyCausePolicy {
    enum class Step { LADDER, FREE_RADIO }

    fun stepAfterBusy(
        reason: Int,
        accessPointUp: Boolean,
        teardownAllowed: Boolean,
        teardownSpent: Boolean,
    ): Step =
        if (reason == P2pCreateWedgePolicy.BUSY && accessPointUp && teardownAllowed && !teardownSpent) {
            Step.FREE_RADIO
        } else {
            Step.LADDER
        }
}
