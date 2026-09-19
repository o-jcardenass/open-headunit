package com.andrerinas.openheadunit.aap

/**
 * What the phone's version response means for the rest of the session.
 *
 * The response was parsed and then dropped: nothing read it back, so a phone that refused outright
 * was walked past into SSL, and messages reserved for a later minor went out unguarded.
 */
object AapVersionPolicy {

    /** `MessageStatus.STATUS_NO_COMPATIBLE_VERSION`. */
    const val STATUS_NO_COMPATIBLE_VERSION = -1

    /**
     * Only -1 aborts. Other negative statuses ride along on version responses that go on to work,
     * so a session must never be thrown away for one.
     */
    fun refusesHandshake(status: Int): Boolean = status == STATUS_NO_COMPATIBLE_VERSION

    /** A message the 1.6 message set added must not reach a phone that selected less. */
    fun withholds16(negotiated: AapVersionNegotiation.Result?): Boolean =
        negotiated == null || !negotiated.supports16
}
