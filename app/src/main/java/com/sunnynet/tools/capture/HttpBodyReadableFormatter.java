package com.sunnynet.tools.capture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * HTTP 详情「正文」模式：优先将 UTF-8 文本格式化为 JSON；否则尝试按位图解码；均失败则按纯文本展示。
 */
public final class HttpBodyReadableFormatter {

    /** 列表/详情缩略预览：解码时限定的最大边长（像素），减轻大图 OOM。 */
    private static final int DECODE_MAX_SIDE_PX = 2048;

    private HttpBodyReadableFormatter() {
    }

    /**
     * 若为合法 JSON 对象或数组则返回缩进为 2 的字符串；否则返回 null。
     *
     * @param utf8Text 与 {@link StreamPayloadFormatter#resolvePayloadBytes} 解码一致的 UTF-8 文本
     */
    @Nullable
    public static String tryFormatJsonPretty(@NonNull String utf8Text) {
        String t = utf8Text.trim();
        if (t.startsWith("\uFEFF")) {
            t = t.substring(1).trim();
        }
        if (t.isEmpty()) {
            return null;
        }
        char c = t.charAt(0);
        if (c != '{' && c != '[') {
            return null;
        }
        try {
            JSONTokener tok = new JSONTokener(t);
            Object v = tok.nextValue();
            if (tok.more()) {
                return null;
            }
            if (v instanceof JSONObject) {
                return ((JSONObject) v).toString(2);
            }
            if (v instanceof JSONArray) {
                return ((JSONArray) v).toString(2);
            }
            return null;
        } catch (JSONException ignored) {
            return null;
        }
    }

    /**
     * 尝试将字节解码为可展示的位图；无法识别为图片或解码失败时返回 null。
     * 长边采样至 {@link #DECODE_MAX_SIDE_PX}，用于列表/详情内嵌预览。
     */
    @Nullable
    public static Bitmap tryDecodeImageBitmap(@NonNull byte[] data) {
        return tryDecodeImageBitmap(data, DECODE_MAX_SIDE_PX);
    }

    /**
     * 尝试将字节解码为位图。
     *
     * @param data       图像原始字节（与入库 Hex 解压后一致）
     * @param maxSidePx  长边目标上限（像素），用于全屏预览等场景适当放大解码；过小则钳位为 1
     */
    @Nullable
    public static Bitmap tryDecodeImageBitmap(@NonNull byte[] data, int maxSidePx) {
        if (data.length < 9) {
            return null;
        }
        int ceiling = Math.max(1, maxSidePx);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, ceiling);
        decode.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, decode);
        } catch (OutOfMemoryError ignored) {
            return null;
        }
    }

    private static int computeInSampleSize(int width, int height, int reqMaxSide) {
        int maxDim = Math.max(width, height);
        if (maxDim <= reqMaxSide) {
            return 1;
        }
        int inSample = 1;
        while (maxDim / (inSample * 2) >= reqMaxSide) {
            inSample *= 2;
        }
        return inSample;
    }
}
