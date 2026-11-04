package com.andrerinas.openheadunit.connection

/**
 * "Close app on disconnect" ends a session, not a failed attempt: an attempt that closes the app
 * leaves nothing to retry from, and a peer stuck on the bus closes it again on every launch.
 */
object KillOnDisconnectPolicy {
    fun closesApp(enabled: Boolean, reachedSsl: Boolean, userExit: Boolean): Boolean =
        enabled && (reachedSsl || userExit)
}
