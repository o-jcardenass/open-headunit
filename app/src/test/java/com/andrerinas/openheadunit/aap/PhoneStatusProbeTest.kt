package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.messages.UpdateUiConfigProbeRequest
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.CodedOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PhoneStatusProbeTest {

    private fun call(state: Int, number: String): ByteArray {
        val b = ByteArrayOutputStream()
        val o = CodedOutputStream.newInstance(b)
        o.writeEnum(1, state)
        o.writeString(3, number)
        o.flush()
        return b.toByteArray()
    }

    private fun status(vararg calls: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        val o = CodedOutputStream.newInstance(b)
        calls.forEach { o.writeByteArray(1, it) }
        o.writeUInt32(2, 4)
        o.flush()
        return b.toByteArray()
    }

    @Test
    fun `one line per call with the raw state and no number`() {
        val data = status(call(2, "5551234"), call(6, "5559999"))
        val lines = PhoneStatusProbe.lines(32769, data, 0, data.size)
        assertEquals(listOf("[DEBUG-phonestatus] state=2", "[DEBUG-phonestatus] state=6"), lines)
        assertFalse(lines.joinToString().contains("555"))
    }

    @Test
    fun `an offset skips the header bytes`() {
        val body = status(call(1, "x"))
        val data = ByteArray(3) + body
        assertEquals(listOf("[DEBUG-phonestatus] state=1"), PhoneStatusProbe.lines(32769, data, 3, body.size))
    }

    @Test
    fun `another type prints its type and size`() {
        assertEquals(listOf("[DEBUG-phonestatus] msg=32770 bytes=7"), PhoneStatusProbe.lines(32770, ByteArray(9), 2, 7))
    }

    @Test
    fun `a truncated message does not throw`() {
        val lines = PhoneStatusProbe.lines(32769, byteArrayOf(0x0a, 0x20, 0x08), 0, 3)
        assertTrue(lines.single().startsWith("[DEBUG-phonestatus] msg=32769"))
    }

    @Test
    fun `insets kind sets content insets only`() {
        val req = UpdateUiConfigProbeRequest.build("insets", 1, 2, 3, 4) as Media.UpdateUiConfigRequest
        assertTrue(req.uiConfig.hasContentInsets())
        assertFalse(req.uiConfig.hasMargins())
        assertFalse(req.uiConfig.hasUiTheme())
        assertEquals(4, req.uiConfig.contentInsets.bottom)
    }

    @Test
    fun `margins kind sets margins only`() {
        val req = UpdateUiConfigProbeRequest.build("margins", 0, 0, 0, 9) as Media.UpdateUiConfigRequest
        assertTrue(req.uiConfig.hasMargins())
        assertFalse(req.uiConfig.hasContentInsets())
        assertFalse(req.uiConfig.hasUiTheme())
    }

    @Test
    fun `reply describing prints both insets`() {
        val reply = Media.UpdateUiConfigReply.newBuilder().setUiConfig(
            Media.UiConfig.newBuilder().setContentInsets(
                Media.Insets.newBuilder().setLeft(0).setTop(0).setRight(0).setBottom(5)
            )
        ).build().toByteArray()
        assertEquals(
            "[DEBUG-uiconfig] ack margins=none insets=0,0,0,5",
            PhoneStatusProbe.describeUiConfigReply(reply, 0, reply.size)
        )
    }
}
