package com.andrerinas.openheadunit.input

/** Where a key delivery to `CommManager.sendKey` came from; the policies and the log read it. */
object KeySource {
    const val UNKNOWN = "unknown"
    const val PROJECTION = "projection"
    const val VOLUME_TO_PHONE = "volume-to-phone"
    const val KEY_BROADCAST = "key-broadcast"
    const val GESTURE = "gesture"
    const val MEDIA_SESSION = "mediasession"
    const val MEDIA_KEY_INTENT = "media-key-intent"
    const val AUTO_RESUME = "auto-resume"
    const val TRANSPORT_CONTROL = "transport-control"
    const val REMOTE_COMMAND = "remote-command"
    const val ASSISTANT = "assistant"
    const val CARKEY = "carkey"
}
