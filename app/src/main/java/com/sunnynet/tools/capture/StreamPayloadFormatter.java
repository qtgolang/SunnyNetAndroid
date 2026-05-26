package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 流列表正文展示：原始存储多为连续十六进制（SDK {@code bytesToHex}），解析为字节后格式化为「32 0A …」或按 UTF-8 解码为字符串。
 */
public final class StreamPayloadFormatter {

    private StreamPayloadFormatter() {
    }

    /**
     * 列表卡片单行预览：先按模式格式化，再空白压平并限制长度。
     *
     * @param maxChars 最大字符数（十六进制含空格时每字符较短，可略大）
     */
    @NonNull
    public static String previewLine(@NonNull String storedBody, boolean asHex, int maxChars) {
        String formatted = asHex ? toSpacedHex(storedBody) : toUtf8String(storedBody);
        String line = formatted.replace('\n', ' ').replace('\r', ' ').trim();
        if (line.isEmpty()) {
            return "（空）";
        }
        if (line.length() <= maxChars) {
            return line;
        }
        return line.substring(0, maxChars) + "…";
    }

    /**
     * 与正文 Hex/字符串展示一致的长度：能解码为连续十六进制则为解码后字节数；否则视为 UTF-8 原文字节数；
     * 占位或空正文为 0。
     *
     * @param storedBody 入库中的原始载荷串（常为 SDK 下发的连续 Hex）
     * @return 负载语义字节长度，非负数
     */
    public static int logicalPayloadByteCount(@NonNull String storedBody) {
        return resolvePayloadBytes(storedBody).length;
    }

    /**
     * 解析与 {@link #toUtf8String} / {@link #toSpacedHex} 一致的字节序列：占位串返回空数组；
     * 连续 Hex 解码；否则 UTF-8 源码串。
     */
    @NonNull
    public static byte[] resolvePayloadBytes(@NonNull String storedBody) {
        if (storedBody.isEmpty()) {
            return new byte[0];
        }
        if (isPlaceholder(storedBody)) {
            return new byte[0];
        }
        byte[] raw = tryDecodeHexString(storedBody);
        if (raw != null) {
            return raw;
        }
        return storedBody.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将已解析的字节序列格式化为空格分隔大写 Hex，与 {@link #toSpacedHex(String)} 语义一致。
     */
    @NonNull
    public static String toSpacedHexFromBytes(@NonNull byte[] raw) {
        return bytesToSpacedHexUpper(raw);
    }

    /**
     * 空格分隔的大写十六进制，例如 {@code 32 32 0A 0B 00 01}。
     */
    @NonNull
    public static String toSpacedHex(@NonNull String storedBody) {
        if (storedBody.isEmpty()) {
            return "";
        }
        if (isPlaceholder(storedBody)) {
            return bytesToSpacedHexUpper(storedBody.getBytes(StandardCharsets.UTF_8));
        }
        byte[] raw = tryDecodeHexString(storedBody);
        if (raw != null) {
            return bytesToSpacedHexUpper(raw);
        }
        return bytesToSpacedHexUpper(storedBody.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将连续十六进制还原为字节后按 UTF-8 解码；非十六进制则原样返回。
     */
    @NonNull
    public static String toUtf8String(@NonNull String storedBody) {
        if (storedBody.isEmpty()) {
            return "";
        }
        if (isPlaceholder(storedBody)) {
            return storedBody;
        }
        byte[] raw = tryDecodeHexString(storedBody);
        if (raw != null) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        return storedBody;
    }

    private static boolean isPlaceholder(@NonNull String s) {
        return "（无数据）".equals(s) || s.startsWith("（读取数据失败");
    }

    /**
     * 仅当去掉空白后为偶数长度且字符均在 [0-9A-Fa-f] 时视为十六进制负载。
     */
    /** 剔除 {@link CaptureRepository#TRUNCATE_MARKER}，使被截断的 Hex 仍可解码前半段（含二进制图片）。 */
    @NonNull
    private static String stripTruncateMarkerForHexDecode(@NonNull String s) {
        int i = s.indexOf(CaptureRepository.TRUNCATE_MARKER);
        return i >= 0 ? s.substring(0, i) : s;
    }

    @Nullable
    private static byte[] tryDecodeHexString(@NonNull String s) {
        s = stripTruncateMarkerForHexDecode(s);
        String trimmed = s.trim().replaceAll("\\s+", "");
        if (trimmed.isEmpty() || (trimmed.length() & 1) == 1) {
            return null;
        }
        int n = trimmed.length();
        for (int i = 0; i < n; i++) {
            char c = trimmed.charAt(i);
            if (!isHexChar(c)) {
                return null;
            }
        }
        byte[] out = new byte[n / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(trimmed.charAt(i * 2), 16);
            int lo = Character.digit(trimmed.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    @NonNull
    private static String bytesToSpacedHexUpper(@NonNull byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}
