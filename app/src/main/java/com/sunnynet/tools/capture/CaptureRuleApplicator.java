package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.SunnyNet.Internal.Const;
import com.SunnyNet.Internal.HTTPEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 在 HTTP 回调线程应用已启用规则；按 V5 作用域/匹配位置执行，仅重写规则需要 URL。
 */
public final class CaptureRuleApplicator {

    private CaptureRuleApplicator() {
    }

    /**
     * @return true 表示请求已被屏蔽，调用方应跳过后续抓包记录
     */
    public static boolean apply(@NonNull HTTPEvent conn) {
        CaptureRuleStore store;
        try {
            store = CaptureRuleStore.get();
        } catch (IllegalStateException e) {
            return false;
        }
        if (!store.isRulesEnabled()) {
            return false;
        }
        int eventType = conn.Type();
        if (eventType != Const.EventType_HTTP_Request
                && eventType != Const.EventType_HTTP_Response) {
            return false;
        }
        List<CaptureRule> rules = store.getEnabledRules();
        for (CaptureRule rule : rules) {
            switch (rule.getType()) {
                case BLOCK:
                    if (shouldBlockHttp(conn, eventType, rule)) {
                        if (eventType == Const.EventType_HTTP_Request) {
                            conn.Request().Stop();
                            return true;
                        }
                    }
                    break;
                case HOSTS:
                    if (eventType == Const.EventType_HTTP_Request) {
                        applyHosts(conn, rule);
                    }
                    break;
                case REPLACE:
                    applyReplaceHttp(conn, eventType, rule);
                    break;
                case REWRITE:
                    if (eventType == Const.EventType_HTTP_Request) {
                        applyRewrite(conn, rule);
                    }
                    break;
                case INTERCEPT:
                    // 断点编辑与放行 UI 待第二期对接
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean shouldBlockHttp(@NonNull HTTPEvent conn, int eventType, @NonNull CaptureRule rule) {
        String scope = eventType == Const.EventType_HTTP_Request
                ? CaptureRuleConstants.SCOPE_HTTP_REQUEST
                : CaptureRuleConstants.SCOPE_HTTP_RESPONSE;
        if (!rule.hasScope(scope)) {
            return false;
        }
        StringBuilder data = new StringBuilder();
        if (scope.equals(CaptureRuleConstants.SCOPE_HTTP_REQUEST)) {
            data.append(nullToEmpty(conn.URL())).append('\n');
            data.append(nullToEmpty(conn.Request().GetAllHeader()));
            data.append(nullToEmpty(conn.Request().BodyToUTF8()));
        } else {
            data.append(nullToEmpty(conn.Response().GetAllHeader()));
            data.append(nullToEmpty(conn.Response().BodyToUTF8()));
        }
        return CaptureRuleMatcher.matchText(data.toString(), rule.getMatchType(), rule.getMatchData());
    }

    private static void applyReplaceHttp(@NonNull HTTPEvent conn, int eventType, @NonNull CaptureRule rule) {
        if (eventType == Const.EventType_HTTP_Request) {
            if (rule.hasScope(CaptureRuleConstants.SCOPE_HTTP_REQUEST)) {
                replaceRequest(conn, rule);
            }
        } else if (eventType == Const.EventType_HTTP_Response) {
            if (rule.hasScope(CaptureRuleConstants.SCOPE_HTTP_RESPONSE)) {
                replaceResponse(conn, rule);
            }
        }
    }

    private static void replaceRequest(@NonNull HTTPEvent conn, @NonNull CaptureRule rule) {
        String url = conn.URL();
        if (url != null) {
            byte[] nextUrl = CaptureRuleMatcher.replaceBytes(url.getBytes(StandardCharsets.UTF_8), rule);
            if (nextUrl != null && !url.equals(new String(nextUrl, StandardCharsets.UTF_8))) {
                conn.Request().SetUrl(new String(nextUrl, StandardCharsets.UTF_8));
            }
        }
        String headers = conn.Request().GetAllHeader();
        if (headers != null) {
            byte[] nextHeaders = CaptureRuleMatcher.replaceBytes(headers.getBytes(StandardCharsets.UTF_8), rule);
            if (nextHeaders != null) {
                conn.Request().SetALLHeader(new String(nextHeaders, StandardCharsets.UTF_8));
            }
        }
        byte[] body = conn.Request().Body();
        if (body != null && body.length > 0) {
            byte[] nextBody = CaptureRuleMatcher.replaceBytes(body, rule);
            if (nextBody != null) {
                conn.Request().Body(nextBody);
            }
        }
    }

    private static void replaceResponse(@NonNull HTTPEvent conn, @NonNull CaptureRule rule) {
        String headers = conn.Response().GetAllHeader();
        if (headers != null) {
            byte[] nextHeaders = CaptureRuleMatcher.replaceBytes(headers.getBytes(StandardCharsets.UTF_8), rule);
            if (nextHeaders != null) {
                conn.Response().SetResponseAllHeader(new String(nextHeaders, StandardCharsets.UTF_8));
            }
        }
        byte[] body = conn.Response().Body();
        if (body != null && body.length > 0) {
            byte[] nextBody = CaptureRuleMatcher.replaceBytes(body, rule);
            if (nextBody != null) {
                conn.Response().Body(nextBody);
            }
        }
    }

    private static void applyHosts(@NonNull HTTPEvent conn, @NonNull CaptureRule rule) {
        String oldHost = rule.getOldHost().trim();
        String newHost = rule.getNewHost().trim();
        if (oldHost.isEmpty() || newHost.isEmpty()) {
            return;
        }
        try {
            URI uri = URI.create(conn.URL());
            String currentHost = uri.getHost();
            if (currentHost == null) {
                return;
            }
            if (!hostMatches(oldHost, currentHost) && !hostMatches(oldHost, uri.getAuthority())) {
                return;
            }
            int port = uri.getPort();
            String targetHost = newHost;
            if (!newHost.contains(":") && port > 0) {
                targetHost = newHost + ':' + port;
            }
            URI mapped = new URI(uri.getScheme(), uri.getUserInfo(), targetHost,
                    -1, uri.getPath(), uri.getQuery(), uri.getFragment());
            conn.Request().SetUrl(mapped.toString());
            conn.Request().SetHeader("Host", targetHost);
        } catch (Exception ignored) {
        }
    }

    private static boolean hostMatches(@NonNull String ruleHost, @Nullable String endpoint) {
        if (endpoint == null) {
            return false;
        }
        return ruleHost.equalsIgnoreCase(endpoint.trim());
    }

    private static void applyRewrite(@NonNull HTTPEvent conn, @NonNull CaptureRule rule) {
        String target = CaptureRuleMatcher.normalizeUrlWithoutQuery(conn.URL());
        String match = CaptureRuleMatcher.normalizeUrlWithoutQuery(rule.getMatchURL());
        if (match.isEmpty() || !match.equals(target)) {
            return;
        }
        if (!CaptureRuleMatcher.rewriteMethodMatched(rule.getMatchMethod(), conn.Method())) {
            return;
        }
        HTTPEvent.Response response = conn.Response();
        long status = 200L;
        String statusText = rule.getStatusCode().trim();
        if (!statusText.isEmpty()) {
            try {
                status = Long.parseLong(statusText);
            } catch (NumberFormatException ignored) {
            }
        }
        response.StatusCode(status);
        if (!rule.getHeaders().trim().isEmpty()) {
            response.SetResponseAllHeader(rule.getHeaders().trim());
        }
        if (!rule.getResponseData().trim().isEmpty()) {
            response.BodyToUTF8(rule.getResponseData());
        }
    }

    @NonNull
    private static String nullToEmpty(@Nullable String value) {
        return value != null ? value : "";
    }
}
