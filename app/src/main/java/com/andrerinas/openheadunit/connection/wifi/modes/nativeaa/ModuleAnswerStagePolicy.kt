package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * The module route speaks blind into an empty car. Only a phone the module reported
 * may move the status pill to "phone answered".
 */
object ModuleAnswerStagePolicy {
    fun reportsPhoneAnswered(peerReportedPresent: Boolean): Boolean = peerReportedPresent
}
