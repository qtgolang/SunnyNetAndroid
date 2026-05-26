package com.sunnynet.tools.capture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 单条抓包记录，供列表与详情页展示。
 * HTTP 为请求/响应；WS/TCP/UDP 为会话 + 流列表。
 */
public class CaptureRecord {

    public static final String TYPE_HTTP = "HTTP";
    public static final String TYPE_TCP = "TCP";
    public static final String TYPE_UDP = "UDP";
    public static final String TYPE_WEBSOCKET = "WebSocket";

    public static final String STATE_UNKNOWN = "unknown";
    public static final String STATE_CONNECTING = "connecting";
    public static final String STATE_CONNECTED = "connected";
    public static final String STATE_DISCONNECTED = "disconnected";

    private static final AtomicLong STREAM_SEQ = new AtomicLong(1);
    /** {@link #buildSearchText()} 时间格式化（SimpleDateFormat 非线程安全） */
    private static final Object SEARCH_TIME_LOCK = new Object();
    private static final SimpleDateFormat SEARCH_TIME_HMS = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final SimpleDateFormat SEARCH_TIME_FULL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private final long id;
    private String protocol;
    private final long theologyId;
    private final long timestampMs;
    private boolean streamSession;
    private String title;
    private String summary;
    private String detail;
    private String connectionState = STATE_UNKNOWN;
    /** 产生流量的应用包名（来自 SDK 回调 conn.PackageName()） */
    private String packageName = "";
    /** HTTP 客户端 IP（来自 SDK conn.ClientIP()） */
    private String clientIp = "";
    /** 请求 HTTP 版本（来自 SDK Request.GetProto()） */
    private String requestHttpProto = "";
    /** 响应 HTTP 版本（来自 SDK Response.GetProto()） */
    private String responseHttpProto = "";
    /** HTTP 响应状态码；0 表示尚未收到响应 */
    private int httpStatusCode;
    /** 响应到达时间戳；0 表示尚未收到响应 */
    private long responseTimestampMs;
    /** 请求 Body 字节数；-1 表示未知（旧记录） */
    private long requestBodyBytes = -1;
    /** 响应 Body 字节数；-1 表示未知（旧记录） */
    private long responseBodyBytes = -1;
    private final List<CaptureStreamEntry> streamEntries = new ArrayList<>();

    public CaptureRecord(long id, String protocol, long theologyId, String title, String summary, String detail) {
        this(id, protocol, theologyId, title, summary, detail, false);
    }

    public CaptureRecord(long id, String protocol, long theologyId, String title, String summary,
                         String detail, boolean streamSession) {
        this(id, protocol, theologyId, System.currentTimeMillis(), title, summary, detail,
                streamSession, STATE_UNKNOWN, "", "", "", "", 0, 0, -1, -1, null);
    }

    /** 从 ObjectBox / 导入文件还原 */
    public static CaptureRecord fromStore(long id, String protocol, long theologyId, long timestampMs,
                                          String title, String summary, String detail,
                                          boolean streamSession, String connectionState,
                                          String packageName, String clientIp, String requestHttpProto,
                                          String responseHttpProto, int httpStatusCode,
                                          long responseTimestampMs, long requestBodyBytes,
                                          long responseBodyBytes,
                                          List<CaptureStreamEntry> streams) {
        CaptureRecord record = new CaptureRecord(id, protocol, theologyId, timestampMs, title, summary,
                detail, streamSession, connectionState, packageName, clientIp,
                requestHttpProto, responseHttpProto, httpStatusCode, responseTimestampMs,
                requestBodyBytes, responseBodyBytes, streams);
        if (streamSession) {
            record.refreshStreamSummary();
        }
        return record;
    }

    private CaptureRecord(long id, String protocol, long theologyId, long timestampMs,
                          String title, String summary, String detail, boolean streamSession,
                          String connectionState, String packageName, String clientIp,
                          String requestHttpProto,
                          String responseHttpProto, int httpStatusCode, long responseTimestampMs,
                          long requestBodyBytes, long responseBodyBytes,
                          List<CaptureStreamEntry> streams) {
        this.id = id;
        this.protocol = protocol;
        this.theologyId = theologyId;
        this.timestampMs = timestampMs;
        this.title = title;
        this.summary = summary;
        this.detail = detail;
        this.streamSession = streamSession;
        this.connectionState = connectionState != null ? connectionState : STATE_UNKNOWN;
        this.packageName = packageName != null ? packageName : "";
        this.clientIp = clientIp != null ? clientIp : "";
        this.requestHttpProto = requestHttpProto != null ? requestHttpProto : "";
        this.responseHttpProto = responseHttpProto != null ? responseHttpProto : "";
        this.httpStatusCode = httpStatusCode;
        this.responseTimestampMs = responseTimestampMs;
        this.requestBodyBytes = requestBodyBytes;
        this.responseBodyBytes = responseBodyBytes;
        if (streams != null) {
            streamEntries.addAll(streams);
        }
    }

    public static boolean isStreamProtocol(String protocol) {
        return TYPE_WEBSOCKET.equals(protocol)
                || TYPE_TCP.equals(protocol)
                || TYPE_UDP.equals(protocol);
    }

    public static String stateLabel(String state) {
        if (STATE_CONNECTED.equals(state)) {
            return "已连接";
        }
        if (STATE_DISCONNECTED.equals(state)) {
            return "已断开";
        }
        if (STATE_CONNECTING.equals(state)) {
            return "连接中";
        }
        return "未知";
    }

    public long getId() {
        return id;
    }

    public String getProtocol() {
        return protocol;
    }

    public long getTheologyId() {
        return theologyId;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public boolean isStreamSession() {
        return streamSession;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetail() {
        return detail;
    }

    public String getConnectionState() {
        return connectionState;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        this.packageName = packageName;
    }

    public String getClientIp() {
        return clientIp;
    }

    /** 写入 HTTP 回调 ClientIP；非空时覆盖。 */
    public void setClientIp(@Nullable String clientIp) {
        if (clientIp != null && !clientIp.isEmpty()) {
            this.clientIp = clientIp;
        }
    }

    /** 包名为空且 ClientIP 非 127.0.0.1 开头时，列表/详情显示「代理请求」。 */
    public boolean isProxyRequestDisplay() {
        if (packageName != null && !packageName.isEmpty()) {
            return false;
        }
        return !CaptureClientIp.isLocalLoopback(clientIp);
    }

    public String getRequestHttpProto() {
        return requestHttpProto;
    }

    public void setRequestHttpProto(@Nullable String requestHttpProto) {
        if (requestHttpProto != null && !requestHttpProto.isEmpty()) {
            this.requestHttpProto = requestHttpProto;
        }
    }

    public String getResponseHttpProto() {
        return responseHttpProto;
    }

    public void setResponseHttpProto(@Nullable String responseHttpProto) {
        if (responseHttpProto != null && !responseHttpProto.isEmpty()) {
            this.responseHttpProto = responseHttpProto;
        }
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        if (httpStatusCode > 0) {
            this.httpStatusCode = httpStatusCode;
        }
    }

    public long getResponseTimestampMs() {
        return responseTimestampMs;
    }

    public void setResponseTimestampMs(long responseTimestampMs) {
        if (responseTimestampMs > 0) {
            this.responseTimestampMs = responseTimestampMs;
        }
    }

    public long getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(long requestBodyBytes) {
        if (requestBodyBytes >= 0) {
            this.requestBodyBytes = requestBodyBytes;
        }
    }

    public long getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(long responseBodyBytes) {
        if (responseBodyBytes >= 0) {
            this.responseBodyBytes = responseBodyBytes;
        }
    }

    public List<CaptureStreamEntry> getStreamEntries() {
        synchronized (streamEntries) {
            return Collections.unmodifiableList(new ArrayList<>(streamEntries));
        }
    }

    /**
     * 列表搜索用全文：在当前协议类型下，匹配 URL、HTTP(S) 请求/响应头与体（均含于 {@link #detail}）、
     * WebSocket/TCP/UDP 流正文，以及每条流的「发送/接收（共 N 字节）」与 N 的十进制串；另有列表展示相关字段（协议、标题、摘要、状态、包名、时间等）。
     */
    @NonNull
    public String buildSearchText() {
        StringBuilder sb = new StringBuilder(512);
        appendSearchPart(sb, protocol);
        appendSearchPart(sb, title);
        appendSearchPart(sb, summary);
        appendSearchPart(sb, detail);
        appendSearchPart(sb, packageName);
        appendSearchPart(sb, clientIp);
        appendSearchPart(sb, requestHttpProto);
        appendSearchPart(sb, responseHttpProto);
        appendSearchPart(sb, stateLabel(connectionState));
        if (httpStatusCode > 0) {
            appendSearchPart(sb, Integer.toString(httpStatusCode));
            appendSearchPart(sb, "HTTP " + httpStatusCode);
        }
        if (requestBodyBytes >= 0) {
            appendSearchPart(sb, Long.toString(requestBodyBytes));
        }
        if (responseBodyBytes >= 0) {
            appendSearchPart(sb, Long.toString(responseBodyBytes));
        }
        appendSearchPart(sb, Long.toString(theologyId));
        appendSearchPart(sb, Long.toString(timestampMs));
        appendSearchPart(sb, formatSearchTime(timestampMs));
        if (responseTimestampMs > 0) {
            appendSearchPart(sb, Long.toString(responseTimestampMs));
            appendSearchPart(sb, formatSearchTime(responseTimestampMs));
        }
        synchronized (streamEntries) {
            for (CaptureStreamEntry e : streamEntries) {
                String bodyPart = e.getBody() != null ? e.getBody() : "";
                int byteLen = StreamPayloadFormatter.logicalPayloadByteCount(bodyPart);
                appendSearchPart(sb, e.getDirection());
                appendSearchPart(sb, Integer.toString(byteLen));
                appendSearchPart(sb, e.getDirection() + "（共" + byteLen + "字节）");
                appendSearchPart(sb, bodyPart);
                appendSearchPart(sb, Long.toString(e.getTimestampMs()));
                appendSearchPart(sb, formatSearchTime(e.getTimestampMs()));
            }
        }
        return sb.toString();
    }

    private static void appendSearchPart(StringBuilder sb, @Nullable String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(part);
    }

    private static String formatSearchTime(long ms) {
        Date d = new Date(ms);
        synchronized (SEARCH_TIME_LOCK) {
            return SEARCH_TIME_HMS.format(d) + "\n" + SEARCH_TIME_FULL.format(d);
        }
    }

    public int getStreamCount() {
        synchronized (streamEntries) {
            return streamEntries.size();
        }
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setConnectionState(String connectionState) {
        this.connectionState = connectionState != null ? connectionState : STATE_UNKNOWN;
        refreshStreamSummary();
    }

    /**
     * WebSocket 握手成功后，将对应 HTTP 101 卡片升级为 WS 流会话（保留原请求/响应详情）。
     */
    public void promoteToWebSocketSession(String wsUrl, @Nullable String method) {
        this.protocol = TYPE_WEBSOCKET;
        this.streamSession = true;
        if (wsUrl != null && !wsUrl.isEmpty()) {
            this.title = wsUrl;
        }
        this.detail = upsertDetailLine(detail, "协议: ", TYPE_WEBSOCKET);
        if (method != null && !method.isEmpty()) {
            this.detail = upsertDetailLine(this.detail, "Method: ", method);
        }
        if (wsUrl != null && !wsUrl.isEmpty()) {
            this.detail = upsertDetailLine(this.detail, "URL: ", wsUrl);
        }
        setConnectionState(STATE_CONNECTED);
    }

    private static String upsertDetailLine(@Nullable String detail, String prefix, String value) {
        String text = detail != null ? detail : "";
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                if (!replaced) {
                    appendLine(out, prefix + value);
                    replaced = true;
                }
            } else {
                appendLine(out, line);
            }
        }
        if (!replaced) {
            appendLine(out, prefix + value);
        }
        return out.toString();
    }

    private static void appendLine(StringBuilder out, String line) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(line);
    }

    public void addStreamEntry(String direction, String body, int maxEntries) {
        synchronized (streamEntries) {
            streamEntries.add(0, new CaptureStreamEntry(STREAM_SEQ.getAndIncrement(), direction, body));
            while (streamEntries.size() > maxEntries) {
                streamEntries.remove(streamEntries.size() - 1);
            }
        }
        refreshStreamSummary();
    }

    /** 会话卡片副标题：状态 + 流条数 */
    public void refreshStreamSummary() {
        if (!streamSession) {
            return;
        }
        summary = stateLabel(connectionState) + " · " + getStreamCount() + " 条流";
    }

    public void appendDetail(String text) {
        if (detail == null || detail.isEmpty()) {
            detail = text;
        } else {
            detail = detail + "\n\n" + text;
        }
    }
}
