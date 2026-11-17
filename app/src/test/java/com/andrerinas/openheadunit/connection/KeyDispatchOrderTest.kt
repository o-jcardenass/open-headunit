package com.andrerinas.openheadunit.connection

import com.andrerinas.openheadunit.input.InputAction
import com.andrerinas.openheadunit.input.KeyBindingPolicy
import com.andrerinas.openheadunit.input.LocalAction
import com.andrerinas.openheadunit.input.MediaKeyRoutingPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One car key press reaches CommManager.sendKey by several routes, and a bound action must still
 * run once. A gesture bound to a media key never reaches the Bluetooth side, so routing must not hold it back.
 */
class KeyDispatchOrderTest {

    private val next = 87 // KeyEvent.KEYCODE_MEDIA_NEXT

    /** The steps of CommManager.sendKey and sendLogicalKey, in their order, around the real policies. */
    private class Dispatch(
        private val bindings: Map<Int, InputAction>,
        private val routing: MediaKeyRoutingPolicy.Mode = MediaKeyRoutingPolicy.Mode.ALWAYS,
        private val btMediaLink: Boolean? = false
    ) {
        val ran = mutableListOf<InputAction>()
        val sent = mutableListOf<Pair<Int, Boolean>>()
        private val keyStates = mutableMapOf<Int, KeyDebouncePolicy.KeyState>()
        private val boundStates = mutableMapOf<Int, KeyDebouncePolicy.KeyState>()

        private fun admitsBound(keyCode: Int, isPress: Boolean, downTime: Long?, now: Long): Boolean {
            val d = KeyDebouncePolicy.decide(boundStates[keyCode] ?: KeyDebouncePolicy.KeyState(), isPress, downTime, false, now)
            boundStates[keyCode] = d.state
            return d.forward
        }

        fun sendKey(keyCode: Int, isPress: Boolean, downTime: Long?, source: String, now: Long) {
            val logicalCode = when (val r = KeyBindingPolicy.resolve(bindings, keyCode, isPress)) {
                is KeyBindingPolicy.Resolution.SendToAa -> r.logicalCode
                is KeyBindingPolicy.Resolution.Swallow -> { admitsBound(keyCode, isPress, downTime, now); return }
                is KeyBindingPolicy.Resolution.Run -> {
                    if (admitsBound(keyCode, isPress, downTime, now)) ran.add(r.action)
                    return
                }
                KeyBindingPolicy.Resolution.Unbound -> keyCode
            }
            sendLogicalKey(logicalCode, isPress, downTime, source, now)
        }

        fun sendLogicalKey(code: Int, isPress: Boolean, downTime: Long?, source: String, now: Long) {
            val isMedia = code in 85..90 || code in 126..127
            if (isMedia && source != "auto-resume" && source != "gesture" &&
                !MediaKeyRoutingPolicy.shouldForward(routing, true, btMediaLink)) return
            val d = KeyDebouncePolicy.decide(keyStates[code] ?: KeyDebouncePolicy.KeyState(), isPress, downTime, isMedia, now)
            keyStates[code] = d.state
            if (d.forward) sent.add(code to isPress)
        }

        /** One physical click delivered by every route in [routes], press edges first. */
        fun click(keyCode: Int, downTime: Long, now: Long, vararg routes: Pair<String, Long?>) {
            routes.forEachIndexed { i, (src, id) -> sendKey(keyCode, true, id, src, now + i) }
            routes.forEachIndexed { i, (src, id) -> sendKey(keyCode, false, id, src, now + 60 + i) }
        }
    }

    private val dim = InputAction.Local(LocalAction.DIM_SCREEN)

    // ---- Bug 1: a bound local action runs once per route, so "Dim screen" toggles back. ----

    @Test
    fun `one press by the activity and the media session runs a bound action once`() {
        val d = Dispatch(mapOf(next to dim))
        d.click(next, downTime = 100, now = 1_000, "projection" to 100L, "mediasession" to 100L)
        assertEquals(listOf<InputAction>(dim), d.ran)
    }

    @Test
    fun `one press by the activity and the key broadcast runs a bound action once`() {
        val d = Dispatch(mapOf(next to dim))
        d.click(next, downTime = 100, now = 1_000, "projection" to 100L, "key-broadcast" to null)
        assertEquals(listOf<InputAction>(dim), d.ran)
    }

    @Test
    fun `control - two separate presses run a bound action twice`() {
        val d = Dispatch(mapOf(next to dim))
        d.click(next, downTime = 100, now = 1_000, "projection" to 100L)
        d.click(next, downTime = 5_000, now = 5_000, "projection" to 5_000L)
        assertEquals(listOf<InputAction>(dim, dim), d.ran)
    }

    @Test
    fun `control - an unbound media key by two routes reaches the phone once`() {
        val d = Dispatch(emptyMap())
        d.click(next, downTime = 100, now = 1_000, "projection" to 100L, "mediasession" to 100L)
        assertEquals(listOf(next to true, next to false), d.sent)
    }

    // ---- Bug 2: a gesture bound to a media key is held back by media key routing. ----

    @Test
    fun `a gesture bound to Next reaches the phone with routing NEVER`() {
        val d = Dispatch(emptyMap(), MediaKeyRoutingPolicy.Mode.NEVER)
        d.sendLogicalKey(next, true, null, "gesture", 1_000)
        d.sendLogicalKey(next, false, null, "gesture", 1_001)
        assertEquals(listOf(next to true, next to false), d.sent)
    }

    @Test
    fun `a gesture bound to Next reaches the phone with routing AUTO and an A2DP link`() {
        val d = Dispatch(emptyMap(), MediaKeyRoutingPolicy.Mode.AUTO, btMediaLink = true)
        d.sendLogicalKey(next, true, null, "gesture", 1_000)
        d.sendLogicalKey(next, false, null, "gesture", 1_001)
        assertEquals(listOf(next to true, next to false), d.sent)
    }

    @Test
    fun `control - a physical Next is held back with routing NEVER`() {
        val d = Dispatch(emptyMap(), MediaKeyRoutingPolicy.Mode.NEVER)
        d.click(next, downTime = 100, now = 1_000, "projection" to 100L)
        assertEquals(emptyList<Pair<Int, Boolean>>(), d.sent)
    }
}
