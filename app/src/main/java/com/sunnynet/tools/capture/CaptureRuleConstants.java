package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;

/**
 * 规则作用域、匹配类型等常量，对齐 SunnyNetV5 {@code RulesLayout.vue}。
 */
public final class CaptureRuleConstants {

    public static final String MATCH_STRING = "string";
    public static final String MATCH_HEX = "hex";
    public static final String MATCH_BASE64 = "base64";
    public static final String MATCH_REGEX = "regex";

    public static final String SCOPE_HTTP_REQUEST = "http_request";
    public static final String SCOPE_HTTP_RESPONSE = "http_response";
    public static final String SCOPE_WS_SEND = "ws_send";
    public static final String SCOPE_WS_RECEIVE = "ws_receive";
    public static final String SCOPE_TCP_SEND = "tcp_send";
    public static final String SCOPE_TCP_RECEIVE = "tcp_receive";
    public static final String SCOPE_UDP_SEND = "udp_send";
    public static final String SCOPE_UDP_RECEIVE = "udp_receive";

    public static final String MATCH_IN_URL = "url";
    public static final String MATCH_IN_REQUEST_HEADER = "request_header";
    public static final String MATCH_IN_REQUEST_BODY = "request_body";
    public static final String MATCH_IN_RESPONSE_HEADER = "response_header";
    public static final String MATCH_IN_RESPONSE_BODY = "response_body";

    public static final String REWRITE_METHOD_ALL = "ALL";
    public static final String RESPONSE_TYPE_TEXT = "text";

    private CaptureRuleConstants() {
    }

    /** 替换/屏蔽可用的 8 种作用域。 */
    @NonNull
    public static ScopeOption[] allScopes() {
        return new ScopeOption[]{
                new ScopeOption(SCOPE_HTTP_REQUEST, "HTTP 请求"),
                new ScopeOption(SCOPE_HTTP_RESPONSE, "HTTP 响应"),
                new ScopeOption(SCOPE_WS_SEND, "WS 发送"),
                new ScopeOption(SCOPE_WS_RECEIVE, "WS 接收"),
                new ScopeOption(SCOPE_TCP_SEND, "TCP 发送"),
                new ScopeOption(SCOPE_TCP_RECEIVE, "TCP 接收"),
                new ScopeOption(SCOPE_UDP_SEND, "UDP 发送"),
                new ScopeOption(SCOPE_UDP_RECEIVE, "UDP 接收"),
        };
    }

    /** 拦截规则仅 HTTP 请求/响应。 */
    @NonNull
    public static ScopeOption[] interceptScopes() {
        return new ScopeOption[]{
                new ScopeOption(SCOPE_HTTP_REQUEST, "HTTP 请求"),
                new ScopeOption(SCOPE_HTTP_RESPONSE, "HTTP 响应"),
        };
    }

    @NonNull
    public static MatchInOption[] interceptMatchIns() {
        return new MatchInOption[]{
                new MatchInOption(MATCH_IN_URL, "URL",
                        new String[]{SCOPE_HTTP_REQUEST, SCOPE_HTTP_RESPONSE}),
                new MatchInOption(MATCH_IN_REQUEST_HEADER, "请求协议头",
                        new String[]{SCOPE_HTTP_REQUEST}),
                new MatchInOption(MATCH_IN_REQUEST_BODY, "请求正文",
                        new String[]{SCOPE_HTTP_REQUEST}),
                new MatchInOption(MATCH_IN_RESPONSE_HEADER, "响应协议头",
                        new String[]{SCOPE_HTTP_RESPONSE}),
                new MatchInOption(MATCH_IN_RESPONSE_BODY, "响应正文",
                        new String[]{SCOPE_HTTP_RESPONSE}),
        };
    }

    @NonNull
    public static String[] matchTypeLabels() {
        return new String[]{"字符串", "十六进制", "Base64", "正则"};
    }

    @NonNull
    public static String[] matchTypeValues() {
        return new String[]{MATCH_STRING, MATCH_HEX, MATCH_BASE64, MATCH_REGEX};
    }

    @NonNull
    public static String[] rewriteMethods() {
        return new String[]{"ALL", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};
    }

    public static final class ScopeOption {
        public final String id;
        public final String label;

        ScopeOption(@NonNull String id, @NonNull String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final class MatchInOption {
        public final String id;
        public final String label;
        @NonNull
        public final String[] scopes;

        MatchInOption(@NonNull String id, @NonNull String label, @NonNull String[] scopes) {
            this.id = id;
            this.label = label;
            this.scopes = scopes;
        }
    }
}
