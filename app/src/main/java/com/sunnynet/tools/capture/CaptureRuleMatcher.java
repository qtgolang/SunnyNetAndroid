package com.sunnynet.tools.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 规则匹配与数据编解码，对齐 V5 {@code interceptMatchBytes} / {@code decodeRuleDataByType}。
 */
public final class CaptureRuleMatcher {

    private CaptureRuleMatcher() {
    }

    public static boolean matchBytes(@Nullable byte[] source, @NonNull String matchType, @NonNull String matchData) {
        if (source == null || source.length == 0) {
            return false;
        }
        String md = matchData.trim();
        if (md.isEmpty()) {
            return false;
        }
        String mt = matchType.toLowerCase(Locale.ROOT).trim();
        switch (mt) {
            case CaptureRuleConstants.MATCH_REGEX:
                try {
                    return Pattern.compile(md).matcher(new String(source, StandardCharsets.UTF_8)).find();
                } catch (PatternSyntaxException e) {
                    return false;
                }
            case CaptureRuleConstants.MATCH_HEX:
            case CaptureRuleConstants.MATCH_BASE64: {
                byte[] needle = decodeRuleData(mt, md);
                if (needle == null || needle.length == 0) {
                    return false;
                }
                return indexOf(source, needle) >= 0;
            }
            case CaptureRuleConstants.MATCH_STRING:
            default:
                return new String(source, StandardCharsets.UTF_8).contains(md);
        }
    }

    public static boolean matchText(@Nullable String source, @NonNull String matchType, @NonNull String matchData) {
        if (source == null) {
            return false;
        }
        return matchBytes(source.getBytes(StandardCharsets.UTF_8), matchType, matchData);
    }

    @Nullable
    public static byte[] decodeRuleData(@NonNull String type, @NonNull String raw) {
        String t = type.toLowerCase(Locale.ROOT).trim();
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        switch (t) {
            case CaptureRuleConstants.MATCH_HEX:
                return hexToBytes(text);
            case CaptureRuleConstants.MATCH_BASE64:
                try {
                    return java.util.Base64.getDecoder().decode(text);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            case CaptureRuleConstants.MATCH_REGEX:
            case CaptureRuleConstants.MATCH_STRING:
            default:
                return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Nullable
    public static byte[] replaceBytes(@NonNull byte[] source, @NonNull CaptureRule rule) {
        String oldType = rule.getOldType();
        if (CaptureRuleConstants.MATCH_REGEX.equalsIgnoreCase(oldType)) {
            try {
                Pattern pattern = Pattern.compile(rule.getOldData().trim());
                byte[] replacement = decodeRuleData(rule.getNewType(), rule.getNewData());
                if (replacement == null) {
                    return null;
                }
                String replaced = pattern.matcher(new String(source, StandardCharsets.UTF_8))
                        .replaceAll(new String(replacement, StandardCharsets.UTF_8));
                return replaced.getBytes(StandardCharsets.UTF_8);
            } catch (PatternSyntaxException e) {
                return null;
            }
        }
        byte[] oldBytes = decodeRuleData(oldType, rule.getOldData());
        byte[] newBytes = decodeRuleData(rule.getNewType(), rule.getNewData());
        if (oldBytes == null || newBytes == null || oldBytes.length == 0) {
            return null;
        }
        return replaceAll(source, oldBytes, newBytes);
    }

    @NonNull
    public static String normalizeUrlWithoutQuery(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        int q = url.indexOf('?');
        if (q >= 0) {
            url = url.substring(0, q);
        }
        return url.trim();
    }

    public static boolean rewriteMethodMatched(@NonNull String ruleMethod, @Nullable String reqMethod) {
        String rm = ruleMethod.trim().toUpperCase(Locale.ROOT);
        if (rm.isEmpty() || CaptureRuleConstants.REWRITE_METHOD_ALL.equals(rm)) {
            return true;
        }
        return rm.equals(reqMethod != null ? reqMethod.trim().toUpperCase(Locale.ROOT) : "");
    }

    @Nullable
    private static byte[] hexToBytes(@NonNull String hex) {
        String compact = hex.replace(" ", "").replace("\n", "");
        if (compact.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(compact.charAt(i * 2), 16);
            int lo = Character.digit(compact.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static int indexOf(@NonNull byte[] haystack, @NonNull byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @NonNull
    private static byte[] replaceAll(@NonNull byte[] source, @NonNull byte[] oldBytes, @NonNull byte[] newBytes) {
        int idx = indexOf(source, oldBytes);
        if (idx < 0) {
            return source;
        }
        byte[] out = new byte[source.length - oldBytes.length + newBytes.length];
        System.arraycopy(source, 0, out, 0, idx);
        System.arraycopy(newBytes, 0, out, idx, newBytes.length);
        System.arraycopy(source, idx + oldBytes.length, out, idx + newBytes.length,
                source.length - idx - oldBytes.length);
        return out;
    }
}
