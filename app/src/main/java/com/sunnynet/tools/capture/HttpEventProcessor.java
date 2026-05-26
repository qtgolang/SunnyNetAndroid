package com.sunnynet.tools.capture;

import com.SunnyNet.Internal.Const;

/**
 * 在 {@link CaptureEventQueue} 线程中处理已提取的 HTTP 事件字段。
 * 请求、响应、错误均按 theologyId 聚合到同一张卡片；未命中索引时再新建。
 */
final class HttpEventProcessor {

    private HttpEventProcessor() {
    }

    static void process(int type, long theologyId, String method, String url, String error,
                        long statusCode, String requestBody, String responseBody,
                        String requestHeaders, String responseHeaders,
                        String requestProto, String responseProto,
                        int requestBodyBytes, int responseBodyBytes, String packageName,
                        String clientIp) {
        switch (type) {
            case Const.EventType_HTTP_Request:
                handleRequest(theologyId, method, url, packageName, clientIp, requestProto,
                        requestHeaders, requestBody, requestBodyBytes);
                break;
            case Const.EventType_HTTP_Response:
                handleResponse(theologyId, url, packageName, clientIp, responseProto, statusCode,
                        responseHeaders, responseBody, responseBodyBytes);
                break;
            case Const.EventType_HTTP_Error:
                handleError(theologyId, url, error, packageName, clientIp);
                break;
            default:
                break;
        }
    }

    private static void handleRequest(long theologyId, String method, String url,
                                      String packageName, String clientIp, String requestProto,
                                      String requestHeaders, String requestBody,
                                      int requestBodyBytes) {
        CaptureRepository repo = CaptureRepository.get();
        CaptureRecord existing = repo.findByTheologyId(theologyId);
        if (existing != null && isMergeableHttpRecord(existing)) {
            applyRequestToRecord(existing, method, url, packageName, clientIp, requestProto,
                    requestHeaders, requestBody, requestBodyBytes);
            repo.update(existing);
            return;
        }
        String requestDetail = buildRequestDetail(method, url, requestHeaders, requestBody);
        CaptureRecord record = repo.add(
                CaptureRecord.TYPE_HTTP,
                theologyId,
                method + " " + url,
                "请求",
                requestDetail,
                packageName
        );
        record.setClientIp(clientIp);
        record.setRequestHttpProto(requestProto);
        if (requestBodyBytes >= 0) {
            record.setRequestBodyBytes(requestBodyBytes);
        }
        repo.update(record);
    }

    private static void handleResponse(long theologyId, String url, String packageName,
                                       String clientIp, String responseProto, long statusCode,
                                       String responseHeaders, String responseBody,
                                       int responseBodyBytes) {
        String statusLine = "HTTP " + statusCode;
        long responseAt = System.currentTimeMillis();
        CaptureRepository repo = CaptureRepository.get();
        CaptureRecord existing = repo.findByTheologyId(theologyId);
        if (existing != null && isMergeableHttpRecord(existing)) {
            existing.setSummary(statusLine);
            existing.setPackageName(packageName);
            existing.setClientIp(clientIp);
            existing.setResponseHttpProto(responseProto);
            existing.setHttpStatusCode((int) statusCode);
            existing.setResponseTimestampMs(responseAt);
            if (responseBodyBytes >= 0) {
                existing.setResponseBodyBytes(responseBodyBytes);
            }
            appendResponseSection(existing, responseHeaders, responseBody);
            repo.update(existing);
            return;
        }
        String detail = statusLine + buildResponseAppendix(responseHeaders, responseBody);
        CaptureRecord record = repo.add(
                CaptureRecord.TYPE_HTTP,
                theologyId,
                url,
                statusLine,
                detail,
                packageName
        );
        record.setClientIp(clientIp);
        record.setResponseHttpProto(responseProto);
        record.setHttpStatusCode((int) statusCode);
        record.setResponseTimestampMs(responseAt);
        if (responseBodyBytes >= 0) {
            record.setResponseBodyBytes(responseBodyBytes);
        }
        repo.update(record);
    }

    private static void handleError(long theologyId, String url, String error, String packageName,
                                    String clientIp) {
        String errorText = error != null ? error : "";
        String errorSummary = "错误: " + errorText;
        CaptureRepository repo = CaptureRepository.get();
        CaptureRecord existing = repo.findByTheologyId(theologyId);
        if (existing != null && isMergeableHttpRecord(existing)) {
            existing.setSummary(errorSummary);
            existing.setPackageName(packageName);
            existing.setClientIp(clientIp);
            applyErrorDetail(existing, errorText);
            repo.update(existing);
            return;
        }
        CaptureRecord record = repo.add(
                CaptureRecord.TYPE_HTTP,
                theologyId,
                url,
                errorSummary,
                errorText,
                packageName
        );
        record.setClientIp(clientIp);
        repo.update(record);
    }

    /** 仅 HTTP 单条记录可合并；已升级为流会话的卡片不再写入 HTTP 分段。 */
    private static boolean isMergeableHttpRecord(CaptureRecord record) {
        return CaptureRecord.TYPE_HTTP.equals(record.getProtocol()) && !record.isStreamSession();
    }

    private static void applyRequestToRecord(CaptureRecord record, String method, String url,
                                             String packageName, String clientIp, String requestProto,
                                             String requestHeaders, String requestBody,
                                             int requestBodyBytes) {
        record.setTitle(method + " " + url);
        record.setPackageName(packageName);
        record.setClientIp(clientIp);
        record.setRequestHttpProto(requestProto);
        if (requestBodyBytes >= 0) {
            record.setRequestBodyBytes(requestBodyBytes);
        }
        if (detailHasRequestSection(record.getDetail())) {
            return;
        }
        String requestDetail = buildRequestDetail(method, url, requestHeaders, requestBody);
        String existingDetail = record.getDetail();
        if (existingDetail == null || existingDetail.isEmpty()) {
            record.setDetail(requestDetail);
        } else {
            record.setDetail(requestDetail + "\n\n" + existingDetail);
        }
        if (record.getHttpStatusCode() <= 0 && record.getResponseTimestampMs() <= 0
                && !isErrorSummary(record.getSummary())) {
            record.setSummary("请求");
        }
    }

    private static void applyErrorDetail(CaptureRecord record, String error) {
        if (error.isEmpty()) {
            return;
        }
        String detail = record.getDetail();
        if (detail != null && detail.contains(error)) {
            return;
        }
        record.appendDetail("错误:\n" + error);
    }

    private static boolean isErrorSummary(String summary) {
        return summary != null && summary.startsWith("错误:");
    }

    private static boolean detailHasRequestSection(String detail) {
        if (detail == null || detail.isEmpty()) {
            return false;
        }
        return detail.contains(HttpDetailSections.MARKER_REQUEST_HEADERS)
                || detail.contains(HttpDetailSections.MARKER_REQUEST_BODY);
    }

    private static String buildRequestDetail(String method, String url,
                                             String requestHeaders, String requestBody) {
        String bodyText = requestBody != null ? requestBody : "（无请求体）";
        String headersText = requestHeaders != null ? requestHeaders : "（无请求头）";
        return "URL: " + url
                + "\nMethod: " + method
                + "\n\n" + HttpDetailSections.MARKER_REQUEST_HEADERS + "\n" + headersText
                + "\n\n" + HttpDetailSections.MARKER_REQUEST_BODY + "\n" + bodyText;
    }

    private static void appendResponseSection(CaptureRecord record, String responseHeaders,
                                              String responseBody) {
        String detail = record.getDetail();
        if (detail != null && (detail.contains(HttpDetailSections.MARKER_RESPONSE_BODY)
                || detail.contains(BodyCaptureHelper.MARKER_RESPONSE))) {
            return;
        }
        record.appendDetail(buildResponseAppendix(responseHeaders, responseBody));
    }

    private static String buildResponseAppendix(String responseHeaders, String responseBody) {
        StringBuilder section = new StringBuilder("\n\n");
        String headersText = responseHeaders != null ? responseHeaders : "（无响应头）";
        String body = responseBody != null ? responseBody : "（无响应体）";
        section.append(HttpDetailSections.MARKER_RESPONSE_HEADERS).append('\n')
                .append(headersText)
                .append("\n\n")
                .append(HttpDetailSections.MARKER_RESPONSE_BODY).append('\n')
                .append(body);
        return section.toString();
    }
}
