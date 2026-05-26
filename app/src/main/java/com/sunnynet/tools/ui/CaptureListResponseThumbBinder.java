package com.sunnynet.tools.ui;

import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.capture.CaptureRecord;
import com.sunnynet.tools.capture.HttpBodyReadableFormatter;
import com.sunnynet.tools.capture.HttpDetailSections;
import com.sunnynet.tools.capture.StreamPayloadFormatter;

/**
 * 抓包主列表 HTTP 卡片：响应正文可解码为位图时，在标题行右侧固定正方形区域内展示缩略图。
 * 使用 {@link ImageView.ScaleType#CENTER_INSIDE} 将整图等比缩放入框（可留边），避免 {@link ImageView.ScaleType#CENTER_CROP} 纵向裁切被误认为「只显示半张图」；点击缩略可全屏查看原图。
 */
public final class CaptureListResponseThumbBinder {

    private static final float THUMB_CORNER_DP = 8f;

    /** 解码采样长边上界（像素），供列表小格 {@link ImageView.ScaleType#CENTER_CROP} 使用。 */
    private static final int THUMB_DECODE_MAX_SIDE_PX = 512;

    /** 响应体过大时不解码预览，避免阻塞 UI / OOM。 */
    private static final long MAX_RESPONSE_BYTES_FOR_THUMB = 5 * 1024 * 1024L;

    private CaptureListResponseThumbBinder() {
    }

    /**
     * 绑定缩略区：成功时展示 {@code thumbFrame}，由 {@link ImageView.ScaleType#CENTER_INSIDE} 将整图等比缩放入与 {@code capture_list_thumb_size} 一致的外框；点击外框全屏预览（原始字节解码）。
     */
    public static void bind(@NonNull View thumbFrame, @NonNull ImageView imageView,
                            @NonNull CaptureRecord record) {
        recycleImageDrawable(imageView);
        if (!shouldAttemptDecode(record)) {
            hideThumb(thumbFrame, imageView);
            return;
        }
        String stored = HttpDetailSections.buildResponseBodyFromRecord(record);
        if (stored.isEmpty()) {
            hideThumb(thumbFrame, imageView);
            return;
        }
        long rb = record.getResponseBodyBytes();
        if (rb >= 0 && rb > MAX_RESPONSE_BYTES_FOR_THUMB) {
            hideThumb(thumbFrame, imageView);
            return;
        }
        byte[] raw = StreamPayloadFormatter.resolvePayloadBytes(stored);
        if (raw.length == 0 || raw.length > MAX_RESPONSE_BYTES_FOR_THUMB) {
            hideThumb(thumbFrame, imageView);
            return;
        }
        Bitmap decoded = HttpBodyReadableFormatter.tryDecodeImageBitmap(raw, THUMB_DECODE_MAX_SIDE_PX);
        if (decoded == null) {
            hideThumb(thumbFrame, imageView);
            return;
        }

        imageView.setAdjustViewBounds(false);
        imageView.setPadding(0, 0, 0, 0);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageBitmap(decoded);
        imageView.setVisibility(View.VISIBLE);
        thumbFrame.setVisibility(View.VISIBLE);
        thumbFrame.setClickable(true);
        thumbFrame.setOnClickListener(v -> DetailBodyImageFullscreenHelper.show(v.getContext(), raw));
        thumbFrame.post(() -> applyRoundRectOutline(thumbFrame));
    }

    /** ViewHolder 回收：回收 Bitmap 并收起外框，避免滑动时积压。 */
    public static void release(@Nullable View thumbFrame, @Nullable ImageView imageView) {
        if (imageView != null) {
            recycleImageDrawable(imageView);
        }
        hideThumb(thumbFrame, imageView);
    }

    private static boolean shouldAttemptDecode(@NonNull CaptureRecord record) {
        if (!CaptureRecord.TYPE_HTTP.equals(record.getProtocol()) || record.isStreamSession()) {
            return false;
        }
        return record.getHttpStatusCode() > 0;
    }

    private static void hideThumb(@Nullable View thumbFrame, @Nullable ImageView imageView) {
        if (thumbFrame != null) {
            thumbFrame.setOnClickListener(null);
            thumbFrame.setClickable(false);
            thumbFrame.setVisibility(View.GONE);
        }
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
        }
    }

    private static void applyRoundRectOutline(@NonNull View frame) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THUMB_CORNER_DP,
                frame.getResources().getDisplayMetrics());
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    private static void recycleImageDrawable(@NonNull ImageView imageView) {
        Drawable prev = imageView.getDrawable();
        imageView.setImageDrawable(null);
        if (prev instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) prev).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }
}
