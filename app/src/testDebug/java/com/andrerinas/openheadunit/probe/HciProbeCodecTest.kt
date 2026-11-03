package com.andrerinas.openheadunit.probe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HciProbeCodecTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun resetEncodesToTheSpecBytes() {
        assertArrayEquals(bytes(0x03, 0x0C, 0x00), HciProbeCodec.command(HciProbeCodec.OP_RESET))
    }

    @Test
    fun anEventSplitAcrossPacketsIsRejoined() {
        val a = HciProbeCodec.EventAssembler()
        // Command Complete for Read Local Version, RTL8761B values, split 5 + rest.
        val whole = bytes(0x0E, 0x0C, 0x01, 0x01, 0x10, 0x00, 0x0A, 0x0B, 0x00, 0x0A, 0x5D, 0x00, 0x61, 0x87)
        assertEquals(0, a.feed(whole.copyOfRange(0, 5), 5).size)
        val events = a.feed(whole.copyOfRange(5, whole.size), whole.size - 5)
        assertEquals(1, events.size)
        val (op, ret) = HciProbeCodec.commandComplete(events[0])!!
        assertEquals(HciProbeCodec.OP_READ_LOCAL_VERSION, op)
        val v = HciProbeCodec.localVersion(ret)!!
        assertEquals(0, v.status)
        assertEquals(HciProbeCodec.MANUFACTURER_REALTEK, v.manufacturer)
        assertEquals(0x8761, v.lmpSubversion)
    }

    @Test
    fun twoEventsInOnePacketAreBothReturned() {
        val a = HciProbeCodec.EventAssembler()
        val p = bytes(0x0F, 0x04, 0x00, 0x01, 0x05, 0x04, 0x03, 0x0B, 0x00, 0x2A, 0x00, 1, 2, 3, 4, 5, 6, 0x01, 0x00)
        val events = a.feed(p, p.size)
        assertEquals(2, events.size)
        assertEquals(HciProbeCodec.OP_CREATE_CONNECTION to 0, HciProbeCodec.commandStatus(events[0]))
        assertEquals(0 to 0x2A, HciProbeCodec.connectionComplete(events[1]))
    }

    @Test
    fun bdAddrReadsMostSignificantByteFirst() {
        assertEquals("66:55:44:33:22:11", HciProbeCodec.bdAddr(bytes(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)))
    }

    @Test
    fun createConnectionPutsTheAddressLittleEndian() {
        val cmd = HciProbeCodec.createConnection("AA:BB:CC:DD:EE:FF")
        assertArrayEquals(bytes(0x05, 0x04, 13, 0xFF, 0xEE, 0xDD, 0xCC, 0xBB, 0xAA), cmd.copyOfRange(0, 9))
        assertEquals(16, cmd.size)
    }

    @Test
    fun otherEventsAreNotReadAsCommandComplete() {
        assertNull(HciProbeCodec.commandComplete(HciProbeCodec.Event(0x0F, bytes(0, 1, 3, 0x0C))))
    }
}
