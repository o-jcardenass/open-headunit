package com.andrerinas.openheadunit.decoder.audio

import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface VolumePort {
    fun index(stream: Int): Int
    fun adjust(stream: Int, raise: Boolean)
}

fun interface SpeedSource {
    fun speedKmh(): Float?
}

data class StoredLevel(val stream: Int, val index: Int, val level: Int)

interface LevelStore {
    fun load(): StoredLevel?
    fun save(value: StoredLevel)
}

/** What the session fixed at start: the stream, the audio sink flag and the switch. */
data class SessionSnapshot(val stream: Int, val sinkEnabled: Boolean, val switchOn: Boolean)

/**
 * Moves the media stream index one step per speed band by relative adjusts only. The held level
 * is what the index really holds, so a limit or a knob turn never drifts the volume.
 */
class SpeedVolumeController(
    private val volume: VolumePort,
    private val speed: SpeedSource,
    private val store: LevelStore,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val log: (String) -> Unit = AppLog::i
) {
    private var job: Job? = null
    private var snapshot: SessionSnapshot? = null
    private var policyState = SpeedVolumePolicy.State()
    private var heldLevel: Int? = null
    private var leftIndex = 0
    private var noFix = false

    fun start(session: SessionSnapshot, scope: CoroutineScope) {
        stop()
        if (!session.switchOn) {
            log("SpeedVolumeController: disabled")
            return
        }
        if (!session.sinkEnabled) {
            log("SpeedVolumeController: disabled: audio sink off")
            return
        }
        snapshot = session
        policyState = SpeedVolumePolicy.State()
        heldLevel = null
        noFix = false
        leftIndex = volume.index(session.stream)
        log("SpeedVolumeController: started stream ${session.stream} index $leftIndex")
        job = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    /** Moves nothing: the stored level lets the next session resume. */
    fun stop() {
        val running = job
        job = null
        snapshot = null
        policyState = SpeedVolumePolicy.State()
        heldLevel = null
        leftIndex = 0
        noFix = false
        if (running != null) {
            running.cancel()
            log("SpeedVolumeController: stopped")
        }
    }

    fun tick() {
        val session = snapshot ?: return
        val stream = session.stream
        val index = volume.index(stream)
        if (index != leftIndex) {
            log("SpeedVolumeController: manual change, index $leftIndex -> $index")
            leftIndex = index
            heldLevel?.let { store.save(StoredLevel(stream, index, it)) }
        }
        val kmh = speed.speedKmh()
        if (!SpeedVolumePolicy.isValid(kmh)) {
            if (!noFix) {
                noFix = true
                log("SpeedVolumeController: no fix, holding")
            }
            return
        }
        if (noFix) {
            noFix = false
            log("SpeedVolumeController: fix back")
        }
        val held = heldLevel
        if (held == null) {
            begin(stream, kmh!!)
            return
        }
        val result = SpeedVolumePolicy.next(policyState, kmh, clock())
        policyState = result.state
        if (result.step == SpeedVolumePolicy.Step.NONE) return
        val policyLevel = result.state.level ?: return
        val up = result.step == SpeedVolumePolicy.Step.UP
        val name = if (up) "UP" else "DOWN"
        if (!SpeedVolumePolicy.moves(result.step, policyLevel, held)) {
            log("SpeedVolumeController: step $name skipped, held level $held")
            return
        }
        val before = volume.index(stream)
        volume.adjust(stream, up)
        val after = volume.index(stream)
        leftIndex = after
        if (after == before) {
            log("SpeedVolumeController: step $name on stream $stream no change")
            return
        }
        val next = held + if (up) 1 else -1
        heldLevel = next
        store.save(StoredLevel(stream, after, next))
        log("SpeedVolumeController: level $held -> $next at ${kmh!!.roundToInt()} km/h, " +
            "step $name on stream $stream, index $before -> $after")
    }

    private fun begin(stream: Int, kmh: Float) {
        val level = SpeedVolumePolicy.levelFor(kmh)
        policyState = SpeedVolumePolicy.State(level, null)
        val stored = store.load()
        if (stored == null || stored.stream != stream || stored.index != leftIndex) {
            heldLevel = level
            store.save(StoredLevel(stream, leftIndex, level))
            log("SpeedVolumeController: base level $level at ${kmh.roundToInt()} km/h")
            return
        }
        val from = leftIndex
        var held = stored.level
        val steps = SpeedVolumePolicy.resume(stored.level, kmh)
        repeat(abs(steps)) {
            val before = volume.index(stream)
            volume.adjust(stream, steps > 0)
            if (volume.index(stream) != before) held += if (steps > 0) 1 else -1
        }
        leftIndex = volume.index(stream)
        heldLevel = held
        // A resume is a step, so the hold starts here.
        policyState = SpeedVolumePolicy.State(level, if (steps != 0) clock() else null)
        store.save(StoredLevel(stream, leftIndex, held))
        log("SpeedVolumeController: resume level ${stored.level} -> $held at ${kmh.roundToInt()} km/h, " +
            "index $from -> $leftIndex")
    }

    private companion object {
        const val TICK_MS = 2_000L
    }
}
