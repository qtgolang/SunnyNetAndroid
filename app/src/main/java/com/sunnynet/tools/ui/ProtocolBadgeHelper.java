package com.sunnynet.tools.ui;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;

/**
 * 为列表项协议标签设置配色。
 */
public final class ProtocolBadgeHelper {

    private ProtocolBadgeHelper() {
    }

    public static void apply(@NonNull TextView protocolView, @NonNull String protocol) {
        int bgRes;
        int textColorRes;
        if (CaptureRecord.TYPE_HTTP.equals(protocol)) {
            bgRes = R.drawable.bg_protocol_http;
            textColorRes = R.color.protocol_http_text;
        } else if (CaptureRecord.TYPE_WEBSOCKET.equals(protocol)) {
            bgRes = R.drawable.bg_protocol_ws;
            textColorRes = R.color.protocol_ws_text;
        } else if (CaptureRecord.TYPE_TCP.equals(protocol)) {
            bgRes = R.drawable.bg_protocol_tcp;
            textColorRes = R.color.protocol_tcp_text;
        } else if (CaptureRecord.TYPE_UDP.equals(protocol)) {
            bgRes = R.drawable.bg_protocol_udp;
            textColorRes = R.color.protocol_udp_text;
        } else {
            bgRes = R.drawable.bg_protocol_http;
            textColorRes = R.color.protocol_http_text;
        }
        protocolView.setBackgroundResource(bgRes);
        protocolView.setTextColor(protocolView.getContext().getColor(textColorRes));
    }
}
