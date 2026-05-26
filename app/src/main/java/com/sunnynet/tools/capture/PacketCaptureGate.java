package com.sunnynet.tools.capture;

/**
 * 抓包回调开关（volatile），避免在 JNI 高频回调里调用 {@link CaptureEngine#get()}。
 */
public final class PacketCaptureGate {

    private static volatile boolean sessionActive;
    private static volatile boolean captureVisible = true;
    private static volatile boolean recordHttp = true;
    private static volatile boolean recordWebSocket = true;
    private static volatile boolean recordTcp = true;
    private static volatile boolean recordUdp = true;

    private PacketCaptureGate() {
    }

    public static void setSessionActive(boolean active) {
        sessionActive = active;
    }

    public static void setCaptureVisible(boolean visible) {
        captureVisible = visible;
    }

    public static void setProtocolFlags(boolean http, boolean webSocket, boolean tcp, boolean udp) {
        recordHttp = http;
        recordWebSocket = webSocket;
        recordTcp = tcp;
        recordUdp = udp;
    }

    public static boolean shouldRecord() {
        return sessionActive && captureVisible;
    }

    public static boolean shouldRecordHttp() {
        return shouldRecord() && recordHttp;
    }

    public static boolean shouldRecordWebSocket() {
        return shouldRecord() && recordWebSocket;
    }

    public static boolean shouldRecordTcp() {
        return shouldRecord() && recordTcp;
    }

    public static boolean shouldRecordUdp() {
        return shouldRecord() && recordUdp;
    }
}
