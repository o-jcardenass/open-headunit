package com.andrerinas.openheadunit.probe

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Debug-only probe for issue 931: can this app open a USB Bluetooth controller and run HCI on it.
 * `am start -n <pkg>/com.andrerinas.openheadunit.probe.UsbHciProbeActivity [--es page <MAC>]`.
 */
class UsbHciProbeActivity : Activity() {

    private val result = linkedMapOf<String, String>()
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra("page")?.trim()?.takeIf { it.isNotEmpty() }
        log("start sdk=${Build.VERSION.SDK_INT} page=${if (page == null) "none" else "set"}")
        val usb = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usb.deviceList.values.toList()
        log("devices=${devices.size}")
        devices.forEach { log("device ${describe(it)}") }
        val target = devices.firstOrNull { controllerInterface(it) != null }
        if (target == null) {
            finishWith("controller=none")
            return
        }
        result["device"] = "%04x:%04x".format(target.vendorId, target.productId)
        if (usb.hasPermission(target)) {
            result["perm"] = "held"
            start(usb, target, page)
            return
        }
        val action = "$packageName.HCI_PROBE_PERMISSION"
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                log("permission granted=$granted")
                result["perm"] = if (granted) "granted" else "denied"
                if (granted) start(usb, target, page) else finishWith()
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 931, Intent(action).setPackage(packageName), flags)
        log("permission requested")
        usb.requestPermission(target, pi)
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    private fun start(usb: UsbManager, device: UsbDevice, page: String?) {
        Thread({
            try {
                probe(usb, device, page)
            } catch (t: Throwable) {
                log("threw ${t.javaClass.simpleName}: ${t.message}")
                result["error"] = t.javaClass.simpleName
            }
            runOnUiThread { finishWith() }
        }, "UsbHciProbe").start()
    }

    private fun probe(usb: UsbManager, device: UsbDevice, page: String?) {
        val intf = controllerInterface(device)!!
        val conn = usb.openDevice(device)
        if (conn == null) { result["open"] = "failed"; return }
        result["open"] = "ok"
        try {
            result["claim"] = when {
                conn.claimInterface(intf, false) -> "plain"
                conn.claimInterface(intf, true) -> "forced"
                else -> "failed"
            }
            log("claim=${result["claim"]}")
            if (result["claim"] == "failed") return
            val evt = (0 until intf.endpointCount).map { intf.getEndpoint(it) }.firstOrNull {
                it.type == UsbConstants.USB_ENDPOINT_XFER_INT && it.direction == UsbConstants.USB_DIR_IN
            }
            if (evt == null) { result["events"] = "no interrupt endpoint"; return }
            val hci = Hci(conn, intf, evt)
            hci.drain()

            result["reset"] = hci.status(HciProbeCodec.OP_RESET, HciProbeCodec.command(HciProbeCodec.OP_RESET), 3000)
            if (result["reset"] != "0x00") return

            hci.complete(HciProbeCodec.OP_READ_LOCAL_VERSION, HciProbeCodec.command(HciProbeCodec.OP_READ_LOCAL_VERSION), 1000)
                ?.let { HciProbeCodec.localVersion(it) }?.let { v ->
                    result["version"] = "hci=%d lmp=%d manufacturer=0x%04x subver=0x%04x".format(
                        v.hciVersion, v.lmpVersion, v.manufacturer, v.lmpSubversion)
                    if (v.manufacturer == HciProbeCodec.MANUFACTURER_REALTEK) {
                        val rom = hci.complete(HciProbeCodec.OP_RTK_READ_ROM_VERSION,
                            HciProbeCodec.command(HciProbeCodec.OP_RTK_READ_ROM_VERSION), 1000)
                        result["rom"] = rom?.let { HciProbeCodec.hex(it) } ?: "no answer"
                    }
                } ?: run { result["version"] = "no answer" }

            result["addr"] = hci.complete(HciProbeCodec.OP_READ_BD_ADDR, HciProbeCodec.command(HciProbeCodec.OP_READ_BD_ADDR), 1000)
                ?.let { HciProbeCodec.bdAddr(it) } ?: "no answer"

            result["page"] = if (page == null) "skipped" else hci.page(page)
        } finally {
            runCatching { conn.releaseInterface(intf) }
            conn.close()
        }
    }

    /** One controller session: commands on endpoint 0, events read back from [evt]. */
    private inner class Hci(val conn: UsbDeviceConnection, val intf: UsbInterface, val evt: UsbEndpoint) {
        private val assembler = HciProbeCodec.EventAssembler()
        private val queue = ArrayDeque<HciProbeCodec.Event>()
        private val buf = ByteArray(64)

        fun drain() {
            var n = 0
            while (read(100) != null) n++
            if (n > 0) log("drained $n stale events")
        }

        fun send(cmd: ByteArray): Boolean {
            val ret = conn.controlTransfer(0x20, 0x00, 0, intf.id, cmd, cmd.size, 1000)
            log("tx ${HciProbeCodec.hex(cmd, minOf(cmd.size, 3))} ret=$ret")
            return ret == cmd.size
        }

        fun read(timeoutMs: Int): HciProbeCodec.Event? {
            queue.removeFirstOrNull()?.let { return it }
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                val n = conn.bulkTransfer(evt, buf, buf.size, 200)
                if (n < 0) SystemClock.sleep(20)
                if (n > 0) {
                    queue.addAll(assembler.feed(buf, n))
                    queue.removeFirstOrNull()?.let {
                        log("rx event 0x%02x len=%d %s".format(it.code, it.params.size, HciProbeCodec.hex(it.params, minOf(it.params.size, 12))))
                        return it
                    }
                }
            }
            return null
        }

        /** Return parameters of the Command Complete for [opcode], or null on timeout. */
        fun complete(opcode: Int, cmd: ByteArray, timeoutMs: Int): ByteArray? {
            if (!send(cmd)) return null
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                val e = read((deadline - SystemClock.elapsedRealtime()).toInt().coerceAtLeast(1)) ?: continue
                HciProbeCodec.commandComplete(e)?.let { (op, ret) -> if (op == opcode) return ret }
            }
            return null
        }

        fun status(opcode: Int, cmd: ByteArray, timeoutMs: Int): String =
            complete(opcode, cmd, timeoutMs)?.firstOrNull()?.let { "0x%02x".format(it.toInt() and 0xFF) } ?: "timeout"

        /** Pages [mac] and reports the Connection Complete status; drops the link if it formed. */
        fun page(mac: String): String {
            if (!send(HciProbeCodec.createConnection(mac))) return "send failed"
            val deadline = SystemClock.elapsedRealtime() + 20000
            while (SystemClock.elapsedRealtime() < deadline) {
                val e = read(1000) ?: continue
                HciProbeCodec.commandStatus(e)?.let { (op, st) ->
                    if (op == HciProbeCodec.OP_CREATE_CONNECTION && st != 0) return "refused 0x%02x".format(st)
                }
                HciProbeCodec.connectionComplete(e)?.let { (st, handle) ->
                    if (st == 0) {
                        send(HciProbeCodec.disconnect(handle))
                        read(3000)
                    }
                    return "0x%02x".format(st)
                }
            }
            return "timeout"
        }
    }

    private fun controllerInterface(d: UsbDevice): UsbInterface? =
        (0 until d.interfaceCount).map { d.getInterface(it) }.firstOrNull {
            it.interfaceClass == 0xE0 && it.interfaceSubclass == 0x01 && it.interfaceProtocol == 0x01
        }

    private fun describe(d: UsbDevice): String {
        val ifaces = (0 until d.interfaceCount).joinToString(",") {
            val i = d.getInterface(it)
            "%02x/%02x/%02x".format(i.interfaceClass, i.interfaceSubclass, i.interfaceProtocol)
        }
        return "%04x:%04x class=%02x ifaces=[%s]".format(d.vendorId, d.productId, d.deviceClass, ifaces)
    }

    private fun finishWith(extra: String? = null) {
        val line = (listOfNotNull(extra) + result.map { "${it.key}=${it.value}" }).joinToString(" ")
        log("RESULT $line")
        finish()
    }

    private fun log(msg: String) = AppLog.i("UsbHciProbe: $msg")
}
