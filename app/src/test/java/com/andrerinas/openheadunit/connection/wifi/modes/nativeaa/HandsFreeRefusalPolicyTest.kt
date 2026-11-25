package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.BluetoothWakePolicy.TargetLink
import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.BluetoothWakePolicy.WakeOutcome
import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.HfpSlcInitiator.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeRefusalPolicyTest {

    // Measured on a Z Fold3 with the car's Bluetooth (and at home a headset) plus earbuds connected:
    // the poke's socket connected, nothing answered AT+BRSF, and the phone dropped the whole link
    // 0.34 s later ("Blocked by isNewConnectionAcceptable").
    @Test
    fun `no answer and the phone's link gone is a refusal`() {
        assertTrue(HandsFreeRefusalPolicy.refused(Stage.BRSF, TargetLink.ABSENT))
        assertTrue(HandsFreeRefusalPolicy.refused(Stage.IDLE, TargetLink.ABSENT))
    }

    // A phone already serving another device's hands-free link keeps ours up and says nothing.
    @Test
    fun `no answer with the link still up is silence, not a refusal`() {
        assertFalse(HandsFreeRefusalPolicy.refused(Stage.BRSF, TargetLink.CONNECTED))
    }

    @Test
    fun `a link that cannot be read is never taken as gone`() {
        assertFalse(HandsFreeRefusalPolicy.refused(Stage.BRSF, TargetLink.UNREADABLE))
    }

    @Test
    fun `a phone that answered did not refuse, whatever its link did afterwards`() {
        for (stage in listOf(Stage.CIND_TEST, Stage.CIND_READ, Stage.CMER, Stage.ESTABLISHED)) {
            assertFalse(stage.name, HandsFreeRefusalPolicy.refused(stage, TargetLink.ABSENT))
        }
    }

    private val listB = PokeTargets.Selected(setOf("B"))
    private fun offListA(list: PokeTargets = listB) = HandsFreeRefusalPolicy.offListPick("A", list, 1_000L)

    @Test
    fun `only a pick the wake list leaves out is kept at the hand-over`() {
        assertEquals("A", offListA()?.mac)
        assertEquals("A", offListA(PokeTargets.None)?.mac)
        assertNull(offListA(PokeTargets.Selected(setOf("A", "B"))))
        assertNull(offListA(PokeTargets.AllPaired))
    }

    @Test
    fun `an off-list pick is asked for ten minutes from the pick and no longer`() {
        val pick = offListA()!!
        fun asks(age: Long) = HandsFreeRefusalPolicy.asksOffList(pick, "A", listB, stillRefuses = true, pickAgeMs = age)
        assertTrue(asks(0L))
        assertTrue(asks(HandsFreeRefusalPolicy.PATIENCE_MS - 1))
        assertFalse(asks(HandsFreeRefusalPolicy.PATIENCE_MS))
        assertFalse(asks(55 * 60_000L))
    }

    @Test
    fun `a changed list, another pick or a taken link ends the off-list asks`() {
        val pick = offListA()!!
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, "A", PokeTargets.None, true, 0L))
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, "A", PokeTargets.Selected(setOf("A", "B")), true, 0L))
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, "B", listB, true, 0L))
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, null, listB, true, 0L))
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, "A", listB, false, 0L))
    }

    // The list is the one at the pick: a listed phone taken off it during the wake is not stored,
    // and one picked off it is dropped once the list is another.
    @Test
    fun `a pick and a list that changed during its wake are never asked off the list`() {
        assertNull(offListA(PokeTargets.Selected(setOf("A", "B"))))
        val pick = offListA(PokeTargets.Selected(setOf("B", "C")))!!
        assertFalse(HandsFreeRefusalPolicy.asksOffList(pick, "A", listB, stillRefuses = true, pickAgeMs = 60_000L))
    }

    @Test
    fun `a refusal holds the issue only while it is live`() {
        val old = HandsFreeRefusalPolicy.Refusals(firstAtMs = 1_000L, lastAtMs = 2_000L)
        val edge = 2_000L + HandsFreeRefusalPolicy.PATIENCE_MS
        assertFalse(HandsFreeRefusalPolicy.anyLive(emptyList(), edge))
        assertTrue(HandsFreeRefusalPolicy.anyLive(listOf(old), edge))
        assertFalse(HandsFreeRefusalPolicy.anyLive(listOf(old), edge + 1))
        // One that still refuses is asked every 2 min at most, so it stays live.
        val still = HandsFreeRefusalPolicy.Refusals(1_000L, edge + 1 - HandsFreeRefusalPolicy.SLOW_RETRY_GAP_MS)
        assertTrue(HandsFreeRefusalPolicy.anyLive(listOf(old, still), edge + 1))
    }

    @Test
    fun `an off-list ask is spaced from the last ask, and a dead dial waits longer beside other phones`() {
        assertEquals(1_000L + HandsFreeRefusalPolicy.RETRY_GAP_MS, offListA()!!.nextAskAtMs)
        assertEquals(HandsFreeRefusalPolicy.RETRY_GAP_MS, HandsFreeRefusalPolicy.offListGapMs(unanswered = false, othersListed = true))
        assertEquals(HandsFreeRefusalPolicy.RETRY_GAP_MS, HandsFreeRefusalPolicy.offListGapMs(unanswered = true, othersListed = false))
        assertEquals(HandsFreeRefusalPolicy.SLOW_RETRY_GAP_MS, HandsFreeRefusalPolicy.offListGapMs(unanswered = true, othersListed = true))
    }

    // The automatic loop keeps asking a listed phone, at the slower gap once ten minutes are over.
    @Test
    fun `after the fast ten minutes the asking slows`() {
        val refusedAt = 1_000L
        val tenMinutesOn = refusedAt + HandsFreeRefusalPolicy.PATIENCE_MS
        assertEquals(HandsFreeRefusalPolicy.RETRY_GAP_MS, HandsFreeRefusalPolicy.gapMs(refusedAt, tenMinutesOn - 1, 15_000L))
        assertEquals(HandsFreeRefusalPolicy.SLOW_RETRY_GAP_MS, HandsFreeRefusalPolicy.gapMs(refusedAt, tenMinutesOn, 15_000L))
    }

    // Each ask opens Android Auto on the phone for about 5 s, so asking is spaced well apart.
    @Test
    fun `a refusing phone is not asked more often than every half minute`() {
        assertTrue(HandsFreeRefusalPolicy.RETRY_GAP_MS >= 30_000L)
        assertEquals(HandsFreeRefusalPolicy.RETRY_GAP_MS, HandsFreeRefusalPolicy.gapMs(firstRefusalAtMs = 1_000L, nowMs = 1_000L, ordinaryGapMs = 15_000L))
    }

    @Test
    fun `a phone that never refused keeps the ordinary gap`() {
        assertEquals(15_000L, HandsFreeRefusalPolicy.gapMs(firstRefusalAtMs = 0L, nowMs = 1_000L, ordinaryGapMs = 15_000L))
    }

    // The Fold dropped the link 0.34 s after the socket connected; the read must come after that.
    @Test
    fun `the link is read after the phone has had time to drop it`() {
        assertTrue(HandsFreeRefusalPolicy.LINK_PROBE_MS >= 1_000L)
    }

    @Test
    fun `the automatic loop keeps the refusal gap through a run of refusals`() {
        var refusals: HandsFreeRefusalPolicy.Refusals? = null
        var now = 1_000L
        var asks = 0
        while (now < 1_000L + HandsFreeRefusalPolicy.PATIENCE_MS) {
            val noted = HandsFreeRefusalPolicy.noted(refusals, now)
            assertTrue(HandsFreeRefusalPolicy.askAgainAtMs(noted) - now >= HandsFreeRefusalPolicy.RETRY_GAP_MS)
            assertEquals(1_000L, noted.firstAtMs)
            refusals = noted
            now = HandsFreeRefusalPolicy.askAgainAtMs(noted)
            asks++
        }
        assertEquals(20, asks)
        val slow = HandsFreeRefusalPolicy.noted(refusals, now)
        assertEquals(HandsFreeRefusalPolicy.SLOW_RETRY_GAP_MS, HandsFreeRefusalPolicy.askAgainAtMs(slow) - now)
    }

    @Test
    fun `a refusal after a long silence starts the cadence again`() {
        val old = HandsFreeRefusalPolicy.Refusals(firstAtMs = 1_000L, lastAtMs = 2_000L)
        val later = 2_000L + HandsFreeRefusalPolicy.PATIENCE_MS + 1
        assertEquals(HandsFreeRefusalPolicy.Refusals(later, later), HandsFreeRefusalPolicy.noted(old, later))
        assertEquals(1_000L, HandsFreeRefusalPolicy.noted(old, later - 1).firstAtMs)
    }

    @Test
    fun `a refused pick hands over once its ordinary rounds are over and never before`() {
        assertTrue(HandsFreeRefusalPolicy.handsOver(true, WakeOutcome.ANSWERED, ordinaryRoundsLeft = false))
        assertTrue(HandsFreeRefusalPolicy.handsOver(true, WakeOutcome.DIALLED, ordinaryRoundsLeft = false))
        assertFalse(HandsFreeRefusalPolicy.handsOver(true, WakeOutcome.ANSWERED, ordinaryRoundsLeft = true))
    }

    @Test
    fun `an unpaired phone or one that never refused ends its wake without a hand-over`() {
        assertFalse(HandsFreeRefusalPolicy.handsOver(true, WakeOutcome.NOT_PAIRED, ordinaryRoundsLeft = false))
        assertFalse(HandsFreeRefusalPolicy.handsOver(false, WakeOutcome.DIALLED, ordinaryRoundsLeft = false))
    }

    // Three refused rounds: 2 s each with 30 s between, then the other phones get their turn.
    @Test
    fun `the ordinary rounds of a refusing pick are over after three asks`() {
        var rounds = 0
        var elapsed = 0L
        while (NativeDriverSelectionPolicy.chosenWakeContinues(WakeOutcome.ANSWERED, rounds, elapsed)) {
            elapsed += HandsFreeRefusalPolicy.LINK_PROBE_MS
            rounds++
            if (NativeDriverSelectionPolicy.chosenWakeContinues(WakeOutcome.ANSWERED, rounds, elapsed)) {
                elapsed += HandsFreeRefusalPolicy.gapMs(1_000L, 1_000L + elapsed, 15_000L)
            }
        }
        assertEquals(3, rounds)
        assertEquals(66_000L, elapsed)
    }
}
