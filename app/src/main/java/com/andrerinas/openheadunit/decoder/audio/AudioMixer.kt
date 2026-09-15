package com.andrerinas.openheadunit.decoder.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.os.Build
import com.andrerinas.openheadunit.utils.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioMixer — Single-AudioTrack mixer for Static Audio Focus mode.
 *
 * When enabled, ALL audio channels (AUDIO/media, AUDIO1/assistant, AUDIO2/navigation)
 * are mixed in software and written to a single AudioTrack (48kHz, 16-bit, stereo).
 *
 * This prevents Chinese head unit firmware from reacting to the creation/destruction
 * of multiple AudioTrack instances, which causes volume routing bugs.
 */
class AudioMixer(
    private val stream: Int = AudioManager.STREAM_MUSIC,
    private val attachHwDspEqualizer: Boolean = false,
    /**
     * The user's setting, uncapped. One track serves every channel, so it is sized for the deepest
     * consumer; the per-channel caps live on the per-channel banks, where they cost nobody else.
     */
    private val audioLatencyMultiplier: Int = 8
) {

    companion object {
        private const val TAG = "AudioMixer"

        // Output format (fixed)
        const val OUTPUT_SAMPLE_RATE = 48000
        const val OUTPUT_CHANNELS = 2      // stereo
        const val OUTPUT_BIT_DEPTH = 16

        // Mix cycle interval in ms — balance between latency and CPU usage
        private const val MIX_INTERVAL_MS = 20L

        // Output frames drained per mix cycle: 48000 * 0.020 = 960 frames
        private const val SAMPLES_PER_CYCLE = (OUTPUT_SAMPLE_RATE * MIX_INTERVAL_MS / 1000).toInt()

        // The same cycle counted in shorts, which is what the channel rings hold: 960 * 2 = 1920.
        private const val SHORTS_PER_CYCLE = SAMPLES_PER_CYCLE * OUTPUT_CHANNELS

        // Channel ring capacity in shorts, and the same figure in output frames.
        private const val RING_SHORTS = 96000
        private const val RING_FRAMES = RING_SHORTS / OUTPUT_CHANNELS

        private const val BYTES_PER_OUTPUT_FRAME = OUTPUT_CHANNELS * 2
    }

    /**
     * Per-channel configuration and state.
     */
    data class ChannelConfig(
        val sampleRate: Int,        // e.g. 16000 or 48000
        val channelCount: Int       // 1 = mono, 2 = stereo
    )

    // The single AudioTrack
    private var audioTrack: AudioTrack? = null

    // Per-channel configuration
    private val channelConfigs = ConcurrentHashMap<Int, ChannelConfig>()

    // Per-channel PCM circular buffers
    private val channelBuffers = ConcurrentHashMap<Int, ShortCircularBuffer>()

    // Per-channel pre-rolling state
    private val channelPreRolling = ConcurrentHashMap<Int, Boolean>()

    // Per-channel gain (0.0 to 1.0+)
    private val channelGains = ConcurrentHashMap<Int, Float>()

    // Frames to bank before a channel plays. Derived from the chunks that channel actually
    // arrives in: a target under one chunk plus one drain re-banks forever. See
    // AudioJitterBufferPolicy.
    private val channelTargetShorts = ConcurrentHashMap<Int, Int>()

    // Largest chunk seen on a channel, in output shorts, which is what the target is sized from.
    private val channelChunkShorts = ConcurrentHashMap<Int, Int>()

    // The latency setting as it reaches each channel: capped for the prompt channels, raw for media.
    private val channelMultipliers = ConcurrentHashMap<Int, Int>()

    // Cycles a channel played silence mid-stream because it had run out. The instrument the mixer
    // never had: padding with zeros means the AudioTrack itself never underruns, so every framework
    // counter reads zero while the user hears gaps.
    //
    // Only counted once a channel has played, so the opening bank is not reported as a stutter. A
    // channel registered at setup and never sent anything used to score one of these per mix cycle:
    // 269 in the first window of a healthy run, for a sink that had no data yet.
    private val channelSilentCycles = ConcurrentHashMap<Int, Long>()
    private val channelRebanks = ConcurrentHashMap<Int, Long>()
    private val channelHasPlayed = ConcurrentHashMap<Int, Boolean>()
    private val channelOpeningBankCycles = ConcurrentHashMap<Int, Long>()

    // Mixing thread
    private var mixThread: Thread? = null
    private val running = AtomicBoolean(false)

    // Pre-allocated buffers for the mix loop to avoid GC allocation pressure
    private val mixBuffer = IntArray(SHORTS_PER_CYCLE)
    private val tempChannelBuffer = ShortArray(SHORTS_PER_CYCLE)
    private val outputBuffer = ShortArray(SHORTS_PER_CYCLE)

    // Reusable buffers for the feed thread(s) to avoid GC allocation pressure.
    // Since feed() is called from different AudioWriteThread instances concurrently,
    // we use ThreadLocal to ensure thread safety without synchronization overhead.
    private val feedInputBuffer = ThreadLocal<ShortArray>()
    private val feedOutputBuffer = ThreadLocal<ShortArray>()

    private fun getFeedInputBuffer(minSize: Int): ShortArray {
        var arr = feedInputBuffer.get()
        if (arr == null || arr.size < minSize) {
            arr = ShortArray(maxOf(minSize, 4096))
            feedInputBuffer.set(arr)
        }
        return arr
    }

    private fun getFeedOutputBuffer(minSize: Int): ShortArray {
        var arr = feedOutputBuffer.get()
        if (arr == null || arr.size < minSize) {
            arr = ShortArray(maxOf(minSize, 12288))
            feedOutputBuffer.set(arr)
        }
        return arr
    }

    /**
     * Start the mixer — creates the AudioTrack and starts the mixing thread.
     */
    fun start() {
        if (running.get()) {
            AppLog.i("$TAG: Already running")
            return
        }

        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val dataFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, channelConfig, dataFormat)
        val bufferSize = AudioBufferSizingPolicy.requestedBytes(
            minBufferSize, audioLatencyMultiplier, OUTPUT_SAMPLE_RATE, BYTES_PER_OUTPUT_FRAME
        )

        try {
            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(channelConfig)
                    .setEncoding(dataFormat)
                    .build()

                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    stream,
                    OUTPUT_SAMPLE_RATE,
                    channelConfig,
                    dataFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.let { track ->
                if (attachHwDspEqualizer) {
                    try {
                        val sessionId = track.audioSessionId
                        if (sessionId != AudioManager.AUDIO_SESSION_ID_GENERATE && sessionId > 0) {
                            val equalizer = Equalizer(0, sessionId)
                            equalizer.enabled = true
                            AppLog.i("$TAG: Attached dummy Equalizer to audioSessionId $sessionId to trigger HW DSP")
                        }
                    } catch (t: Throwable) {
                        // Ignore if Equalizer or AudioEffect is unsupported on device
                    }
                }
                track.play()
            }
            running.set(true)

            mixThread = Thread({
                AppLog.i("$TAG: Mix thread started")
                mixLoop()
                AppLog.i("$TAG: Mix thread stopped")
            }, "AudioMixer-Thread")
            mixThread?.start()

            // What the unit actually gave us, not what was asked for: setBufferSizeInBytes is a
            // request the framework may clamp, and no log could say what landed.
            val capacity = capacityFrames()
            val effective = effectiveFrames()
            AppLog.i(
                "$TAG: Started (asked $bufferSize bytes = ${msOf(bufferSize / BYTES_PER_OUTPUT_FRAME)}ms, " +
                    "minBuffer=$minBufferSize, capacity=$capacity frames (${msOf(capacity)}ms), " +
                    "effective=$effective frames (${msOf(effective)}ms), " +
                    "latencyMultiplier=$audioLatencyMultiplier)"
            )
        } catch (e: Exception) {
            AppLog.e("$TAG: Failed to start AudioTrack or mixer thread", e)
        }
    }

    /**
     * Stop the mixer — stops the mixing thread and releases the AudioTrack.
     */
    fun stop() {
        if (!running.getAndSet(false)) return

        try {
            mixThread?.interrupt()
            mixThread?.join(1000)
        } catch (e: Exception) {
            AppLog.e("$TAG: Error joining mix thread", e)
        }
        mixThread = null

        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            AppLog.e("$TAG: Error stopping AudioTrack: ${e.message}")
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            AppLog.e("$TAG: Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null

        channelBuffers.clear()
        channelPreRolling.clear()
        channelConfigs.clear()
        channelGains.clear()

        AppLog.i("$TAG: Stopped and released")
    }

    /**
     * Register a channel with its audio parameters.
     */
    fun registerChannel(
        channel: Int,
        sampleRate: Int,
        channelCount: Int,
        latencyMultiplier: Int = AudioJitterBufferPolicy.ANCHOR_MULTIPLIER
    ) {
        val config = ChannelConfig(sampleRate, channelCount)
        channelMultipliers[channel] = latencyMultiplier
        channelConfigs[channel] = config
        channelBuffers.putIfAbsent(channel, ShortCircularBuffer(RING_SHORTS)) // ~1s at 48kHz stereo
        channelPreRolling[channel] = true
        channelGains.putIfAbsent(channel, 1.0f)
        channelSilentCycles[channel] = 0L
        channelRebanks[channel] = 0L
        channelHasPlayed[channel] = false
        channelOpeningBankCycles[channel] = 0L
        channelChunkShorts[channel] = 0
        // Seeded at one drain, so the target starts at the time-based figure and feed() raises it
        // to whatever this channel is really sent. Assuming a large chunk here would delay a spoken
        // prompt by a third of a second on a guess.
        noteChunkShorts(channel, SHORTS_PER_CYCLE)

        val target = channelTargetShorts[channel] ?: SHORTS_PER_CYCLE
        AppLog.i(
            "$TAG: Registered channel $channel (sampleRate=$sampleRate, channels=$channelCount, " +
                "latencyMultiplier=$latencyMultiplier, " +
                "bank ${msOf(target / OUTPUT_CHANNELS)}ms before playing)"
        )
    }

    /** Output shorts one [chunkBytes] message of this channel's format becomes after resampling. */
    private fun outputShortsFor(config: ChannelConfig, chunkBytes: Int): Int {
        val inputBytesPerFrame = (config.channelCount * 2).coerceAtLeast(1)
        val inputFrames = chunkBytes / inputBytesPerFrame
        val rate = config.sampleRate.coerceAtLeast(1)
        val outputFrames = (inputFrames.toLong() * OUTPUT_SAMPLE_RATE / rate).toInt()
        return outputFrames * OUTPUT_CHANNELS
    }

    /**
     * Raise this channel's target if a larger chunk turns up. Only ever raises: a short tail at the
     * end of a prompt is not evidence the stream got easier.
     */
    private fun noteChunkShorts(channel: Int, chunkShorts: Int) {
        if (chunkShorts <= 0) return
        val seen = channelChunkShorts[channel] ?: 0
        if (chunkShorts <= seen) return
        channelChunkShorts[channel] = chunkShorts
        val targetFrames = AudioJitterBufferPolicy.targetFrames(
            sampleRateInHz = OUTPUT_SAMPLE_RATE,
            arrivalChunkFrames = chunkShorts / OUTPUT_CHANNELS,
            drainQuantumFrames = SAMPLES_PER_CYCLE,
            capacityFrames = RING_FRAMES,
            latencyMultiplier = channelMultipliers[channel] ?: AudioJitterBufferPolicy.ANCHOR_MULTIPLIER
        )
        channelTargetShorts[channel] = (targetFrames * OUTPUT_CHANNELS).coerceAtLeast(SHORTS_PER_CYCLE)
    }

    private fun msOf(frames: Int): Long = frames.toLong() * 1000L / OUTPUT_SAMPLE_RATE

    /**
     * Frames the output track can hold at all. `getBufferSizeInFrames()` answers the effective size
     * rather than the capacity, so the two are read apart rather than one printed as the other.
     */
    private fun capacityFrames(): Int {
        val track = audioTrack ?: return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val frames = try { track.bufferCapacityInFrames } catch (e: Exception) { 0 }
            if (frames > 0) return frames
        }
        return effectiveFrames()
    }

    /** Frames the framework will keep filled, which is at most the capacity. */
    private fun effectiveFrames(): Int {
        val track = audioTrack ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { track.bufferSizeInFrames } catch (e: Exception) { 0 }
        } else {
            0
        }
    }

    /** Output frames this channel banks before it plays. */
    fun targetFramesFor(channel: Int): Int =
        (channelTargetShorts[channel] ?: SHORTS_PER_CYCLE) / OUTPUT_CHANNELS

    /** Cycles this channel spent silent mid-stream, after it had started playing. */
    fun silentCyclesFor(channel: Int): Long = channelSilentCycles[channel] ?: 0L


    /** Times this channel fell under its target and had to bank again. */
    fun rebanksFor(channel: Int): Long = channelRebanks[channel] ?: 0L

    /** Output frames this channel is holding and has not yet been mixed. */
    fun depthFramesFor(channel: Int): Int =
        (channelBuffers[channel]?.size() ?: 0) / OUTPUT_CHANNELS

    /**
     * Unregister a channel (e.g. on session end).
     */
    fun unregisterChannel(channel: Int) {
        channelConfigs.remove(channel)
        channelBuffers.remove(channel)
        channelPreRolling.remove(channel)
        channelGains.remove(channel)
        channelTargetShorts.remove(channel)
        channelMultipliers.remove(channel)
        channelChunkShorts.remove(channel)
        channelSilentCycles.remove(channel)
        channelRebanks.remove(channel)
        channelHasPlayed.remove(channel)
        channelOpeningBankCycles.remove(channel)
        AppLog.i("$TAG: Unregistered channel $channel")
    }

    /**
     * Feed raw PCM data for a channel. Called from the wrapper/decoder thread.
     * Data format: 16-bit signed little-endian PCM.
     * Resamples to 48kHz stereo directly on the caller thread to offload calculations.
     */
    fun feed(channel: Int, data: ByteArray, offset: Int, length: Int) {
        val config = channelConfigs[channel] ?: return
        val buffer = channelBuffers[channel] ?: return
        if (length <= 0) return

        noteChunkShorts(channel, outputShortsFor(config, length))

        val inputShortsSize = length / 2
        if (inputShortsSize <= 0) return

        // 1. Convert bytes (little-endian) to shorts
        val inputShorts = getFeedInputBuffer(inputShortsSize)
        for (i in 0 until inputShortsSize) {
            val idx = offset + i * 2
            val lo = data[idx].toInt() and 0xFF
            val hi = data[idx + 1].toInt()
            inputShorts[i] = ((hi shl 8) or lo).toShort()
        }

        // 2. Resample / convert channels and write to buffer
        if (config.sampleRate == OUTPUT_SAMPLE_RATE && config.channelCount == OUTPUT_CHANNELS) {
            // No conversion needed, just write directly
            buffer.write(inputShorts, inputShortsSize)
        } else if (config.sampleRate == 16000 && config.channelCount == 1) {
            // Highly optimized path: 16kHz mono to 48kHz stereo (ratio = 3)
            val outputShortsSize = inputShortsSize * 3 * 2
            val outputShorts = getFeedOutputBuffer(outputShortsSize)
            var outIdx = 0
            for (i in 0 until inputShortsSize) {
                val current = inputShorts[i].toInt()
                val next = if (i + 1 < inputShortsSize) inputShorts[i + 1].toInt() else current

                for (j in 0 until 3) {
                    val fraction = j.toFloat() / 3f
                    val interpolated = (current + (next - current) * fraction).toInt().coerceIn(-32768, 32767).toShort()
                    outputShorts[outIdx++] = interpolated // Left
                    outputShorts[outIdx++] = interpolated // Right
                }
            }
            buffer.write(outputShorts, outputShortsSize)
        } else {
            // Generic linear resampling and channel configuration fallback
            val invRatio = config.sampleRate.toFloat() / OUTPUT_SAMPLE_RATE.toFloat()
            val ratio = OUTPUT_SAMPLE_RATE.toFloat() / config.sampleRate.toFloat()
            val inputChannels = config.channelCount
            val inputFrames = inputShortsSize / inputChannels
            val outputFrames = (inputFrames * ratio).toInt()
            val outputShortsSize = outputFrames * OUTPUT_CHANNELS
            val outputShorts = getFeedOutputBuffer(outputShortsSize)

            var outIdx = 0
            for (outFrame in 0 until outputFrames) {
                val inFrameExact = outFrame * invRatio
                val inFrameLow = inFrameExact.toInt()
                val fraction = inFrameExact - inFrameLow
                val inFrameHigh = if (inFrameLow + 1 < inputFrames) inFrameLow + 1 else inFrameLow

                val currentL = inputShorts[inFrameLow * inputChannels].toInt()
                val nextL = inputShorts[inFrameHigh * inputChannels].toInt()
                val interpolatedL = (currentL + (nextL - currentL) * fraction).toInt().coerceIn(-32768, 32767).toShort()

                val currentR = if (inputChannels == 2) inputShorts[inFrameLow * 2 + 1].toInt() else currentL
                val nextR = if (inputChannels == 2) inputShorts[inFrameHigh * 2 + 1].toInt() else nextL
                val interpolatedR = (currentR + (nextR - currentR) * fraction).toInt().coerceIn(-32768, 32767).toShort()

                outputShorts[outIdx++] = interpolatedL
                outputShorts[outIdx++] = interpolatedR
            }
            buffer.write(outputShorts, outputShortsSize)
        }
    }

    /**
     * Set per-channel gain (for ducking).
     * @param gain 0.0 = silence, 1.0 = full volume
     */
    fun setChannelGain(channel: Int, gain: Float) {
        channelGains[channel] = gain
    }

    /**
     * Get current gain for a channel.
     */
    fun getChannelGain(channel: Int): Float = channelGains[channel] ?: 1.0f

    /**
     * Check if the mixer is currently running.
     */
    fun isRunning(): Boolean = running.get()

    /**
     * Check if a channel is registered.
     */
    fun hasChannel(channel: Int): Boolean = channelConfigs.containsKey(channel)

    // ========================================================================
    // Private mixing logic
    // ========================================================================

    /**
     * Soft-clipping function to prevent digital distortion (hard clipping) when 
     * multiple channels are mixed together. Compresses signals exceeding ~ -4dB (20480).
     */
    private fun softClip(s: Int): Short {
        val clippedS = s.coerceIn(-98304, 98304)
        if (clippedS > 20480) {
            val diff = clippedS - 20480
            return (20480 + (diff * 12287) / (diff + 24574)).toShort()
        } else if (clippedS < -20480) {
            val diff = -clippedS - 20480
            return (-(20480 + (diff * 12287) / (diff + 24574))).toShort()
        }
        return clippedS.toShort()
    }

    /**
     * Main mixing loop — runs on the dedicated thread.
     * Every MIX_INTERVAL_MS, drains all channel circular buffers,
     * mixes into a single buffer, and writes to the AudioTrack.
     */
    private fun mixLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        while (running.get()) {
            // Clear mix buffer
            mixBuffer.fill(0)
            var hasData = false

            // Process each registered channel
            for ((channel, buffer) in channelBuffers) {
                val gain = channelGains[channel] ?: 1.0f
                var isPreRolling = channelPreRolling[channel] ?: true

                // Sized from the chunks this channel arrives in. Three mix cycles used to be the
                // target, which is 60ms against a 42.7ms message: the trough fell under one cycle
                // once per arrival and the channel re-banked forever, on a link with no jitter.
                val target = channelTargetShorts[channel] ?: SHORTS_PER_CYCLE

                if (isPreRolling) {
                    if (buffer.size() >= target) {
                        channelPreRolling[channel] = false
                        isPreRolling = false
                    } else {
                        // Still banking, so this channel contributes silence to the mix. Before it
                        // has ever played that is the opening bank, which nobody is listening
                        // through yet; after it, it is a gap.
                        if (channelHasPlayed[channel] == true) {
                            channelSilentCycles[channel] = (channelSilentCycles[channel] ?: 0L) + 1L
                        } else {
                            channelOpeningBankCycles[channel] =
                                (channelOpeningBankCycles[channel] ?: 0L) + 1L
                        }
                        continue
                    }
                }

                // Double check if we still have enough samples for a complete cycle to prevent underruns
                if (buffer.size() >= SHORTS_PER_CYCLE) {
                    val read = buffer.read(tempChannelBuffer, 0, SHORTS_PER_CYCLE)
                    if (read > 0) {
                        hasData = true
                        if (channelHasPlayed[channel] != true) {
                            channelHasPlayed[channel] = true
                            val bankedMs = (channelOpeningBankCycles[channel] ?: 0L) * MIX_INTERVAL_MS
                            AppLog.i(
                                "$TAG: channel $channel started playing after banking for ${bankedMs}ms " +
                                    "- silentCycles counts gaps from here"
                            )
                        }
                        for (i in 0 until read) {
                            mixBuffer[i] += (tempChannelBuffer[i] * gain).toInt()
                        }
                    }
                } else {
                    // Ran dry mid-stream. Bank again rather than mix a partial cycle, and count it:
                    // this is the only place a mixer stutter is visible, because the output track
                    // is padded with zeros and so never underruns itself.
                    channelPreRolling[channel] = true
                    channelSilentCycles[channel] = (channelSilentCycles[channel] ?: 0L) + 1L
                    channelRebanks[channel] = (channelRebanks[channel] ?: 0L) + 1L
                }
            }

            // Apply soft clipping and write output (always write to keep the AudioTrack active)
            for (i in mixBuffer.indices) {
                outputBuffer[i] = softClip(mixBuffer[i])
            }

            val track = audioTrack
            var written = 0
            if (track != null) {
                try {
                    written = track.write(outputBuffer, 0, SHORTS_PER_CYCLE)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error writing to AudioTrack", e)
                }
            }

            // If write fails, track is null, or not playing, sleep to prevent a busy loop.
            // If it succeeds and is playing, track.write() blocks and acts as our hardware clock regulator.
            if (written <= 0 || track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    Thread.sleep(MIX_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    /**
     * Thread-safe, garbage-free circular buffer for Short audio samples.
     */
    private class ShortCircularBuffer(capacity: Int) {
        private val buffer = ShortArray(capacity)
        private var head = 0
        private var tail = 0
        private var size = 0

        @Synchronized
        fun write(data: ShortArray, length: Int): Int {
            var written = 0
            for (i in 0 until length) {
                if (size >= buffer.size) {
                    break
                }
                buffer[tail] = data[i]
                tail = (tail + 1) % buffer.size
                size++
                written++
            }
            return written
        }

        @Synchronized
        fun read(out: ShortArray, offset: Int, length: Int): Int {
            var read = 0
            for (i in 0 until length) {
                if (size == 0) {
                    break
                }
                out[offset + i] = buffer[head]
                head = (head + 1) % buffer.size
                size--
                read++
            }
            return read
        }

        @Synchronized
        fun size(): Int = size

        @Synchronized
        fun clear() {
            head = 0
            tail = 0
            size = 0
        }
    }
}
