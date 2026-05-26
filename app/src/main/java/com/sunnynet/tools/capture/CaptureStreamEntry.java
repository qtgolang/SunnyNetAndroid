package com.sunnynet.tools.capture;

/**
 * 单条连接内的收发流（WebSocket / TCP / UDP 会话消息）。
 */
public class CaptureStreamEntry {

    private final long id;
    private final long timestampMs;
    private final String direction;
    private final String body;

    public CaptureStreamEntry(long id, String direction, String body) {
        this(id, direction, body, System.currentTimeMillis());
    }

    public CaptureStreamEntry(long id, String direction, String body, long timestampMs) {
        this.id = id;
        this.timestampMs = timestampMs;
        this.direction = direction != null ? direction : "";
        this.body = body != null ? body : "";
    }

    public long getId() {
        return id;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getDirection() {
        return direction;
    }

    public String getBody() {
        return body;
    }

    /** 列表预览：截断后的正文 */
    public String preview() {
        String text = body.replace('\n', ' ').trim();
        if (text.length() <= 96) {
            return text.isEmpty() ? "（空）" : text;
        }
        return text.substring(0, 96) + "…";
    }
}
