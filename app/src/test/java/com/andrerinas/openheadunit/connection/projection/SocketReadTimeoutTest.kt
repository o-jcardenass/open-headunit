package com.andrerinas.openheadunit.connection.projection

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException

class SocketReadTimeoutTest {
    private class FakeSocket : Socket() {
        val sets = ArrayList<Int>()
        var failNext = false
        override fun getInetAddress(): InetAddress = InetAddress.getByName("192.0.2.1")
        override fun getPort() = 5277
        override fun isConnected() = true
        override fun getInputStream() = ByteArrayInputStream(ByteArray(64))
        override fun getOutputStream() = object : OutputStream() { override fun write(b: Int) {} }
        override fun setSoTimeout(timeout: Int) {
            if (failNext) { failNext = false; throw SocketException("injected") }
            sets.add(timeout)
        }
    }

    private fun connection(socket: Socket) = SocketProjectionConnection(socket, mock(Context::class.java))

    @Test fun `an equal timeout is set once`() {
        val socket = FakeSocket()
        val c = connection(socket)
        val buf = ByteArray(4)
        repeat(3) { c.recvBlocking(buf, 4, 5000, true) }
        assertEquals(listOf(5000), socket.sets)
    }

    @Test fun `a changed timeout is set again`() {
        val socket = FakeSocket()
        val c = connection(socket)
        val buf = ByteArray(4)
        c.recvBlocking(buf, 4, 5000, true)
        c.recvBlocking(buf, 4, 1000, true)
        c.recvBlocking(buf, 4, 1000, true)
        c.recvBlocking(buf, 4, 5000, true)
        assertEquals(listOf(5000, 1000, 5000), socket.sets)
    }

    @Test fun `a set that throws is retried on the next read`() {
        val socket = FakeSocket()
        val c = connection(socket)
        val buf = ByteArray(4)
        socket.failNext = true
        c.recvBlocking(buf, 4, 5000, true)
        c.recvBlocking(buf, 4, 5000, true)
        assertEquals(listOf(5000), socket.sets)
    }
}
