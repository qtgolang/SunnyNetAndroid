package com.sunnynet.tools.capture;

import com.SunnyNet.Internal.Const;
import com.SunnyNet.Internal.HTTPEvent;
import com.SunnyNet.Internal.SunnyNetCallback;
import com.SunnyNet.Internal.TCPEvent;
import com.SunnyNet.Internal.UDPEvent;
import com.SunnyNet.Internal.WebSocketEvent;
import com.SunnyNet.tools;

/**
 * SunnyNet 流量回调：回调线程仅复制字段并入队，不做 Body 解析、不触碰主线程。
 */
public class PacketCaptureCallback implements SunnyNetCallback {

    @Override
    public void onHTTPCallback(HTTPEvent conn) {
        if (CaptureRuleApplicator.apply(conn)) {
            return;
        }
        if (!PacketCaptureGate.shouldRecordHttp()) {
            return;
        }
        final int type = conn.Type();
        final long theologyId = conn.TheologyID();
        final String method = conn.Method();
        final String url = conn.URL();
        final String error = conn.Error();
        final String packageName = conn.PackageName();
        final String clientIp = conn.ClientIP();
        final long statusCode = type == Const.EventType_HTTP_Response
                ? conn.Response().StatusCode() : 0L;
        if (type == Const.EventType_HTTP_Request) {
            conn.Request().RemoveCompressionMark();
        }
        final String requestBody = type == Const.EventType_HTTP_Request
                ? BodyCaptureHelper.readHttpRequestBody(conn) : null;
        final String responseBody = type == Const.EventType_HTTP_Response
                ? BodyCaptureHelper.readHttpResponseBody(conn) : null;
        final String requestHeaders = type == Const.EventType_HTTP_Request
                ? BodyCaptureHelper.readHttpRequestHeaders(conn) : null;
        final String responseHeaders = type == Const.EventType_HTTP_Response
                ? BodyCaptureHelper.readHttpResponseHeaders(conn) : null;
        final String requestProto = type == Const.EventType_HTTP_Request
                ? BodyCaptureHelper.readHttpRequestProto(conn) : null;
        final String responseProto = type == Const.EventType_HTTP_Response
                ? BodyCaptureHelper.readHttpResponseProto(conn) : null;
        final int requestBodyBytes = type == Const.EventType_HTTP_Request
                ? BodyCaptureHelper.readHttpRequestBodyBytes(conn) : -1;
        final int responseBodyBytes = type == Const.EventType_HTTP_Response
                ? BodyCaptureHelper.readHttpResponseBodyBytes(conn) : -1;
        CaptureEventQueue.offer(() ->
                HttpEventProcessor.process(type, theologyId, method, url, error, statusCode,
                        requestBody, responseBody, requestHeaders, responseHeaders,
                        requestProto, responseProto, requestBodyBytes, responseBodyBytes, packageName,
                        clientIp));
    }

    @Override
    public void onWebSocketCallback(WebSocketEvent conn) {
        if (!PacketCaptureGate.shouldRecordWebSocket()) {
            return;
        }
        final int type = conn.Type();
        final long theologyId = conn.TheologyID();
        final String url = conn.URL();
        final String method = conn.Method();
        final String packageName = conn.PackageName();
        final String wsBody = readBinaryBodyHex(conn.Body());
        CaptureEventQueue.offer(() ->
                StreamSessionProcessor.onWebSocket(type, theologyId, url, method, wsBody, packageName));
    }

    @Override
    public void onTCPCallback(TCPEvent conn) {
        if (!PacketCaptureGate.shouldRecordTcp()) {
            return;
        }
        final int type = conn.Type();
        final long theologyId = conn.TheologyID();
        final String remote = conn.RemoteAddr();
        final String local = conn.LocalAddr();
        final String packageName = conn.PackageName();
        final String tcpBody = readBinaryBodyHex(conn.Body());
        CaptureEventQueue.offer(() ->
                StreamSessionProcessor.onTcp(type, theologyId, local, remote, tcpBody, packageName));
    }

    @Override
    public void onUDPCallback(UDPEvent conn) {
        if (!PacketCaptureGate.shouldRecordUdp()) {
            return;
        }
        final int type = conn.Type();
        final long theologyId = conn.TheologyID();
        final String remote = conn.RemoteAddr();
        final String local = conn.LocalAddr();
        final String packageName = conn.PackageName();
        final String udpBody = readBinaryBodyHex(conn.Body());
        CaptureEventQueue.offer(() ->
                StreamSessionProcessor.onUdp(type, theologyId, local, remote, udpBody, packageName));
    }

    private static String readBinaryBodyHex(byte[] body) {
        if (body == null || body.length == 0) {
            return "（无数据）";
        }
        try {
            return CaptureRepository.truncate(tools.bytesToHex(body));
        } catch (Throwable t) {
            return "（读取数据失败: " + t.getMessage() + "）";
        }
    }

    @Override
    public void onScriptLogCallback(long sunnyNetContext, String logInfo) {
    }

    @Override
    public void onScriptCodeSaveCallback(long sunnyNetContext, String scriptCode) {
    }
}
