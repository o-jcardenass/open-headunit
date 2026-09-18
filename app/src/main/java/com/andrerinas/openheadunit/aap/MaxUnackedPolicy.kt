package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.Channel

/**
 * How many messages the phone may have in flight before it waits for an ack. The only backpressure
 * signal AAP gives a head unit, so the numbers are load-bearing rather than tuning.
 */
object MaxUnackedPolicy {

    fun forChannel(channel: Int, wireless: Boolean, bundledSoftwareHevc: Boolean): Int {
        if (channel == Channel.ID_VID) {
            if (bundledSoftwareHevc) {
                // Keep the phone closer to decoder pace. A large wireless window lets video
                // backlog turn into visible input lag when 2K HEVC is decoded in software.
                return if (wireless) 6 else 8
            }
            // Left wide for hardware decode, deliberately: a keyframe fragments into a dozen or
            // more messages, so narrowing this stalls the phone mid-keyframe and caps throughput at
            // window/RTT. The phone does not hold to it either - one told 12 ran our backlog to 120
            // - so the bound that works is the decoder discarding frames it is behind on.
            return if (wireless) 12 else 16
        }

        // Audio still benefits from a wider jitter window, especially on wireless.
        return if (wireless) 30 else 16
    }
}
