package com.andrerinas.openheadunit.decoder.audio

/** Names the likeliest cause of one audible discontinuity from facts the mixer already holds. */
internal object AudioStutterAttribution {
    enum class Kind { XRUN, CONCEAL, STALE, COMPRESS }
    enum class Cause { MIXER_STALL, PHONE_FLOW_RESET, LINK_LATE, PHONE_SOURCE_GAP, STREAM_EDGE, UNKNOWN }

    /**
     * The phone waits 300 ms for an ack permit, then sends max_unacked - 1 frames at once
     * (15 on USB, 29 on wireless), well above the 3 frame burst asked for here.
     */
    const val FLOW_RESET_GAP_MS = 280L
    /** A longer gap is a link hole: TCP delivers its backlog as a burst too. */
    const val FLOW_RESET_MAX_GAP_MS = 400L
    const val MARGIN_MS = 20L
    const val EDGE_MS = 250L

    /** [unfedMs] is how long the device went without a write. -1 means unknown for the gap fields. */
    data class Facts(
        val unfedMs: Long,
        val deviceMs: Long,
        val arrivalGapMs: Long,
        val sourceGapMs: Long,
        val frameMs: Long,
        val flowReset: Boolean,
        val edgeMs: Long,
        val isMediaSink: Boolean
    )

    /** First match wins, in the order the causes are cheapest to rule in. */
    fun classify(f: Facts): Cause {
        val late = f.frameMs + MARGIN_MS
        return when {
            f.unfedMs > f.deviceMs -> Cause.MIXER_STALL
            f.flowReset -> Cause.PHONE_FLOW_RESET
            f.arrivalGapMs > late && f.sourceGapMs in 0..late -> Cause.LINK_LATE
            f.sourceGapMs > late -> Cause.PHONE_SOURCE_GAP
            !f.isMediaSink && f.edgeMs in 0..EDGE_MS -> Cause.STREAM_EDGE
            else -> Cause.UNKNOWN
        }
    }
}

/**
 * Per-channel arrival facts for [AudioStutterAttribution]: the latest late arrival, the latest
 * source gap, and whether a long gap was followed by a burst of normally spaced frames.
 */
internal class AudioIngressTracker {
    private var lastArrivalMs = -1L
    private var gapMs = -1L
    private var gapSourceMs = -1L
    private var gapAtMs = -1L
    private var burstCount = 0
    private var burstSourceOk = true
    private var burstSourceKnown = false
    private var sourceGapMs = -1L
    private var sourceAtMs = -1L
    private var frameMs = 20L
    private var burstAudioMs = 0L

    /** [sourceGap] is the phone's own interval to the previous frame, or -1 when unknown. */
    @Synchronized fun onArrival(nowMs: Long, frame: Long, sourceGap: Long) {
        frameMs = frame.coerceAtLeast(1)
        val late = frameMs + AudioStutterAttribution.MARGIN_MS
        val gap = if (lastArrivalMs >= 0) nowMs - lastArrivalMs else 0L
        // AAC units arrive in groups: a gap is late against the audio the last group carried.
        val together = lastArrivalMs >= 0 && gap < frameMs / 2
        val lateGap = !together && gap > burstAudioMs + AudioStutterAttribution.MARGIN_MS
        burstAudioMs = if (together) burstAudioMs + frameMs else frameMs
        if (lateGap) {
            gapMs = gap
            gapSourceMs = sourceGap
            gapAtMs = nowMs
            burstCount = 0
            burstSourceOk = true
            burstSourceKnown = false
        } else if (gapAtMs >= 0 && nowMs - gapAtMs <= BURST_MS) {
            burstCount++
            if (sourceGap > late) burstSourceOk = false
            if (sourceGap >= 0) burstSourceKnown = true
        }
        if (sourceGap > late) { sourceGapMs = sourceGap; sourceAtMs = nowMs }
        lastArrivalMs = nowMs
    }

    @Synchronized fun reset() {
        lastArrivalMs = -1L; gapMs = -1L; gapAtMs = -1L; sourceAtMs = -1L
        burstCount = 0; burstSourceOk = true; burstSourceKnown = false; burstAudioMs = 0L
    }

    /** [unfedMs] and [deviceMs] come from the mixer, [edgeMs] from the last START or STOP. */
    @Synchronized fun facts(nowMs: Long, unfedMs: Long, deviceMs: Long, edgeMs: Long,
                            isMediaSink: Boolean): AudioStutterAttribution.Facts {
        val recentGap = gapAtMs >= 0 && nowMs - gapAtMs <= RECENT_MS
        val recentSource = sourceAtMs >= 0 && nowMs - sourceAtMs <= RECENT_MS
        // An unknown source gap is not a normal one: the burst must carry the phone's own timing.
        val flow = recentGap &&
            gapMs in AudioStutterAttribution.FLOW_RESET_GAP_MS..AudioStutterAttribution.FLOW_RESET_MAX_GAP_MS &&
            burstCount >= BURST_FRAMES && burstSourceOk && burstSourceKnown &&
            gapSourceMs in 0..frameMs + AudioStutterAttribution.MARGIN_MS
        return AudioStutterAttribution.Facts(unfedMs, deviceMs,
            if (recentGap) gapMs else -1L,
            if (recentGap) gapSourceMs else if (recentSource) sourceGapMs else -1L,
            frameMs, flow, edgeMs, isMediaSink)
    }

    private companion object {
        const val BURST_MS = 50L
        const val BURST_FRAMES = 3
        const val RECENT_MS = 1000L
    }
}

/**
 * The longest time the device went without a write: between writes, or inside one call or wait.
 * The whole write is not counted, because its wait for room is normal back-pressure, not a stall.
 */
internal class MixerUnfedGap {
    private var lastWriteDoneMs = -1L
    private var recentMs = 0L
    private var prevMs = 0L
    private var nextRotateMs = 0L

    fun onCycle(nowMs: Long) {
        if (nowMs < nextRotateMs) return
        prevMs = recentMs; recentMs = 0L; nextRotateMs = nowMs + WINDOW_MS
    }

    fun onWriteStart(nowMs: Long) {
        if (lastWriteDoneMs >= 0) recentMs = maxOf(recentMs, nowMs - lastWriteDoneMs)
    }

    fun onWriteDone(nowMs: Long) { lastWriteDoneMs = nowMs }

    /**
     * One device call or one wait for room inside a write: time beyond the audio it accepted is
     * time off the CPU, so a stall inside the write still counts. Each call is taken alone.
     */
    fun onDeviceCall(startMs: Long, endMs: Long, acceptedMs: Long) {
        recentMs = maxOf(recentMs, endMs - startMs - acceptedMs)
    }

    /** Output held back for focus: each held cycle counts as an instant write, so only a late one is a stall. */
    fun onHold(nowMs: Long) {
        onWriteStart(nowMs)
        lastWriteDoneMs = nowMs
    }

    /** The device is not consuming, so a pause or an idle wait is not time unfed. */
    fun reset() { lastWriteDoneMs = -1L }

    fun gapMs(nowMs: Long): Long =
        maxOf(recentMs, prevMs, if (lastWriteDoneMs >= 0) nowMs - lastWriteDoneMs else 0L)

    private companion object { const val WINDOW_MS = 100L }
}

/** At most [perSecond] log lines a second; the rest are counted for the window roll-up. */
internal class StutterLogBudget(private val perSecond: Int = 2) {
    private var windowStartMs = -1L
    private var used = 0
    private var suppressed = 0

    fun tryLog(nowMs: Long): Boolean {
        if (windowStartMs < 0 || nowMs - windowStartMs >= 1000 || nowMs < windowStartMs) {
            windowStartMs = nowMs
            used = 0
        }
        if (used < perSecond) { used++; return true }
        suppressed++
        return false
    }

    fun takeSuppressed(): Int = suppressed.also { suppressed = 0 }
}
