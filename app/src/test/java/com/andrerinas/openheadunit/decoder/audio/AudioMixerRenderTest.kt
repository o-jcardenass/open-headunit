package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AudioMixerRenderTest {
    /** Renders a twin channel and applies the dry output stage exactly as it was before the trim. */
    private fun oldDry(twins: List<AudioMixer.Channel>, now: Long, duck: Boolean): ShortArray {
        val mix = IntArray(960)
        val media = IntArray(960)
        val pcm = ShortArray(960)
        for (c in twins) {
            val active = c.buffer.render(pcm, now, 480)
            if (!active || c.gain == 0f) continue
            val dest = if (duck && c.isMediaSink) media else mix
            for (i in pcm.indices) dest[i] += (pcm[i] * c.gain).toInt()
        }
        return ShortArray(960) { i ->
            val sample = if (duck) mix[i] + media[i].toFloat().toInt() else mix[i]
            sample.coerceIn(-32768, 32767).toShort()
        }
    }

    private fun feed(states: List<AudioMixer.Channel>, pcm: ShortArray, cycle: Int) {
        for (s in states) {
            s.buffer.noteArrival(cycle * 10L, 480)
            s.buffer.write(pcm, pcm.size, cycle * 10L)
        }
    }

    private fun parity(duck: Boolean, gain: Float, media: Boolean, amplitude: Int) {
        val mixer = AudioMixer(duckMediaForSpeech = duck)
        val twinMixer = AudioMixer(duckMediaForSpeech = duck)
        val state = mixer.registerChannel(4, 48000, 2, 1, media)
        val twin = twinMixer.registerChannel(4, 48000, 2, 1, media)
        state.gain = gain; twin.gain = gain
        val random = Random(7)
        var sawSound = false
        repeat(60) { cycle ->
            val pcm = ShortArray(960) { ((random.nextInt(2 * amplitude + 1)) - amplitude).toShort() }
            feed(listOf(state, twin), pcm, cycle)
            val out = mixer.renderCycle(cycle * 10L, 480, arrayOf(state))
            sawSound = sawSound || out.any { it != 0.toShort() }
            assertArrayEquals("duck=$duck gain=$gain cycle=$cycle", oldDry(listOf(twin), cycle * 10L, duck), out)
        }
        assertTrue("never produced sound", sawSound)
    }

    @Test fun `one channel at unity gain matches the old stage`() = parity(false, 1f, false, 32768)

    @Test fun `one channel below unity matches the old stage`() = parity(false, 0.6f, false, 32768)

    @Test fun `a negative gain that clips stays clamped`() = parity(false, -2f, false, 30000)

    @Test fun `ducking on keeps the media Float round trip`() = parity(true, 1f, true, 32768)

    @Test fun `two channels match a twin mixer`() {
        val mixer = AudioMixer()
        val twinMixer = AudioMixer()
        val a = mixer.registerChannel(4, 48000, 2, 1, false)
        val b = mixer.registerChannel(5, 48000, 2, 1, false)
        val ta = twinMixer.registerChannel(4, 48000, 2, 1, false)
        val tb = twinMixer.registerChannel(5, 48000, 2, 1, false)
        val random = Random(11)
        repeat(30) { cycle ->
            val pcm = ShortArray(960) { (random.nextInt(40000) - 20000).toShort() }
            feed(listOf(a, b, ta, tb), pcm, cycle)
            assertArrayEquals("cycle=$cycle",
                twinMixer.renderCycle(cycle * 10L, 480, arrayOf(ta, tb)),
                mixer.renderCycle(cycle * 10L, 480, arrayOf(a, b)))
        }
    }

    @Test fun `silence returns zeros and every bank still renders`() {
        val mixer = AudioMixer()
        val state = mixer.registerChannel(4, 48000, 2, 1, false)
        repeat(10) { cycle ->
            feed(listOf(state), ShortArray(960) { 1234 }, cycle)
            mixer.renderCycle(cycle * 10L, 480, arrayOf(state))
        }
        state.buffer.finish()
        var now = 100L
        while (!state.buffer.isIdle() && now < 2000L) {
            mixer.renderCycle(now, 480, arrayOf(state))
            now += 10L
        }
        assertTrue(state.buffer.isIdle())
        assertArrayEquals(ShortArray(960), mixer.renderCycle(now, 480, arrayOf(state)))
        assertEquals(false, state.activeThisCycle)
    }
}
