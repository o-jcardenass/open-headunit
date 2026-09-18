package com.andrerinas.openheadunit.utils

/**
 * What a mid-session orientation mismatch is allowed to do to the negotiated geometry. The canvas
 * goes out once, in the service discovery response, and Google's own schema says codec and
 * resolution changes belong to service rediscovery rather than to any live message. Re-deriving
 * them locally therefore only makes the scale math describe a canvas the phone never received.
 */
object SessionGeometryLockPolicy {

    enum class Verdict {
        /** Hold the negotiated geometry; the wire cannot be corrected. */
        KEEP,

        /** Re-derive: either nothing is announced yet, or a lever exists that can announce it. */
        RENEGOTIATE,
    }

    /** No lever is wired up yet, so every announced session keeps what it negotiated. */
    const val RENEGOTIATION_AVAILABLE = false

    fun onOrientationMismatch(
        canvasAnnounced: Boolean,
        renegotiationAvailable: Boolean = RENEGOTIATION_AVAILABLE,
    ): Verdict = when {
        !canvasAnnounced -> Verdict.RENEGOTIATE
        renegotiationAvailable -> Verdict.RENEGOTIATE
        else -> Verdict.KEEP
    }
}
