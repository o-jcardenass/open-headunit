package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.CodedInputStream

/**
 * Probe only: reads PhoneStatus with raw wire numbers because `control.proto`'s state enum differs
 * from Google's. It never reads a caller's number or name, since users post logs in public.
 */
object PhoneStatusProbe {

    private const val PHONE_STATUS_MESSAGE = 32769

    fun lines(type: Int, data: ByteArray, offset: Int, length: Int): List<String> {
        if (type != PHONE_STATUS_MESSAGE) return listOf("[DEBUG-phonestatus] msg=$type bytes=$length")
        val out = ArrayList<String>()
        try {
            val input = CodedInputStream.newInstance(data, offset, length)
            while (true) {
                val tag = input.readTag()
                if (tag == 0) break
                if (tag == (1 shl 3) or 2) out.add("[DEBUG-phonestatus] state=${callState(input.readByteArray())}")
                else input.skipField(tag)
            }
        } catch (e: java.io.IOException) {
            out.add("[DEBUG-phonestatus] msg=$type bytes=$length unparsed")
        }
        return out
    }

    /** The raw state integer of one call, or -1 when the call carries none. */
    private fun callState(call: ByteArray): Int {
        val input = CodedInputStream.newInstance(call)
        var state = -1
        while (true) {
            val tag = input.readTag()
            if (tag == 0) break
            if (tag == (1 shl 3)) state = input.readEnum() else input.skipField(tag)
        }
        return state
    }

    fun describeUiConfigReply(data: ByteArray, offset: Int, length: Int): String {
        val cfg = try {
            Media.UpdateUiConfigReply.newBuilder().mergeFrom(data, offset, length).build().uiConfig
        } catch (e: java.io.IOException) {
            return "[DEBUG-uiconfig] ack unparsed bytes=$length"
        }
        fun ins(has: Boolean, i: Media.Insets) = if (has) "${i.left},${i.top},${i.right},${i.bottom}" else "none"
        return "[DEBUG-uiconfig] ack margins=${ins(cfg.hasMargins(), cfg.margins)} " +
            "insets=${ins(cfg.hasContentInsets(), cfg.contentInsets)}"
    }
}
