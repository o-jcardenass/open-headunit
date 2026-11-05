package com.andrerinas.openheadunit.decoder.audio

/** Says when the output route label is new, so the log shows each route once and no repeat. */
internal class AudioRouteReport {
    private var last: String? = null

    fun changed(label: String?): String? {
        if (label == null || label == last) return null
        last = label
        return label
    }
}
