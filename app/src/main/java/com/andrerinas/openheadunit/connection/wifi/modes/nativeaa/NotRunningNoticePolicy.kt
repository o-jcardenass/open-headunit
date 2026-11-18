package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/** Whether a press that found the handshake servers stopped tells the person so. */
object NotRunningNoticePolicy {
    /** A bring-up in flight (measuring the daemon or enabling the adapter) is working, not failing. */
    fun shows(measuringDaemon: Boolean, attemptInFlight: Boolean?, notStartedReason: String?): Boolean =
        !measuringDaemon && attemptInFlight != true && notStartedReason != null
}
