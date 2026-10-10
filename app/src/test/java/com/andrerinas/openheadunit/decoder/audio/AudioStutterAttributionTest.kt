package com.andrerinas.openheadunit.decoder.audio

import com.andrerinas.openheadunit.decoder.audio.AudioStutterAttribution.Cause
import org.junit.Assert.*
import org.junit.Test

class AudioStutterAttributionTest {
    private fun facts(unfedMs: Long = 10, deviceMs: Long = 20, arrivalGapMs: Long = -1,
                      sourceGapMs: Long = -1, flowReset: Boolean = false, edgeMs: Long = -1,
                      isMediaSink: Boolean = true) =
        AudioStutterAttribution.Facts(unfedMs, deviceMs, arrivalGapMs, sourceGapMs, 20, flowReset, edgeMs, isMediaSink)

    @Test fun `a device left unfed longer than its buffer is a mixer stall`() {
        assertEquals(Cause.MIXER_STALL, AudioStutterAttribution.classify(facts(unfedMs = 80)))
    }

    @Test fun `a late frame with a normal source gap is the link`() {
        assertEquals(Cause.LINK_LATE, AudioStutterAttribution.classify(facts(arrivalGapMs = 120, sourceGapMs = 20)))
    }

    @Test fun `a late frame with an unknown source gap is not blamed on the link`() {
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts(arrivalGapMs = 120)))
    }

    @Test fun `a source gap is the phone's own capture pause`() {
        assertEquals(Cause.PHONE_SOURCE_GAP, AudioStutterAttribution.classify(facts(sourceGapMs = 120)))
    }

    @Test fun `a start shortly before the event is a stream edge on guidance`() {
        assertEquals(Cause.STREAM_EDGE, AudioStutterAttribution.classify(facts(edgeMs = 100, isMediaSink = false)))
    }

    @Test fun `the same event on the media channel is not an edge`() {
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts(edgeMs = 100, isMediaSink = true)))
    }

    @Test fun `an old edge does not count`() {
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts(edgeMs = 400, isMediaSink = false)))
    }

    @Test fun `no matching fact is unknown`() {
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts()))
    }

    @Test fun `an unfed device inside a stream edge reports the stall`() {
        assertEquals(Cause.MIXER_STALL,
            AudioStutterAttribution.classify(facts(unfedMs = 80, edgeMs = 50, isMediaSink = false)))
    }

    @Test fun `a gap of 300 ms then a fast burst is the phone's flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(300, 20, 20)
        for (i in 1..6) tracker.onArrival(300L + i, 20, 20)
        val facts = tracker.facts(320, 10, 20, -1, true)
        assertTrue(facts.flowReset)
        assertEquals(Cause.PHONE_FLOW_RESET, AudioStutterAttribution.classify(facts))
    }

    @Test fun `a long gap with no burst is late delivery, not a flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(300, 20, 20)
        tracker.onArrival(320, 20, 20)
        val facts = tracker.facts(330, 10, 20, -1, true)
        assertFalse(facts.flowReset)
        assertEquals(Cause.LINK_LATE, AudioStutterAttribution.classify(facts))
    }

    @Test fun `a burst whose source gaps are wide is not a flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(300, 20, 20)
        for (i in 1..6) tracker.onArrival(300L + i, 20, 120)
        assertFalse(tracker.facts(320, 10, 20, -1, true).flowReset)
    }

    @Test fun `a gap older than a second is forgotten`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(120, 20, 20)
        assertEquals(120L, tracker.facts(500, 10, 20, -1, true).arrivalGapMs)
        assertEquals(-1L, tracker.facts(2000, 10, 20, -1, true).arrivalGapMs)
    }

    @Test fun `AAC groups of two units on a steady link are not late`() {
        val tracker = AudioIngressTracker()
        for (g in 0 until 40) {
            val at = g * 4267L / 100
            tracker.onArrival(at, 21, 21)
            tracker.onArrival(at, 21, -1)
        }
        val facts = tracker.facts(1700, 10, 40, -1, true)
        assertEquals(-1L, facts.arrivalGapMs)
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts))
    }

    @Test fun `a late AAC group is still the link`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 21, 21); tracker.onArrival(0, 21, -1)
        tracker.onArrival(43, 21, 21); tracker.onArrival(43, 21, -1)
        tracker.onArrival(163, 21, 21); tracker.onArrival(163, 21, -1)
        val facts = tracker.facts(170, 10, 40, -1, true)
        assertEquals(120L, facts.arrivalGapMs)
        assertEquals(Cause.LINK_LATE, AudioStutterAttribution.classify(facts))
    }

    @Test fun `ten events in one second give two lines and eight suppressed`() {
        val budget = StutterLogBudget()
        val logged = (0 until 10).count { budget.tryLog(1000L + it * 50) }
        assertEquals(2, logged)
        assertEquals(8, budget.takeSuppressed())
        assertEquals(0, budget.takeSuppressed())
    }

    @Test fun `each channel has its own budget and a new second refills it`() {
        val a = StutterLogBudget()
        val b = StutterLogBudget()
        repeat(5) { a.tryLog(1000) }
        assertTrue(b.tryLog(1000))
        assertTrue(a.tryLog(2100))
    }

    @Test fun `a write that waits for room on a 20 ms device is not a stall`() {
        val gap = MixerUnfedGap()
        var t = 0L
        repeat(50) {
            gap.onCycle(t)
            gap.onWriteStart(t + 1)
            gap.onWriteDone(t + 21)
            t += 22
        }
        val unfed = gap.gapMs(t)
        assertTrue("unfed=$unfed", unfed <= 2)
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts(unfedMs = unfed, deviceMs = 20)))
    }

    @Test fun `a thread that comes back late to write is a stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(20)
        gap.onCycle(80); gap.onWriteStart(81); gap.onWriteDone(100)
        assertEquals(61L, gap.gapMs(101))
    }

    @Test fun `a stall still running at the scan counts`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(20)
        assertEquals(70L, gap.gapMs(90))
    }

    @Test fun `an idle wait is not time unfed`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(20)
        gap.reset()
        gap.onCycle(500); gap.onWriteStart(501); gap.onWriteDone(520)
        assertEquals(1L, gap.gapMs(521))
    }

    @Test fun `a thread stalled inside the wait for room is a stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(10)
        gap.onCycle(10); gap.onWriteStart(10)
        gap.onDeviceCall(10, 10, 0)
        gap.onDeviceCall(10, 160, 0)
        gap.onDeviceCall(160, 160, 10)
        gap.onWriteDone(160)
        gap.onCycle(160); gap.onWriteStart(161)
        val unfed = gap.gapMs(161)
        assertEquals(150L, unfed)
        assertEquals(Cause.MIXER_STALL, AudioStutterAttribution.classify(facts(unfedMs = unfed, deviceMs = 100)))
    }

    @Test fun `a blocking write stalled in the call is a stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0)
        gap.onDeviceCall(0, 160, 10)
        gap.onWriteDone(160)
        assertEquals(150L, gap.gapMs(160))
    }

    @Test fun `ordinary waits for room on a full 20 ms device are not a stall`() {
        val gap = MixerUnfedGap()
        var t = 0L
        repeat(50) {
            gap.onCycle(t); gap.onWriteStart(t)
            repeat(9) { k -> gap.onDeviceCall(t + k, t + k, 0); gap.onDeviceCall(t + k, t + k + 1, 0) }
            gap.onDeviceCall(t + 9, t + 10, 10)
            gap.onWriteDone(t + 10)
            t += 10
        }
        val unfed = gap.gapMs(t)
        assertTrue("unfed=$unfed", unfed <= 1)
        assertEquals(Cause.UNKNOWN, AudioStutterAttribution.classify(facts(unfedMs = unfed, deviceMs = 20)))
    }

    @Test fun `a 1200 ms hole then a burst is the link, not a flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(1200, 20, 20)
        for (i in 1..6) tracker.onArrival(1200L + i, 20, 20)
        val facts = tracker.facts(1220, 10, 20, -1, true)
        assertFalse(facts.flowReset)
        assertEquals(Cause.LINK_LATE, AudioStutterAttribution.classify(facts))
    }

    @Test fun `a 310 ms gap then a burst is the phone's flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(310, 20, 20)
        for (i in 1..6) tracker.onArrival(310L + i, 20, 20)
        assertEquals(Cause.PHONE_FLOW_RESET,
            AudioStutterAttribution.classify(tracker.facts(330, 10, 20, -1, true)))
    }

    @Test fun `a burst with unknown source gaps is not a flow reset`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, -1)
        tracker.onArrival(300, 20, -1)
        for (i in 1..6) tracker.onArrival(300L + i, 20, -1)
        val facts = tracker.facts(320, 10, 20, -1, true)
        assertFalse(facts.flowReset)
        assertNotEquals(Cause.PHONE_FLOW_RESET, AudioStutterAttribution.classify(facts))
    }

    @Test fun `a gap with an unknown source gap is not a flow reset even if the burst is known`() {
        val tracker = AudioIngressTracker()
        tracker.onArrival(0, 20, 20)
        tracker.onArrival(300, 20, -1)
        for (i in 1..6) tracker.onArrival(300L + i, 20, 20)
        assertFalse(tracker.facts(320, 10, 20, -1, true).flowReset)
    }

    @Test fun `a focus hold longer than the device buffer is not a mixer stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(20)
        var t = 30L
        while (t <= 520) { gap.onCycle(t); gap.onHold(t); t += 10 }
        assertNotEquals(Cause.MIXER_STALL,
            AudioStutterAttribution.classify(facts(unfedMs = gap.gapMs(525), deviceMs = 100)))
        gap.onCycle(530); gap.onWriteStart(531); gap.onWriteDone(551)
        val unfed = gap.gapMs(551)
        assertTrue("unfed=$unfed", unfed <= 11)
        assertNotEquals(Cause.MIXER_STALL, AudioStutterAttribution.classify(facts(unfedMs = unfed, deviceMs = 100)))
    }

    @Test fun `a thread stalled during a focus hold is still a stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onWriteStart(0); gap.onWriteDone(20)
        gap.onCycle(30); gap.onHold(30)
        gap.onCycle(40); gap.onHold(40)
        gap.onCycle(200); gap.onHold(200)
        val unfed = gap.gapMs(201)
        assertEquals(160L, unfed)
        assertEquals(Cause.MIXER_STALL, AudioStutterAttribution.classify(facts(unfedMs = unfed, deviceMs = 100)))
    }

    @Test fun `a thread late to write after a focus hold is a stall`() {
        val gap = MixerUnfedGap()
        gap.onCycle(0); gap.onHold(0)
        gap.onCycle(10); gap.onHold(10)
        gap.onCycle(170); gap.onWriteStart(170); gap.onWriteDone(190)
        assertEquals(Cause.MIXER_STALL,
            AudioStutterAttribution.classify(facts(unfedMs = gap.gapMs(190), deviceMs = 100)))
    }
}
