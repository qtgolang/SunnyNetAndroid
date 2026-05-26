package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * HTTP 升级 WebSocket 时的 URL 匹配（忽略 ws/wss 与 http/https  scheme 差异）。
 */
final class WebSocketUpgradeMatcher {

    private WebSocketUpgradeMatcher() {
    }

    /** HTTP 卡片标题是否对应 WS 连接 URL。 */
    static boolean matchesHttpRecord(@NonNull CaptureRecord record, @Nullable String wsUrl) {
        if (!CaptureRecord.TYPE_HTTP.equals(record.getProtocol()) || record.isStreamSession()) {
            return false;
        }
        String key = matchKey(wsUrl);
        if (key.isEmpty()) {
            return false;
        }
        return key.equals(matchKey(extractUrlFromHttpTitle(record.getTitle())));
    }

    /** 是否为 WebSocket 握手 HTTP 101 响应。 */
    static boolean isSwitchingProtocolsResponse(@NonNull CaptureRecord record) {
        String summary = record.getSummary();
        if (summary == null) {
            return false;
        }
        return summary.contains("HTTP 101") || summary.contains("101");
    }

    @NonNull
    private static String extractUrlFromHttpTitle(@Nullable String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        int space = title.indexOf(' ');
        if (space < 0) {
            return title.trim();
        }
        return title.substring(space + 1).trim();
    }

    @NonNull
    static String matchKey(@Nullable String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty()) {
            return "";
        }
        if (u.startsWith("wss://")) {
            u = "https://" + u.substring(6);
        } else if (u.startsWith("ws://")) {
            u = "http://" + u.substring(5);
        }
        while (u.endsWith("/") && u.length() > 1) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
