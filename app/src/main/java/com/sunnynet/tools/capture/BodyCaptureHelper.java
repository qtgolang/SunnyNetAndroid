package com.sunnynet.tools.capture;

import com.SunnyNet.Internal.HTTPEvent;
import com.SunnyNet.tools;

/**
 * 在 SDK 回调线程读取 Body，并做长度截断。
 */
final class BodyCaptureHelper {

    static final String MARKER_REQUEST = HttpDetailSections.MARKER_REQUEST_BODY_LEGACY;
    static final String MARKER_RESPONSE = HttpDetailSections.MARKER_RESPONSE_BODY_LEGACY;

    private BodyCaptureHelper() {
    }

    static String readHttpRequestHeaders(HTTPEvent conn) {
        if (conn == null || conn.Request() == null) {
            return "";
        }
        try {
            String headers = conn.Request().GetAllHeader();
            if (headers == null || headers.trim().isEmpty()) {
                return "（无请求头）";
            }
            return CaptureRepository.truncate(headers.trim());
        } catch (Throwable t) {
            return "（读取请求头失败: " + t.getMessage() + "）";
        }
    }

    static String readHttpResponseHeaders(HTTPEvent conn) {
        if (conn == null || conn.Response() == null) {
            return "";
        }
        try {
            String headers = conn.Response().GetAllHeader();
            if (headers == null || headers.trim().isEmpty()) {
                return "（无响应头）";
            }
            return CaptureRepository.truncate(headers.trim());
        } catch (Throwable t) {
            return "（读取响应头失败: " + t.getMessage() + "）";
        }
    }

    /**
     * 请求正文：按原始字节转连续 Hex 入库（与流协议一致），避免 {@code BodyToUTF8()} 损坏 PNG 等二进制。
     */
    static String readHttpRequestBody(HTTPEvent conn) {
        if (conn == null || conn.Request() == null) {
            return "";
        }
        try {
            byte[] raw = conn.Request().Body();
            if (raw == null || raw.length == 0) {
                return "（无请求体）";
            }
            return CaptureRepository.truncate(tools.bytesToHex(raw));
        } catch (Throwable t) {
            return "（读取请求体失败: " + t.getMessage() + "）";
        }
    }

    static int readHttpRequestBodyBytes(HTTPEvent conn) {
        if (conn == null || conn.Request() == null) {
            return 0;
        }
        try {
            byte[] body = conn.Request().Body();
            return body != null ? body.length : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static int readHttpResponseBodyBytes(HTTPEvent conn) {
        if (conn == null || conn.Response() == null) {
            return 0;
        }
        try {
            byte[] body = conn.Response().BodyAuto();
            return body != null ? body.length : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 读取请求 HTTP 版本，例如 HTTP/1.1。 */
    static String readHttpRequestProto(HTTPEvent conn) {
        if (conn == null || conn.Request() == null) {
            return "";
        }
        try {
            String proto = conn.Request().GetProto();
            return proto != null ? proto.trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 读取响应 HTTP 版本，例如 HTTP/1.1。 */
    static String readHttpResponseProto(HTTPEvent conn) {
        if (conn == null || conn.Response() == null) {
            return "";
        }
        try {
            String proto = conn.Response().GetProto();
            return proto != null ? proto.trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 响应正文：按 {@link com.SunnyNet.Internal.Response#BodyAuto()} 原始字节转 Hex，避免 {@code BodyAutoUTF8()}
     * 把图片等二进制变成不可解码的占位串。
     */
    static String readHttpResponseBody(HTTPEvent conn) {
        if (conn == null || conn.Response() == null) {
            return "";
        }
        try {
            byte[] raw = conn.Response().BodyAuto();
            if (raw == null || raw.length == 0) {
                return "（无响应体）";
            }
            return CaptureRepository.truncate(tools.bytesToHex(raw));
        } catch (Throwable t) {
            return "（读取响应体失败: " + t.getMessage() + "）";
        }
    }
}
