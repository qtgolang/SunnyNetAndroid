package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HTTP 详情分段标记与原始 HTTP 报文格式化。
 */
public final class HttpDetailSections {

    public static final String MARKER_REQUEST_HEADERS = "--- Request Headers ---";
    public static final String MARKER_REQUEST_BODY = "--- Request Body ---";
    public static final String MARKER_RESPONSE_HEADERS = "--- Response Headers ---";
    public static final String MARKER_RESPONSE_BODY = "--- Response Body ---";

    /** 兼容旧数据（与历史 BodyCaptureHelper 标记一致） */
    public static final String MARKER_REQUEST_BODY_LEGACY = "--- Request Body ---";
    public static final String MARKER_RESPONSE_BODY_LEGACY = "--- Response Body ---";

    private HttpDetailSections() {
    }

    /** 请求首行：METHOD URL HTTP/x.x */
    @NonNull
    public static String buildRequestFirstLine(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        String[] methodUrl = resolveMethodAndUrl(detail, record.getTitle());
        String proto = record.getRequestHttpProto();
        if (proto.isEmpty()) {
            proto = "HTTP/1.1";
        }
        return (methodUrl[0] + " " + methodUrl[1] + " " + proto).trim();
    }

    /** 请求协议头块（不含首行与 Body）。 */
    @NonNull
    public static String buildRequestHeadersOnly(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        return normalizeHeaderBlock(extractSection(detail, MARKER_REQUEST_HEADERS, MARKER_REQUEST_BODY));
    }

    /** 响应状态行，如 HTTP/1.1 200 OK；尚未响应时可能为空。 */
    @NonNull
    public static String buildResponseStatusLineForTab(@NonNull CaptureRecord record) {
        return buildHttpStatusLine(record);
    }

    /** 响应协议头块（不含状态行与 Body）。 */
    @NonNull
    public static String buildResponseHeadersOnly(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        return normalizeHeaderBlock(extractSection(detail, MARKER_RESPONSE_HEADERS, MARKER_RESPONSE_BODY));
    }

    @NonNull
    public static String buildRequestBodyFromRecord(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        return buildRequestBodyTab(detail);
    }

    @NonNull
    public static String buildResponseBodyFromRecord(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        return buildResponseBodyTab(detail);
    }

    /**
     * 原始报文：请求行 + 协议头 + 空行 + 请求体（Charles/Fiddler 风格）。
     */
    @NonNull
    public static String buildRawRequestMessage(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        String[] methodUrl = resolveMethodAndUrl(detail, record.getTitle());
        String method = methodUrl[0];
        String url = methodUrl[1];

        String headers = normalizeHeaderBlock(extractSection(detail,
                MARKER_REQUEST_HEADERS, MARKER_REQUEST_BODY));
        String body = normalizeBodyBlock(firstNonEmpty(
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_HEADERS),
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_BODY),
                extractSection(detail, MARKER_REQUEST_BODY_LEGACY, MARKER_RESPONSE_BODY_LEGACY)));

        String proto = record.getRequestHttpProto();
        if (proto.isEmpty()) {
            proto = "HTTP/1.1";
        }

        return appendHttpMessage(method + " " + url + " " + proto, headers, body);
    }

    /**
     * 原始报文：状态行 + 协议头 + 空行 + 响应体。
     */
    @NonNull
    public static String buildRawResponseMessage(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        String statusLine = buildHttpStatusLine(record);
        String headers = normalizeHeaderBlock(extractSection(detail,
                MARKER_RESPONSE_HEADERS, MARKER_RESPONSE_BODY));
        String body = normalizeBodyBlock(firstNonEmpty(
                extractSection(detail, MARKER_RESPONSE_BODY, null),
                extractSection(detail, MARKER_RESPONSE_BODY_LEGACY, null)));
        return appendHttpMessage(statusLine, headers, body);
    }

    /** 请求体 Tab：仅展示请求 Body 文本。 */
    @NonNull
    public static String buildRequestBodyTab(@NonNull String detail) {
        return firstNonEmpty(
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_HEADERS),
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_BODY),
                extractSection(detail, MARKER_REQUEST_BODY_LEGACY, MARKER_RESPONSE_BODY_LEGACY));
    }

    /** 响应体 Tab：仅展示响应 Body 文本。 */
    @NonNull
    public static String buildResponseBodyTab(@NonNull String detail) {
        return firstNonEmpty(
                extractSection(detail, MARKER_RESPONSE_BODY, null),
                extractSection(detail, MARKER_RESPONSE_BODY_LEGACY, null));
    }

    @NonNull
    public static String buildRequestTab(@NonNull String detail) {
        StringBuilder sb = new StringBuilder();
        appendBlock(sb, "Request Headers", extractSection(detail,
                MARKER_REQUEST_HEADERS, MARKER_REQUEST_BODY));
        String body = firstNonEmpty(
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_HEADERS),
                extractSection(detail, MARKER_REQUEST_BODY, MARKER_RESPONSE_BODY),
                extractSection(detail, MARKER_REQUEST_BODY_LEGACY, MARKER_RESPONSE_BODY_LEGACY));
        appendBlock(sb, "Request Body", body);
        return sb.toString().trim();
    }

    /** 响应 Tab：协议头 + 响应体 */
    @NonNull
    public static String buildResponseTab(@NonNull String detail) {
        StringBuilder sb = new StringBuilder();
        appendBlock(sb, "Response Headers", extractSection(detail,
                MARKER_RESPONSE_HEADERS, MARKER_RESPONSE_BODY));
        String body = firstNonEmpty(
                extractSection(detail, MARKER_RESPONSE_BODY, null),
                extractSection(detail, MARKER_RESPONSE_BODY_LEGACY, null));
        appendBlock(sb, "Response Body", body);
        return sb.toString().trim();
    }

    @NonNull
    static String extractSection(@NonNull String detail, @NonNull String startMarker,
                                 @Nullable String endMarker) {
        int start = detail.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        start += startMarker.length();
        int end = detail.length();
        if (endMarker != null && !endMarker.isEmpty()) {
            int idx = detail.indexOf(endMarker, start);
            if (idx >= 0) {
                end = idx;
            }
        }
        return detail.substring(start, end).trim();
    }

    @NonNull
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    @NonNull
    private static String extractLineValue(@NonNull String detail, @NonNull String prefix) {
        for (String line : detail.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static void appendBlock(@NonNull StringBuilder sb, @NonNull String title,
                                    @NonNull String content) {
        if (content.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("======== ").append(title).append(" ========\n");
        sb.append(content);
    }

    /** 拼接 HTTP 原始报文：首行、协议头、空行、正文。 */
    @NonNull
    private static String appendHttpMessage(@NonNull String firstLine, @NonNull String headers,
                                            @NonNull String body) {
        StringBuilder sb = new StringBuilder(firstLine);
        if (!headers.isEmpty()) {
            sb.append('\n').append(headers);
        }
        if (!body.isEmpty()) {
            sb.append("\n\n").append(body);
        }
        return sb.toString().trim();
    }

    /** 从 detail 与标题解析 METHOD、URL。 */
    @NonNull
    public static String[] parseMethodAndUrl(@NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        return resolveMethodAndUrl(detail, record.getTitle());
    }

    /** 从 detail 与标题解析 METHOD、URL。 */
    @NonNull
    private static String[] resolveMethodAndUrl(@NonNull String detail, @Nullable String title) {
        String method = extractLineValue(detail, "Method: ");
        String url = extractLineValue(detail, "URL: ");
        if (title != null && !title.isEmpty()) {
            String trimmed = title.trim();
            int space = trimmed.indexOf(' ');
            if (method.isEmpty() && space > 0) {
                method = trimmed.substring(0, space);
                url = trimmed.substring(space + 1).trim();
            } else if (url.isEmpty()) {
                url = trimmed;
            }
        }
        if (method.isEmpty()) {
            method = "GET";
        }
        return new String[]{method, url};
    }

    @NonNull
    private static String buildHttpStatusLine(@NonNull CaptureRecord record) {
        String summary = record.getSummary();
        if (summary != null && "请求".equals(summary.trim())) {
            return "";
        }
        String proto = record.getResponseHttpProto();
        if (proto.isEmpty()) {
            proto = "HTTP/1.1";
        }
        if (summary != null && summary.startsWith("HTTP")) {
            return formatStatusLine(summary, proto);
        }
        String detail = record.getDetail() != null ? record.getDetail() : "";
        for (String line : detail.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("HTTP")) {
                return formatStatusLine(trimmed, proto);
            }
        }
        return "";
    }

    @NonNull
    private static String formatStatusLine(@NonNull String summary, @NonNull String proto) {
        if (!summary.startsWith("HTTP")) {
            return "";
        }
        String codePart = summary.substring(4).trim();
        int space = codePart.indexOf(' ');
        String codeStr = space >= 0 ? codePart.substring(0, space) : codePart;
        try {
            int code = Integer.parseInt(codeStr);
            String phrase = reasonPhrase(code);
            return phrase.isEmpty()
                    ? proto + " " + code
                    : proto + " " + code + " " + phrase;
        } catch (NumberFormatException ignored) {
            return proto + " " + codePart;
        }
    }

    @NonNull
    private static String reasonPhrase(int code) {
        switch (code) {
            case 100:
                return "Continue";
            case 101:
                return "Switching Protocols";
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 304:
                return "Not Modified";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 408:
                return "Request Timeout";
            case 429:
                return "Too Many Requests";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            case 504:
                return "Gateway Timeout";
            default:
                return "";
        }
    }

    /** 去掉占位提示，保留真实协议头文本。 */
    @NonNull
    private static String normalizeHeaderBlock(@NonNull String headers) {
        if (headers.isEmpty() || isPlaceholder(headers)) {
            return "";
        }
        return headers.trim();
    }

    /** 去掉占位提示，保留真实 Body。 */
    @NonNull
    private static String normalizeBodyBlock(@NonNull String body) {
        if (body.isEmpty() || isPlaceholder(body)) {
            return "";
        }
        return body;
    }

    private static boolean isPlaceholder(@NonNull String text) {
        return text.startsWith("（无") || text.startsWith("（读取");
    }

    /**
     * 从已入库的响应协议头中读取 {@code Content-Type}，仅返回分号前的 MIME 主类型（已 trim）。
     * 例如 {@code text/xml; charset="utf-8"} → {@code text/xml}；非 HTTP、无响应头或无该字段时返回空串。
     */
    @NonNull
    public static String extractResponsePrimaryContentType(@NonNull CaptureRecord record) {
        if (!CaptureRecord.TYPE_HTTP.equals(record.getProtocol()) || record.isStreamSession()) {
            return "";
        }
        String block = buildResponseHeadersOnly(record);
        if (block.isEmpty()) {
            return "";
        }
        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if (!name.equalsIgnoreCase("Content-Type")) {
                continue;
            }
            String value = line.substring(colon + 1).trim();
            return primaryMimeBeforeSemicolon(value);
        }
        return "";
    }

    /**
     * 协议头取值中分号前的 MIME 一段；{@code charset} 等参数不展示。
     */
    @NonNull
    public static String primaryMimeBeforeSemicolon(@NonNull String headerValue) {
        int semi = headerValue.indexOf(';');
        String mime = semi >= 0 ? headerValue.substring(0, semi) : headerValue;
        return mime.trim();
    }
}
