package com.sunnynet.tools.capture;

import com.SunnyNet.Internal.Const;

/**
 * 将 WS / TCP / UDP 按 theologyId 聚合为单条会话记录，收发消息写入流列表。
 * TCP/UDP 在收到首条收发数据前不加入抓包列表。
 */
final class StreamSessionProcessor {

    private static final int MAX_STREAMS_PER_SESSION = 300;

    private StreamSessionProcessor() {
    }

    static void onWebSocket(int type, long theologyId, String url, String method, String body,
                            String packageName) {
        if (type == Const.EventType_Websocket_OK) {
            CaptureRepository repo = CaptureRepository.get();
            CaptureRecord upgraded = repo.upgradeHttpToWebSocket(theologyId, url, method, packageName);
            if (upgraded != null) {
                if (isDataEvent(CaptureRecord.TYPE_WEBSOCKET, type)) {
                    upgraded.addStreamEntry(dataDirection(CaptureRecord.TYPE_WEBSOCKET, type),
                            body, MAX_STREAMS_PER_SESSION);
                } else {
                    upgraded.refreshStreamSummary();
                }
                repo.notifyStreamSessionChanged(upgraded, false);
                return;
            }
        }
        recordWebSocket(CaptureRecord.TYPE_WEBSOCKET, theologyId, type, url, null, null, method, body,
                packageName);
    }

    static void onTcp(int type, long theologyId, String local, String remote, String body,
                      String packageName) {
        recordTcpUdp(CaptureRecord.TYPE_TCP, theologyId, type, remote, local, remote, body, packageName);
    }

    static void onUdp(int type, long theologyId, String local, String remote, String body,
                      String packageName) {
        recordTcpUdp(CaptureRecord.TYPE_UDP, theologyId, type, remote, local, remote, body, packageName);
    }

    private static void recordWebSocket(String protocol, long theologyId, int eventType,
                                        String titleEndpoint, String local, String remote,
                                        String method, String body, String packageName) {
        CaptureRepository repo = CaptureRepository.get();
        CaptureRecord session = repo.findByTheologyId(theologyId);
        boolean added = false;
        if (session == null) {
            session = repo.createStreamSession(protocol, theologyId, buildTitle(protocol, titleEndpoint),
                    buildInitialDetail(protocol, titleEndpoint, local, remote, method), packageName);
            added = true;
        } else {
            session.setPackageName(packageName);
        }
        applyLifecycle(session, protocol, eventType, titleEndpoint, local, remote, method);
        if (isDataEvent(protocol, eventType)) {
            session.addStreamEntry(dataDirection(protocol, eventType), body, MAX_STREAMS_PER_SESSION);
        } else {
            session.refreshStreamSummary();
        }
        repo.notifyStreamSessionChanged(session, added);
    }

    private static void recordTcpUdp(String protocol, long theologyId, int eventType,
                                     String titleEndpoint, String local, String remote,
                                     String body, String packageName) {
        CaptureRepository repo = CaptureRepository.get();
        boolean dataEvent = isDataEvent(protocol, eventType);
        boolean closeEvent = isCloseEvent(protocol, eventType);

        CaptureRecord session = repo.findTcpUdpSessionIncludingPending(theologyId);
        boolean added = false;

        if (session == null) {
            if (!dataEvent) {
                session = repo.createPendingTcpUdpSession(protocol, theologyId,
                        buildTitle(protocol, titleEndpoint),
                        buildInitialDetail(protocol, titleEndpoint, local, remote, null), packageName);
                applyLifecycle(session, protocol, eventType, titleEndpoint, local, remote, null);
                if (closeEvent) {
                    repo.removePendingTcpUdpSession(theologyId);
                }
                return;
            }
            session = repo.createStreamSession(protocol, theologyId, buildTitle(protocol, titleEndpoint),
                    buildInitialDetail(protocol, titleEndpoint, local, remote, null), packageName);
            added = true;
        } else if (repo.isPendingTcpUdpSession(theologyId)) {
            applyLifecycle(session, protocol, eventType, titleEndpoint, local, remote, null);
            session.setPackageName(packageName);
            if (closeEvent) {
                repo.removePendingTcpUdpSession(theologyId);
                return;
            }
            if (!dataEvent) {
                return;
            }
            session = repo.promotePendingTcpUdpSession(theologyId);
            if (session == null) {
                return;
            }
            added = true;
        } else {
            session.setPackageName(packageName);
            applyLifecycle(session, protocol, eventType, titleEndpoint, local, remote, null);
        }

        if (dataEvent) {
            session.addStreamEntry(dataDirection(protocol, eventType), body, MAX_STREAMS_PER_SESSION);
        } else {
            session.refreshStreamSummary();
        }
        repo.notifyStreamSessionChanged(session, added);
    }

    private static boolean isCloseEvent(String protocol, int eventType) {
        if (CaptureRecord.TYPE_TCP.equals(protocol)) {
            return eventType == Const.EventType_TCP_Close;
        }
        if (CaptureRecord.TYPE_UDP.equals(protocol)) {
            return eventType == Const.EventType_UDP_Closed;
        }
        return false;
    }

    private static void applyLifecycle(CaptureRecord session, String protocol, int eventType,
                                     String titleEndpoint, String local, String remote, String method) {
        switch (protocol) {
            case CaptureRecord.TYPE_WEBSOCKET:
                if (eventType == Const.EventType_Websocket_OK) {
                    if (titleEndpoint != null && !titleEndpoint.isEmpty()) {
                        session.setTitle(titleEndpoint);
                    }
                    patchDetail(session, titleEndpoint, null, null, method, CaptureRecord.STATE_CONNECTED);
                } else if (eventType == Const.EventType_Websocket_Close) {
                    patchDetail(session, null, null, null, method, CaptureRecord.STATE_DISCONNECTED);
                }
                break;
            case CaptureRecord.TYPE_TCP:
                if (eventType == Const.EventType_TCP_About) {
                    session.setConnectionState(CaptureRecord.STATE_CONNECTING);
                    patchDetail(session, titleEndpoint, local, remote, null, CaptureRecord.STATE_CONNECTING);
                } else if (eventType == Const.EventType_TCP_OK) {
                    session.setConnectionState(CaptureRecord.STATE_CONNECTED);
                    session.setTitle(titleEndpoint != null ? titleEndpoint : session.getTitle());
                    patchDetail(session, titleEndpoint, local, remote, null, CaptureRecord.STATE_CONNECTED);
                } else if (eventType == Const.EventType_TCP_Close) {
                    session.setConnectionState(CaptureRecord.STATE_DISCONNECTED);
                    patchDetail(session, titleEndpoint, local, remote, null, CaptureRecord.STATE_DISCONNECTED);
                }
                break;
            case CaptureRecord.TYPE_UDP:
                if (eventType == Const.EventType_UDP_Closed) {
                    session.setConnectionState(CaptureRecord.STATE_DISCONNECTED);
                    patchDetail(session, titleEndpoint, local, remote, null, CaptureRecord.STATE_DISCONNECTED);
                } else if (session.getConnectionState() == CaptureRecord.STATE_UNKNOWN) {
                    session.setConnectionState(CaptureRecord.STATE_CONNECTED);
                    session.setTitle(titleEndpoint != null ? titleEndpoint : session.getTitle());
                    patchDetail(session, titleEndpoint, local, remote, null, CaptureRecord.STATE_CONNECTED);
                }
                break;
            default:
                break;
        }
    }

    private static boolean isDataEvent(String protocol, int eventType) {
        if (CaptureRecord.TYPE_WEBSOCKET.equals(protocol)) {
            return eventType == Const.EventType_Websocket_Send
                    || eventType == Const.EventType_Websocket_Receive;
        }
        if (CaptureRecord.TYPE_TCP.equals(protocol)) {
            return eventType == Const.EventType_TCP_Send
                    || eventType == Const.EventType_TCP_Receive;
        }
        if (CaptureRecord.TYPE_UDP.equals(protocol)) {
            return eventType == Const.EventType_UDP_Send
                    || eventType == Const.EventType_UDP_Receive;
        }
        return false;
    }

    private static String dataDirection(String protocol, int eventType) {
        if (CaptureRecord.TYPE_WEBSOCKET.equals(protocol)) {
            return eventType == Const.EventType_Websocket_Send ? "发送" : "接收";
        }
        if (CaptureRecord.TYPE_TCP.equals(protocol)) {
            return eventType == Const.EventType_TCP_Send ? "发送" : "接收";
        }
        if (CaptureRecord.TYPE_UDP.equals(protocol)) {
            return eventType == Const.EventType_UDP_Send ? "发送" : "接收";
        }
        return "数据";
    }

    private static String buildTitle(String protocol, String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return protocol;
        }
        return endpoint;
    }

    private static String buildInitialDetail(String protocol, String endpoint,
                                             String local, String remote, String method) {
        StringBuilder sb = new StringBuilder();
        sb.append("协议: ").append(protocol).append('\n');
        if (CaptureRecord.TYPE_WEBSOCKET.equals(protocol)) {
            sb.append("URL: ").append(endpoint != null ? endpoint : "").append('\n');
            if (method != null && !method.isEmpty()) {
                sb.append("Method: ").append(method).append('\n');
            }
        } else {
            if (local != null && remote != null) {
                sb.append("地址: ").append(local).append(" -> ").append(remote).append('\n');
            } else if (endpoint != null) {
                sb.append("远程: ").append(endpoint).append('\n');
            }
        }
        sb.append("状态: ").append(CaptureRecord.stateLabel(CaptureRecord.STATE_UNKNOWN));
        return sb.toString();
    }

    private static void patchDetail(CaptureRecord session, String endpoint, String local, String remote,
                                    String method, String state) {
        String detail = session.getDetail();
        if (detail == null) {
            detail = "";
        }
        if (endpoint != null && !endpoint.isEmpty() && CaptureRecord.TYPE_WEBSOCKET.equals(session.getProtocol())) {
            detail = upsertLine(detail, "URL: ", endpoint);
        }
        if (local != null && remote != null) {
            detail = upsertLine(detail, "地址: ", local + " -> " + remote);
        }
        if (method != null && !method.isEmpty()) {
            detail = upsertLine(detail, "Method: ", method);
        }
        detail = upsertLine(detail, "状态: ", CaptureRecord.stateLabel(state));
        session.setDetail(detail);
        session.setConnectionState(state);
        session.refreshStreamSummary();
    }

    /** 停止抓包时将会话标记为已断开（更新详情与摘要）。 */
    static void disconnectSession(CaptureRecord session) {
        patchDetail(session, null, null, null, null, CaptureRecord.STATE_DISCONNECTED);
    }

    private static String upsertLine(String detail, String prefix, String value) {
        String[] lines = detail.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                if (!replaced) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(prefix).append(value);
                    replaced = true;
                }
            } else {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        if (!replaced) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(prefix).append(value);
        }
        return out.toString();
    }
}