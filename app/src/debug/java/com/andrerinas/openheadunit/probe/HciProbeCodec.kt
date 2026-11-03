package com.andrerinas.openheadunit.probe

/**
 * HCI framing for the USB dongle probe. Commands go out on endpoint 0, events arrive on the
 * interrupt endpoint in packets that can split one event, so [EventAssembler] rejoins them.
 */
object HciProbeCodec {
    const val OP_RESET = 0x0C03
    const val OP_READ_LOCAL_VERSION = 0x1001
    const val OP_READ_BD_ADDR = 0x1009
    const val OP_CREATE_CONNECTION = 0x0405
    const val OP_DISCONNECT = 0x0406
    const val OP_RTK_READ_ROM_VERSION = 0xFC6D

    const val EVT_CONNECTION_COMPLETE = 0x03
    const val EVT_DISCONNECTION_COMPLETE = 0x05
    const val EVT_COMMAND_COMPLETE = 0x0E
    const val EVT_COMMAND_STATUS = 0x0F

    const val MANUFACTURER_REALTEK = 0x005D

    class Event(val code: Int, val params: ByteArray)

    data class LocalVersion(
        val status: Int, val hciVersion: Int, val hciRevision: Int,
        val lmpVersion: Int, val manufacturer: Int, val lmpSubversion: Int
    )

    fun command(opcode: Int, params: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf((opcode and 0xFF).toByte(), (opcode shr 8 and 0xFF).toByte(), params.size.toByte()) + params

    /** Opcode and return parameters of a Command Complete, or null for any other event. */
    fun commandComplete(e: Event): Pair<Int, ByteArray>? {
        if (e.code != EVT_COMMAND_COMPLETE || e.params.size < 3) return null
        return u16(e.params, 1) to e.params.copyOfRange(3, e.params.size)
    }

    /** Opcode and status of a Command Status, or null for any other event. */
    fun commandStatus(e: Event): Pair<Int, Int>? {
        if (e.code != EVT_COMMAND_STATUS || e.params.size < 4) return null
        return u16(e.params, 2) to (e.params[0].toInt() and 0xFF)
    }

    fun localVersion(ret: ByteArray): LocalVersion? {
        if (ret.size < 9) return null
        return LocalVersion(u8(ret, 0), u8(ret, 1), u16(ret, 2), u8(ret, 4), u16(ret, 5), u16(ret, 7))
    }

    /** BD_ADDR from Read BD_ADDR return parameters, most significant byte first. */
    fun bdAddr(ret: ByteArray): String? {
        if (ret.size < 7) return null
        return (6 downTo 1).joinToString(":") { "%02X".format(u8(ret, it)) }
    }

    fun createConnection(mac: String): ByteArray {
        val addr = macToLittleEndian(mac)
        // Packet types DM1/DH1/DM3/DH3/DM5/DH5, page scan R1, no clock offset, role switch allowed.
        return command(OP_CREATE_CONNECTION, addr + byteArrayOf(0x18, 0xCC.toByte(), 0x01, 0x00, 0x00, 0x00, 0x01))
    }

    fun disconnect(handle: Int): ByteArray =
        command(OP_DISCONNECT, byteArrayOf((handle and 0xFF).toByte(), (handle shr 8 and 0x0F).toByte(), 0x13))

    /** Status and connection handle of a Connection Complete, or null for any other event. */
    fun connectionComplete(e: Event): Pair<Int, Int>? {
        if (e.code != EVT_CONNECTION_COMPLETE || e.params.size < 3) return null
        return u8(e.params, 0) to (u16(e.params, 1) and 0x0FFF)
    }

    fun macToLittleEndian(mac: String): ByteArray {
        val hex = mac.replace(":", "").replace("-", "")
        require(hex.length == 12) { "not a Bluetooth address: $mac" }
        return ByteArray(6) { i -> hex.substring((5 - i) * 2, (5 - i) * 2 + 2).toInt(16).toByte() }
    }

    fun hex(b: ByteArray, len: Int = b.size): String =
        (0 until len).joinToString(" ") { "%02x".format(b[it].toInt() and 0xFF) }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)

    /** Rejoins HCI events that arrive split across interrupt packets. */
    class EventAssembler {
        private var pending = ByteArray(0)

        fun feed(data: ByteArray, len: Int): List<Event> {
            pending += data.copyOf(len)
            val out = mutableListOf<Event>()
            while (pending.size >= 2) {
                val total = 2 + (pending[1].toInt() and 0xFF)
                if (pending.size < total) break
                out += Event(pending[0].toInt() and 0xFF, pending.copyOfRange(2, total))
                pending = pending.copyOfRange(total, pending.size)
            }
            return out
        }
    }
}
