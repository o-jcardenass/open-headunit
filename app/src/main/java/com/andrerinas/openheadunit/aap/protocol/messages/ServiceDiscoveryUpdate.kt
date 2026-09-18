package com.andrerinas.openheadunit.aap.protocol.messages

import android.content.Context
import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Control

/**
 * Control message 26. The only message that claims a service's configuration can change on a live
 * session, and the one Google's own UiConfig comment names for a codec or resolution change.
 */
class ServiceDiscoveryUpdate(service: Control.Service)
    : AapMessage(
        Channel.ID_CTR,
        Control.ControlMsgType.MESSAGE_SERVICE_DISCOVERY_UPDATE_VALUE,
        Control.ServiceDiscoveryUpdate.newBuilder().setService(service).build()
    ) {

    companion object {
        /** The video service as it stands now, so a rotation re-announces the shape it just derived. */
        fun forVideo(context: Context) = ServiceDiscoveryUpdate(ServiceDiscoveryResponse.videoService(context))
    }
}
