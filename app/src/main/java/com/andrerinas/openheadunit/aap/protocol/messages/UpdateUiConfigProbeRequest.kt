package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message

/** Probe only: a UiConfig update with margins or content insets alone, and never `ui_theme`. */
class UpdateUiConfigProbeRequest(kind: String, left: Int, top: Int, right: Int, bottom: Int) : AapMessage(
    Channel.ID_VID,
    Media.MsgType.MEDIA_MESSAGE_UPDATE_UI_CONFIG_REQUEST_VALUE,
    build(kind, left, top, right, bottom)
) {
    companion object {
        internal fun build(kind: String, left: Int, top: Int, right: Int, bottom: Int): Message {
            val insets = Media.Insets.newBuilder()
                .setLeft(left).setTop(top).setRight(right).setBottom(bottom).build()
            val config = Media.UiConfig.newBuilder().also {
                if (kind == "insets") it.setContentInsets(insets) else it.setMargins(insets)
            }.build()
            return Media.UpdateUiConfigRequest.newBuilder().setUiConfig(config).build()
        }
    }
}
