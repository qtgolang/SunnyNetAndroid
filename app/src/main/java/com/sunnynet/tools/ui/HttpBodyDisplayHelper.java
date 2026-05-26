package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.HttpBodyReadableFormatter;
import com.sunnynet.tools.capture.StreamPayloadFormatter;

import java.nio.charset.StandardCharsets;

/**
 * 绑定 HTTP 正文展示：**正文**模式优先格式化 JSON，其次解码为图像（内嵌 **`fitCenter` + `maxHeight`**，等比居中、高度不超过 {@code detail_body_image_max_height}），否则 UTF-8 字符串；Hex 为三栏 RecyclerView 虚拟化。
 * Hex 模式下显示「复制全部 Hex」「复制文本列」。
 */
public final class HttpBodyDisplayHelper {

    private HttpBodyDisplayHelper() {
    }

    @NonNull
    private static View resolveHexScrollHost(@NonNull View bodyRoot, @NonNull TextView bodyText) {
        View declared = bodyRoot.findViewById(R.id.detail_body_text_scroll);
        if (declared != null) {
            return declared;
        }
        if (bodyText.getParent() instanceof View) {
            return (View) bodyText.getParent();
        }
        return bodyRoot;
    }

    /**
     * 收起图片预览并回收 {@link Bitmap}，同时隐藏 {@code detail_body_image_container}，避免无图时仍占位或 Tab 切换残留。
     */
    private static void releaseDetailBodyImage(@Nullable View bodyRoot, @Nullable ImageView imageView) {
        if (bodyRoot != null) {
            View container = bodyRoot.findViewById(R.id.detail_body_image_container);
            if (container != null) {
                container.setVisibility(View.GONE);
            }
        }
        if (imageView == null) {
            return;
        }
        imageView.setOnClickListener(null);
        imageView.setClickable(false);
        imageView.setFocusable(false);
        imageView.setVisibility(View.GONE);
        imageView.setMaxWidth(Integer.MAX_VALUE);
        Drawable d = imageView.getDrawable();
        imageView.setImageDrawable(null);
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        }
    }

    /**
     * **正文**模式：JSON 美化 → 位图预览 → UTF-8 纯文本。
     */
    private static void bindReadableBody(@NonNull Context context,
                                         @NonNull View bodyRoot,
                                         @NonNull String storedBody,
                                         @NonNull TextView bodyText,
                                         @Nullable ImageView bodyImage,
                                         int onSurfaceFallback) {
        releaseDetailBodyImage(bodyRoot, bodyImage);

        byte[] bytes = StreamPayloadFormatter.resolvePayloadBytes(storedBody);
        if (bytes.length == 0) {
            bodyText.setVisibility(View.VISIBLE);
            bodyText.setText(context.getString(R.string.detail_empty));
            bodyText.setTypeface(Typeface.DEFAULT);
            bodyText.setBackgroundColor(Color.TRANSPARENT);
            bodyText.setTextColor(MaterialColors.getColor(bodyText,
                    com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));
            return;
        }

        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        String prettyJson = HttpBodyReadableFormatter.tryFormatJsonPretty(utf8);
        if (prettyJson != null) {
            bodyText.setVisibility(View.VISIBLE);
            bodyText.setText(prettyJson);
            bodyText.setTypeface(Typeface.MONOSPACE);
            bodyText.setBackgroundColor(Color.TRANSPARENT);
            bodyText.setTextColor(MaterialColors.getColor(bodyText,
                    com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));
            return;
        }

        Bitmap bmp = HttpBodyReadableFormatter.tryDecodeImageBitmap(bytes);
        if (bmp != null && bodyImage != null) {
            View container = bodyRoot.findViewById(R.id.detail_body_image_container);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
            bodyText.setVisibility(View.GONE);
            bodyImage.setVisibility(View.VISIBLE);
            bodyImage.setClickable(true);
            bodyImage.setFocusable(true);
            bodyImage.setAdjustViewBounds(true);
            bodyImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bodyImage.setImageBitmap(bmp);
            bodyImage.setContentDescription(context.getString(R.string.detail_body_image_content_desc));
            bodyImage.setOnClickListener(v -> DetailBodyImageFullscreenHelper.show(context, bytes));
            return;
        }
        if (bmp != null) {
            bmp.recycle();
        }

        bodyText.setVisibility(View.VISIBLE);
        bodyText.setText(utf8.isEmpty() ? context.getString(R.string.detail_empty) : utf8);
        bodyText.setTypeface(Typeface.DEFAULT);
        bodyText.setBackgroundColor(Color.TRANSPARENT);
        bodyText.setTextColor(MaterialColors.getColor(bodyText,
                com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));
    }

    private static void wireHexCopyButtons(@NonNull Context context,
                                           @NonNull View bodyRoot,
                                           @NonNull String stored) {
        MaterialButton copyHexAll = bodyRoot.findViewById(R.id.detail_body_copy_hex_all);
        MaterialButton copyAsciiCol = bodyRoot.findViewById(R.id.detail_body_copy_ascii_column);
        if (copyHexAll == null || copyAsciiCol == null) {
            return;
        }
        copyHexAll.setOnClickListener(v -> {
            byte[] b = StreamPayloadFormatter.resolvePayloadBytes(stored);
            if (b.length == 0) {
                return;
            }
            ClipboardUiHelper.copyPlain(context,
                    StreamPayloadFormatter.toSpacedHexFromBytes(b), "detail_http_hex_all");
        });
        copyAsciiCol.setOnClickListener(v -> {
            byte[] b = StreamPayloadFormatter.resolvePayloadBytes(stored);
            if (b.length == 0) {
                return;
            }
            ClipboardUiHelper.copyPlain(context,
                    HexDumpFormatter.toAsciiColumnPlain(b), "detail_http_ascii_column");
        });
    }

    private static void setHexAccessoryVisible(@Nullable View actionsScrollWrapper, boolean visible) {
        if (actionsScrollWrapper != null) {
            actionsScrollWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 退出 Hex 或未展开时隐藏列表并清空行缓冲，防止旧内容与选区残留在不可见区。 */
    private static void clearHexRecyclerRows(@Nullable HexDumpRecyclerView hexRecycler) {
        if (hexRecycler == null) {
            return;
        }
        hexRecycler.setVisibility(View.GONE);
        resetDetailHexRecyclerLayoutHeight(hexRecycler);
        RecyclerView.Adapter<?> ad = hexRecycler.getAdapter();
        if (ad instanceof HexDumpLineAdapter) {
            ((HexDumpLineAdapter) ad).clearRows();
        }
    }

    /** 恢复 XML 定义的默认高度，避免下次 Hex 沿用上次夹紧的过矮高度。 */
    private static void resetDetailHexRecyclerLayoutHeight(@NonNull HexDumpRecyclerView hexRecycler) {
        hexRecycler.resetCrossRowSelectionStateQuietly();
        ViewGroup.LayoutParams lp = hexRecycler.getLayoutParams();
        if (lp != null) {
            lp.height = hexRecycler.getResources()
                    .getDimensionPixelSize(R.dimen.detail_hex_dump_recycler_height);
            hexRecycler.setLayoutParams(lp);
        }
    }

    @Nullable
    private static HexDumpLineAdapter ensureDetailHexAdapter(@NonNull Context context,
                                                              @NonNull HexDumpRecyclerView hexRecycler) {
        Object tag = hexRecycler.getTag(R.id.tag_hex_dump_line_adapter);
        if (tag instanceof HexDumpLineAdapter) {
            return (HexDumpLineAdapter) tag;
        }
        HexDumpLineAdapter adapter = new HexDumpLineAdapter(context, R.layout.item_hex_dump_line, hexRecycler);
        hexRecycler.setLayoutManager(new LinearLayoutManager(context));
        hexRecycler.setAdapter(adapter);
        hexRecycler.setTag(R.id.tag_hex_dump_line_adapter, adapter);
        return adapter;
    }

    /**
     * @param rawBody Body 原始字节语义下的字符串快照（与存档一致）；空时正文显示「（无内容）」占位。
     */
    public static void bind(@NonNull Context context, @NonNull View bodyRoot,
                            @NonNull String rawBody) {
        TextView bodyText = bodyRoot.findViewById(R.id.detail_body_text);
        ImageView bodyImage = bodyRoot.findViewById(R.id.detail_body_image);
        MaterialButtonToggleGroup toggle = bodyRoot.findViewById(R.id.detail_body_mode_toggle);
        View actionsScrollWrapper = bodyRoot.findViewById(R.id.detail_body_hex_actions_scroll);
        HexDumpRecyclerView hexRecycler = bodyRoot.findViewById(R.id.detail_body_hex_recycler);

        if (bodyText == null || toggle == null) {
            return;
        }
        final View hexHost = resolveHexScrollHost(bodyRoot, bodyText);
        final String stored = rawBody;
        int onSurfaceFallback = ContextCompat.getColor(context, R.color.sunny_on_surface);

        wireHexCopyButtons(context, bodyRoot, stored);

        Runnable applySizedHexDump = () -> {
            if (toggle.getCheckedButtonId() != R.id.detail_body_mode_hex) {
                return;
            }
            byte[] bytes = StreamPayloadFormatter.resolvePayloadBytes(stored);
            releaseDetailBodyImage(bodyRoot, bodyImage);
            if (bytes.length == 0) {
                clearHexRecyclerRows(hexRecycler);
                bodyText.setVisibility(View.VISIBLE);
                bodyText.setText(context.getString(R.string.detail_empty));
                bodyText.setTypeface(Typeface.MONOSPACE);
                bodyText.setBackgroundColor(Color.TRANSPARENT);
                int fgEmpty = MaterialColors.getColor(bodyText,
                        com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback);
                bodyText.setTextColor(fgEmpty);
                setHexAccessoryVisible(actionsScrollWrapper, false);
                return;
            }
            if (hexRecycler != null) {
                bodyText.setVisibility(View.GONE);
                hexRecycler.setVisibility(View.VISIBLE);
                HexDumpLineAdapter lineAdapter = ensureDetailHexAdapter(context, hexRecycler);
                View probeRow = LayoutInflater.from(context).inflate(R.layout.item_hex_dump_line,
                        hexRecycler, false);
                HexDumpRowLayoutHelper.prepareTripletRowTypography(probeRow);
                TextView probeBytes = probeRow.findViewById(R.id.hex_line_bytes);
                HexDumpViewportHelper.scheduleTripletLayoutWhenHostMeasured(hexRecycler, hexRecycler, hexHost,
                        probeBytes, () -> toggle.getCheckedButtonId() == R.id.detail_body_mode_hex, () -> {
                            int hostInner = HexDumpViewportHelper.resolveTripletHostInnerWidthPx(hexRecycler,
                                    hexHost, probeBytes);
                            int cols = HexDumpViewportHelper.computeBytesPerRowTripletBounded(probeRow,
                                    hostInner);
                            int[] ww = HexDumpViewportHelper.computeTripletWidthsFromInflatedRow(probeRow, cols);
                            lineAdapter.setPayloadAndLayout(bytes, cols, ww[0], ww[1], ww[2]);
                            int maxH = hexRecycler.getResources()
                                    .getDimensionPixelSize(R.dimen.detail_hex_dump_recycler_height);
                            int minH = hexRecycler.getResources()
                                    .getDimensionPixelSize(R.dimen.detail_hex_dump_recycler_min_px);
                            HexDumpViewportHelper.scheduleClampHexRecyclerHeight(hexRecycler, hexHost,
                                    probeRow, cols, bytes.length, ww[0], ww[1], ww[2],
                                    maxH, minH, null, 0);
                        });
            } else {
                bodyText.setVisibility(View.VISIBLE);
                HexDumpViewportHelper.formatIntoTextView(hexHost, bodyText, context, bytes,
                        () -> toggle.getCheckedButtonId() == R.id.detail_body_mode_hex);
                bodyText.setTextColor(MaterialColors.getColor(bodyText,
                        com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));
            }
            setHexAccessoryVisible(actionsScrollWrapper, true);
        };

        Runnable apply = () -> {
            View reflowHost = hexRecycler != null ? hexRecycler : hexHost;
            HexDumpViewportHelper.clearHexReflow(reflowHost);
            HexDumpViewportHelper.clearHexReflow(hexHost);
            boolean hex = toggle.getCheckedButtonId() == R.id.detail_body_mode_hex;
            if (!hex) {
                clearHexRecyclerRows(hexRecycler);
                setHexAccessoryVisible(actionsScrollWrapper, false);
                bindReadableBody(context, bodyRoot, stored, bodyText, bodyImage, onSurfaceFallback);
                return;
            }

            releaseDetailBodyImage(bodyRoot, bodyImage);
            byte[] bytes = StreamPayloadFormatter.resolvePayloadBytes(stored);
            bodyText.setTypeface(Typeface.MONOSPACE);
            bodyText.setBackgroundColor(Color.TRANSPARENT);
            bodyText.setTextColor(MaterialColors.getColor(bodyText,
                    com.google.android.material.R.attr.colorOnSurface, onSurfaceFallback));

            if (bytes.length == 0) {
                clearHexRecyclerRows(hexRecycler);
                bodyText.setVisibility(View.VISIBLE);
                bodyText.setText(context.getString(R.string.detail_empty));
                setHexAccessoryVisible(actionsScrollWrapper, false);
                return;
            }

            bodyText.setText("");
            setHexAccessoryVisible(actionsScrollWrapper, true);
            HexDumpViewportHelper.attachHexReflow(reflowHost, bodyText,
                    () -> toggle.getCheckedButtonId() == R.id.detail_body_mode_hex,
                    applySizedHexDump);
        };
        toggle.check(R.id.detail_body_mode_string);
        apply.run();
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            apply.run();
        });
    }
}
