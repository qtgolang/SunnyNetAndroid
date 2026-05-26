package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HTTP 回调 {@code ClientIP()} 解析：区分本机 VPN 流量与局域网代理连接。
 */
public final class CaptureClientIp {

    private CaptureClientIp() {
    }

    /**
     * 是否为本机回环来源（127.0.0.1 开头，含带端口形式如 127.0.0.1:8888）。
     * 空串视为本机，与「系统进程」展示一致。
     */
    public static boolean isLocalLoopback(@Nullable String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }
        return extractHost(clientIp.trim()).startsWith("127.0.0.1");
    }

    @NonNull
    private static String extractHost(@NonNull String clientIp) {
        if (clientIp.startsWith("[")) {
            int end = clientIp.indexOf(']');
            return end > 0 ? clientIp.substring(0, end + 1) : clientIp;
        }
        int colon = clientIp.indexOf(':');
        if (colon > 0 && clientIp.indexOf('.') >= 0) {
            return clientIp.substring(0, colon);
        }
        return clientIp;
    }
}
