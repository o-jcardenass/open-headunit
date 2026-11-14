package com.andrerinas.openheadunit.connection.self

import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.Action
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.CALL_CONFIRM_WINDOW_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.CallLedger
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.CoverVerdict
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.Episode
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.FIRST_ATTEMPT_DELAY_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.HEAD_UNIT_RAISE_WINDOW_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.IDLE_TICK_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.LEDGER_STALE_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.MAX_ATTEMPTS_PER_CALL
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.POST_CALL_SETTLE_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.RETRY_INTERVAL_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.RING_WAIT_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.Scope
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.START_EVIDENCE_MS
import com.andrerinas.openheadunit.connection.self.SelfModeCallRaisePolicy.TICK_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SelfModeCallRaisePolicyTest {

    private val start = 10_000L

    /** A clock origin far enough from 0 that "no reading yet" and a real time never meet. */
    private val base = 100_000L

    private fun decide(
        nowMs: Long,
        episode: Episode,
        callActive: Boolean = true,
        callStarting: Boolean = false,
        isForeground: Boolean = false,
        pipActive: Boolean = false,
        scope: Scope = Scope.SELF_MODE,
        ledger: CallLedger = CallLedger(),
        reverseGear: Boolean = false,
    ): Action = SelfModeCallRaisePolicy.decide(
        nowMs = nowMs,
        episode = SelfModeCallRaisePolicy.observe(episode, nowMs, callActive),
        callActive = callActive,
        callStarting = callStarting,
        isForeground = isForeground,
        pipActive = pipActive,
        scope = scope,
        ledger = ledger,
        reverseGear = reverseGear,
    )

    private fun why(
        nowMs: Long,
        episode: Episode,
        callActive: Boolean = true,
        callStarting: Boolean = false,
        scope: Scope = Scope.HEAD_UNIT,
        ledger: CallLedger = CallLedger(),
        reverseGear: Boolean = false,
    ): String = SelfModeCallRaisePolicy.describe(
        nowMs = nowMs,
        episode = SelfModeCallRaisePolicy.observe(episode, nowMs, callActive),
        callActive = callActive,
        callStarting = callStarting,
        isForeground = false,
        pipActive = false,
        scope = scope,
        ledger = ledger,
        reverseGear = reverseGear,
    )

    private fun reading(
        ledger: CallLedger,
        nowMs: Long,
        callActive: Boolean,
        ringing: Boolean = false,
        fromModeChange: Boolean = false,
    ): CallLedger = SelfModeCallRaisePolicy.recordCall(ledger, nowMs, callActive, ringing, fromModeChange)

    /** A head unit call whose start OHU saw, at [atMs]. */
    private fun seenCall(atMs: Long): CallLedger = reading(CallLedger(), atMs, callActive = true, fromModeChange = true)

    /** One tick of the loop the activity runs, so a whole call can be played out in a test. */
    private fun tick(
        episode: Episode,
        nowMs: Long,
        callActive: Boolean,
        ledger: CallLedger = CallLedger(),
    ): Pair<Action, Episode> {
        val observed = SelfModeCallRaisePolicy.observe(episode, nowMs, callActive)
        val action = SelfModeCallRaisePolicy.decide(
            nowMs = nowMs,
            episode = observed,
            callActive = callActive,
            callStarting = false,
            isForeground = false,
            pipActive = false,
            scope = episode.scope,
            ledger = ledger,
            reverseGear = false,
        )
        val next = if (action == Action.RAISE) {
            SelfModeCallRaisePolicy.onRaised(observed, nowMs, callActive)
        } else {
            observed
        }
        return action to next
    }

    /**
     * A 60 s call over which the cover returns every 6 s, as the activity plays it: open an
     * episode from the ledger, tick once after the settle delay, then the raise (or the user)
     * resumes the projection. Returns the number of raises.
     */
    private fun coverLoopRaises(scope: Scope): Int {
        var ledger = seenCall(base)
        var raises = 0
        var coverAt = base
        while (coverAt < base + 60_000L) {
            var episode = Episode(
                startedAtMs = coverAt,
                sawCallActive = true,
                attempts = ledger.attempts,
                lastAttemptAtMs = ledger.lastRaiseAtMs,
                scope = scope,
            )
            val tickAt = coverAt + FIRST_ATTEMPT_DELAY_MS
            ledger = reading(ledger, tickAt, callActive = true)
            val (action, next) = tick(episode, tickAt, callActive = true, ledger = ledger)
            if (action == Action.RAISE) {
                raises++
                episode = next
                ledger = SelfModeCallRaisePolicy.recordRaise(ledger, tickAt, callActive = true)
            }
            coverAt += 6_000L
            ledger = reading(ledger, coverAt, callActive = true)
        }
        return raises
    }

    @Test
    fun `the first attempt waits for the call screen to settle`() {
        val episode = Episode(startedAtMs = start)
        assertEquals(Action.WAIT, decide(nowMs = start + FIRST_ATTEMPT_DELAY_MS - 1, episode = episode))
        assertEquals(Action.RAISE, decide(nowMs = start + FIRST_ATTEMPT_DELAY_MS, episode = episode))
    }

    @Test
    fun `attempts are spaced by the retry interval`() {
        val episode = Episode(startedAtMs = start, sawCallActive = true, attempts = 1, lastAttemptAtMs = start + 600)
        assertEquals(Action.WAIT, decide(nowMs = start + 600 + RETRY_INTERVAL_MS - 1, episode = episode))
        assertEquals(Action.RAISE, decide(nowMs = start + 600 + RETRY_INTERVAL_MS, episode = episode))
    }

    @Test
    fun `a call gets exactly three attempts and then silence`() {
        var episode = Episode(startedAtMs = start)
        var now = start
        var raises = 0
        repeat(120) {
            now += TICK_MS
            val (action, next) = tick(episode, now, callActive = true)
            if (action == Action.RAISE) raises++
            episode = next
        }
        assertEquals(MAX_ATTEMPTS_PER_CALL, raises)
        assertEquals(Action.WAIT, tick(episode, now + TICK_MS, callActive = true).first)
    }

    @Test
    fun `the projection coming back closes the episode`() {
        val episode = Episode(startedAtMs = start, sawCallActive = true, attempts = 1, lastAttemptAtMs = start)
        assertEquals(Action.DONE, decide(nowMs = start + 5_000, episode = episode, isForeground = true))
    }

    @Test
    fun `picture-in-picture closes the episode`() {
        val episode = Episode(startedAtMs = start, sawCallActive = true)
        assertEquals(Action.DONE, decide(nowMs = start + 5_000, episode = episode, pipActive = true))
    }

    @Test
    fun `something that is not a call is left alone`() {
        val episode = Episode(startedAtMs = start)
        assertEquals(
            Action.WAIT,
            decide(nowMs = start + CALL_CONFIRM_WINDOW_MS - 1, episode = episode, callActive = false)
        )
        assertEquals(
            Action.DONE,
            decide(nowMs = start + CALL_CONFIRM_WINDOW_MS, episode = episode, callActive = false)
        )
    }

    @Test
    fun `a call that registers late still opens the episode`() {
        var episode = Episode(startedAtMs = start)
        val (early, afterEarly) = tick(episode, start + TICK_MS, callActive = false)
        assertEquals(Action.WAIT, early)
        episode = afterEarly

        val (late, _) = tick(episode, start + FIRST_ATTEMPT_DELAY_MS, callActive = true)
        assertEquals(Action.RAISE, late)
    }

    @Test
    fun `the call ending gets one attempt, and only one`() {
        var episode = Episode(startedAtMs = start, sawCallActive = true, attempts = MAX_ATTEMPTS_PER_CALL)
        val endedAt = start + 30_000

        assertEquals(Action.WAIT, tick(episode, endedAt, callActive = false).first)
        episode = tick(episode, endedAt, callActive = false).second

        val (action, afterRaise) = tick(episode, endedAt + POST_CALL_SETTLE_MS, callActive = false)
        assertEquals(Action.RAISE, action)
        assertEquals(
            Action.DONE,
            tick(afterRaise, endedAt + POST_CALL_SETTLE_MS + TICK_MS, callActive = false).first
        )
    }

    @Test
    fun `the post-call attempt never happens when the projection came back on its own`() {
        val episode = Episode(
            startedAtMs = start,
            sawCallActive = true,
            attempts = MAX_ATTEMPTS_PER_CALL,
            callEndedAtMs = start + 30_000,
        )
        assertEquals(
            Action.DONE,
            decide(
                nowMs = start + 30_000 + POST_CALL_SETTLE_MS,
                episode = episode,
                callActive = false,
                isForeground = true,
            )
        )
    }

    @Test
    fun `the post-call attempt does not spend a call attempt`() {
        val episode = Episode(startedAtMs = start, sawCallActive = true, attempts = 1, callEndedAtMs = start + 5_000)
        val raised = SelfModeCallRaisePolicy.onRaised(episode, start + 6_000, callActive = false)
        assertEquals(1, raised.attempts)
        assertTrue(raised.postCallAttemptUsed)
    }

    @Test
    fun `a call screen that relaunches does not buy a second budget`() {
        val spentAtMs = start + 3_000
        val spent = Episode(
            startedAtMs = spentAtMs + 6_000,
            sawCallActive = true,
            attempts = MAX_ATTEMPTS_PER_CALL,
            lastAttemptAtMs = spentAtMs,
        )
        assertEquals(Action.WAIT, decide(nowMs = spentAtMs + 6_000, episode = spent))
        assertEquals(
            Action.DONE,
            decide(
                nowMs = spentAtMs + 6_000,
                episode = spent.copy(attempts = 1, scope = Scope.HEAD_UNIT),
                scope = Scope.HEAD_UNIT,
                ledger = seenCall(spentAtMs),
            )
        )
    }

    @Test
    fun `a no-call reading clears the ledger, so a later call has a full budget`() {
        val spent = SelfModeCallRaisePolicy.recordRaise(seenCall(base), base + 600, callActive = true)
        assertEquals(1, spent.attempts)

        val cleared = reading(spent, base + 30_000, callActive = false)
        assertEquals(0, cleared.attempts)
        assertEquals(0L, cleared.callSinceMs)

        val later = reading(cleared, base + 30_400, callActive = true)
        assertEquals(0, later.attempts)
        assertEquals(base + 30_400, later.callSinceMs)
        assertTrue(later.startSeen)
    }

    @Test
    fun `a ledger left unread for 60 s does not carry into a new reading`() {
        val spent = SelfModeCallRaisePolicy.recordRaise(seenCall(base), base + 600, callActive = true)
        val later = reading(spent, base + LEDGER_STALE_MS + 1, callActive = true)
        assertEquals(0, later.attempts)
        assertEquals(base + LEDGER_STALE_MS + 1, later.callSinceMs)
        assertFalse(later.startSeen)
    }

    @Test
    fun `a cover that returns every 6 s spends one budget for the whole call`() {
        assertEquals(MAX_ATTEMPTS_PER_CALL, coverLoopRaises(Scope.SELF_MODE))
        assertEquals(1, coverLoopRaises(Scope.HEAD_UNIT))
    }

    @Test
    fun `a ring that outlasts the confirm window keeps the episode open`() {
        val episode = Episode(startedAtMs = start)
        assertEquals(
            Action.WAIT,
            decide(nowMs = start + CALL_CONFIRM_WINDOW_MS, episode = episode, callActive = false, callStarting = true)
        )
        assertEquals(Action.RAISE, decide(nowMs = start + 12_000, episode = episode))
        assertEquals(
            Action.DONE,
            decide(nowMs = start + RING_WAIT_MS, episode = episode, callActive = false, callStarting = true)
        )
    }

    @Test
    fun `a head unit raise waits for the call to settle and then raises once`() {
        val ledger = seenCall(base)
        val episode = Episode(startedAtMs = base, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.WAIT,
            decide(base + FIRST_ATTEMPT_DELAY_MS - 1, episode, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
        assertEquals(
            Action.RAISE,
            decide(base + FIRST_ATTEMPT_DELAY_MS, episode, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
        val raised = SelfModeCallRaisePolicy.onRaised(episode, base + FIRST_ATTEMPT_DELAY_MS, callActive = true)
        assertEquals(
            Action.DONE,
            decide(base + 1_000, raised, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
        assertEquals(
            "attempt 1 of 1 during the call",
            why(base + FIRST_ATTEMPT_DELAY_MS, episode, ledger = ledger)
        )
    }

    @Test
    fun `a head unit raise after a long ring waits 600 ms from the answer`() {
        val ringing = reading(CallLedger(), base, callActive = false, ringing = true, fromModeChange = true)
        val answered = reading(ringing, base + 15_000, callActive = true, fromModeChange = true)
        val episode = Episode(startedAtMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.WAIT,
            decide(base + 15_200, episode, scope = Scope.HEAD_UNIT, ledger = answered)
        )
        assertEquals(
            Action.RAISE,
            decide(base + 15_600, episode, scope = Scope.HEAD_UNIT, ledger = answered)
        )
    }

    @Test
    fun `a cover that came 6 s before the ring gets no raise`() {
        val ringing = reading(CallLedger(), base + 8_000, callActive = false, ringing = true, fromModeChange = true)
        val answered = reading(ringing, base + 12_000, callActive = true, fromModeChange = true)
        val episode = Episode(
            startedAtMs = base + 8_000,
            coveredSinceMs = base,
            sawCallActive = true,
            scope = Scope.HEAD_UNIT,
        )
        assertEquals(
            Action.DONE,
            decide(base + 12_600, episode, scope = Scope.HEAD_UNIT, ledger = answered)
        )
        assertEquals(
            "the cover came before the call, leaving it alone",
            why(base + 12_600, episode, ledger = answered)
        )
        assertEquals(
            Action.DONE,
            decide(base + 8_400, episode.copy(sawCallActive = false), callActive = false, callStarting = true,
                scope = Scope.HEAD_UNIT, ledger = ringing)
        )
    }

    @Test
    fun `a cover that came 10 s into a 25 s ring gets no raise`() {
        val ringing = reading(CallLedger(), base, callActive = false, ringing = true, fromModeChange = true)
        val answered = reading(ringing, base + 25_000, callActive = true, fromModeChange = true)
        val episode = Episode(startedAtMs = base + 10_000, sawCallActive = false, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.WAIT,
            decide(base + 20_000, episode, callActive = false, callStarting = true,
                scope = Scope.HEAD_UNIT, ledger = ringing)
        )
        val seen = episode.copy(sawCallActive = true)
        assertEquals(
            Action.DONE,
            decide(base + 25_600, seen, scope = Scope.HEAD_UNIT, ledger = answered)
        )
        assertEquals(
            "the cover came during the ring, well before the answer, leaving it alone",
            why(base + 25_600, seen, ledger = answered)
        )
    }

    @Test
    fun `a dialer that opened at a 25 s ring still raises at the answer`() {
        val ringing = reading(CallLedger(), base, callActive = false, ringing = true, fromModeChange = true)
        val answered = reading(ringing, base + 25_000, callActive = true, fromModeChange = true)
        val episode = Episode(startedAtMs = base + 1_000, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.RAISE,
            decide(base + 25_600, episode, scope = Scope.HEAD_UNIT, ledger = answered)
        )
    }

    @Test
    fun `a cover 10 s into a ring that OHU first reads at the cover gets no raise`() {
        // Below API 31 nothing reads the mode while the projection is in front, as in onResume then the cover.
        val resumed = reading(CallLedger(), base - 30_000, callActive = false)
        var ledger = reading(resumed, base + 10_000, callActive = false, ringing = true)
        assertEquals(base + 10_000, ledger.signalSinceMs)
        val episode = Episode(startedAtMs = base + 10_000, sawCallActive = false, scope = Scope.HEAD_UNIT)
        var at = base + 10_400
        while (at < base + 25_000) {
            ledger = reading(ledger, at, callActive = false, ringing = true)
            assertEquals(
                Action.WAIT,
                decide(at, episode, callActive = false, callStarting = true, scope = Scope.HEAD_UNIT, ledger = ledger)
            )
            at += 400
        }
        ledger = reading(ledger, base + 25_200, callActive = true)
        assertTrue(ledger.startSeen)
        val seen = episode.copy(sawCallActive = true)
        assertEquals(Action.DONE, decide(base + 25_600, seen, scope = Scope.HEAD_UNIT, ledger = ledger))
        assertEquals(
            "the ring start was not seen and the cover came well before the answer, leaving it alone",
            why(base + 25_600, seen, ledger = ledger)
        )
    }

    @Test
    fun `a ring callback after the cover reading confirms the ring start`() {
        // API 31+: the cover reads RINGTONE first, then the watcher callback for the same change arrives.
        val resumed = reading(CallLedger(), base - 30_000, callActive = false)
        val atCover = reading(resumed, base, callActive = false, ringing = true)
        assertFalse(atCover.signalStartSeen)
        val ringing = reading(atCover, base + 50, callActive = false, ringing = true, fromModeChange = true)
        assertTrue(ringing.signalStartSeen)
        assertEquals(base, ringing.signalSinceMs)
        val answered = reading(ringing, base + 8_000, callActive = true, fromModeChange = true)
        assertTrue(answered.signalStartSeen)
        val episode = Episode(startedAtMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(Action.RAISE, decide(base + 8_600, episode, scope = Scope.HEAD_UNIT, ledger = answered))
    }

    @Test
    fun `a ring callback long after the first ring reading confirms nothing`() {
        val resumed = reading(CallLedger(), base - 30_000, callActive = false)
        val atCover = reading(resumed, base, callActive = false, ringing = true)
        val late = reading(atCover, base + START_EVIDENCE_MS + 1, callActive = false, ringing = true, fromModeChange = true)
        assertFalse(late.signalStartSeen)
    }

    @Test
    fun `a dialer that covers just before a ring OHU reads still raises at a late answer`() {
        val atCover = reading(CallLedger(), base, callActive = false)
        val ringing = reading(atCover, base + 400, callActive = false, ringing = true)
        val held = reading(ringing, base + 19_800, callActive = false, ringing = true)
        val answered = reading(held, base + 20_000, callActive = true)
        assertTrue(answered.signalStartSeen)
        val episode = Episode(startedAtMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(Action.RAISE, decide(base + 20_600, episode, scope = Scope.HEAD_UNIT, ledger = answered))
    }

    @Test
    fun `a cover that came 3 s before the call still raises`() {
        val ledger = reading(CallLedger(), base + 3_000, callActive = true, fromModeChange = true)
        val episode = Episode(startedAtMs = base + 3_000, coveredSinceMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.RAISE,
            decide(base + 3_600, episode, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
    }

    @Test
    fun `an outgoing call whose mode moved before the cover gets no raise below API 31`() {
        val quiet = reading(CallLedger(), base, callActive = false)
        val ledger = reading(quiet, base + 300_000, callActive = true)
        assertFalse(ledger.startSeen)
        val episode = Episode(startedAtMs = base + 300_000, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.DONE,
            decide(base + 300_600, episode, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
        assertEquals(
            "the call start was not seen, leaving the call screen alone",
            why(base + 300_600, episode, ledger = ledger)
        )
    }

    @Test
    fun `a head unit call older than 10 s is left alone`() {
        val ledger = seenCall(base)
        val episode = Episode(startedAtMs = base + 11_000, sawCallActive = true, scope = Scope.HEAD_UNIT)
        val now = base + 11_000 + FIRST_ATTEMPT_DELAY_MS
        assertTrue(now - base > HEAD_UNIT_RAISE_WINDOW_MS)
        assertEquals(Action.DONE, decide(now, episode, scope = Scope.HEAD_UNIT, ledger = ledger))
        assertEquals(
            "the call started over 10 s ago, leaving the call screen alone",
            why(now, episode, ledger = ledger)
        )
    }

    @Test
    fun `a head unit call with an unseen start is left alone`() {
        val ledger = reading(CallLedger(), base, callActive = true)
        assertFalse(ledger.startSeen)
        val episode = Episode(startedAtMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.DONE,
            decide(base + FIRST_ATTEMPT_DELAY_MS, episode, scope = Scope.HEAD_UNIT, ledger = ledger)
        )
    }

    @Test
    fun `a cover that read no call just before the call still sees its start`() {
        val quiet = reading(CallLedger(), base, callActive = false)
        assertTrue(reading(quiet, base + 400, callActive = true).startSeen)
    }

    @Test
    fun `a mode change confirms a start that a cover read first`() {
        val polled = reading(CallLedger(), base, callActive = true)
        assertFalse(polled.startSeen)
        val confirmed = reading(polled, base + 300, callActive = true, fromModeChange = true)
        assertTrue(confirmed.startSeen)
        assertEquals(base, confirmed.callSinceMs)
    }

    @Test
    fun `the reverse gear flag holds a head unit raise until the window ends`() {
        val ledger = seenCall(base)
        val episode = Episode(startedAtMs = base, sawCallActive = true, scope = Scope.HEAD_UNIT)
        assertEquals(
            Action.WAIT,
            decide(base + FIRST_ATTEMPT_DELAY_MS, episode, scope = Scope.HEAD_UNIT, ledger = ledger, reverseGear = true)
        )
        assertEquals(
            Action.DONE,
            decide(base + HEAD_UNIT_RAISE_WINDOW_MS + 100, episode, scope = Scope.HEAD_UNIT, ledger = ledger, reverseGear = true)
        )
    }

    @Test
    fun `a head unit call gets no attempt after it ends`() {
        val ended = reading(seenCall(base), base + 5_000, callActive = false)
        val episode = Episode(
            startedAtMs = base,
            sawCallActive = true,
            attempts = 1,
            callEndedAtMs = base + 5_000,
            scope = Scope.HEAD_UNIT,
        )
        assertEquals(
            Action.DONE,
            decide(base + 5_000 + POST_CALL_SETTLE_MS, episode, callActive = false, scope = Scope.HEAD_UNIT, ledger = ended)
        )
        assertNotEquals(
            Action.DONE,
            decide(base + 5_000 + POST_CALL_SETTLE_MS, episode.copy(scope = Scope.SELF_MODE), callActive = false)
        )
    }

    @Test
    fun `ticking slows down once the attempts are spent`() {
        val spending = Episode(startedAtMs = start, sawCallActive = true, attempts = MAX_ATTEMPTS_PER_CALL - 1)
        assertEquals(TICK_MS, SelfModeCallRaisePolicy.nextTickDelayMs(spending))

        val spent = spending.copy(attempts = MAX_ATTEMPTS_PER_CALL)
        assertEquals(IDLE_TICK_MS, SelfModeCallRaisePolicy.nextTickDelayMs(spent))

        val ended = spent.copy(callEndedAtMs = start + 30_000)
        assertEquals(TICK_MS, SelfModeCallRaisePolicy.nextTickDelayMs(ended))
    }

    /**
     * The reporter's cover: the call screen was up 82 ms after the tap and the audio mode did not
     * report the call for seconds. The episode has to survive that wait and then still raise.
     */
    @Test
    fun `a cover that becomes a call seconds later still raises`() {
        var episode = Episode(startedAtMs = start)

        var nowMs = start + TICK_MS
        while (nowMs < start + 3_000) {
            val (action, next) = tick(episode, nowMs, callActive = false)
            assertEquals(Action.WAIT, action)
            episode = next
            nowMs += TICK_MS
        }

        val (raise, after) = tick(episode, nowMs, callActive = true)
        assertEquals(Action.RAISE, raise)
        assertEquals(1, after.attempts)
        assertTrue(after.sawCallActive)
    }

    @Test
    fun `a cover is watched unless something says otherwise`() {
        assertEquals(CoverVerdict.OPEN, coverVerdict())
        assertEquals(CoverVerdict.USER_LEFT, coverVerdict(userLeft = true))
        assertEquals(CoverVerdict.NOT_SELF_MODE, coverVerdict(selfMode = false))
        assertEquals(CoverVerdict.PIP, coverVerdict(selfMode = false, headUnitEnabled = true, pipActive = true))
        assertEquals(CoverVerdict.PIP, coverVerdict(pipActive = true))
        assertEquals(CoverVerdict.DISABLED, coverVerdict(enabled = false))
        assertEquals(CoverVerdict.ALREADY_OPEN, coverVerdict(episodeOpen = true))
    }

    @Test
    fun `a head unit call screen opens an episode when the head unit switch is on`() {
        assertEquals(CoverVerdict.OPEN, coverVerdict(selfMode = false, headUnitEnabled = true))
        assertEquals(CoverVerdict.OPEN, coverVerdict(selfMode = false, headUnitEnabled = true, enabled = false))
    }

    @Test
    fun `a head unit call screen is left alone when the switch is off`() {
        val verdict = coverVerdict(selfMode = false, headUnitEnabled = false)
        assertEquals(CoverVerdict.NOT_SELF_MODE, verdict)
        assertTrue(verdict.reason.startsWith("not Self Mode"))
    }

    /** An episode already running owns the cover, whatever else is true of it. */
    @Test
    fun `an open episode is never restarted`() {
        assertEquals(
            CoverVerdict.ALREADY_OPEN,
            coverVerdict(userLeft = true, pipActive = true, episodeOpen = true)
        )
    }

    private fun coverVerdict(
        userLeft: Boolean = false,
        selfMode: Boolean = true,
        pipActive: Boolean = false,
        enabled: Boolean = true,
        headUnitEnabled: Boolean = false,
        episodeOpen: Boolean = false,
    ): CoverVerdict = SelfModeCallRaisePolicy.coverVerdict(
        userLeft = userLeft,
        selfMode = selfMode,
        pipActive = pipActive,
        enabled = enabled,
        headUnitEnabled = headUnitEnabled,
        episodeOpen = episodeOpen,
    )
}
