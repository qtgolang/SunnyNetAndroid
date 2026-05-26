package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.R;

import java.util.Arrays;

/**
 * Hex 检视：正文区宽度就绪后重算每行字节（优先 {@link #computeBytesPerRowTripletBounded} 与实测三栏宽一致），
 * 与单行 item inflate 结果算三栏像素宽（{@link #computeTripletWidthsFromInflatedRow}）。
 * {@link #attachHexReflow} 在宽度变化时防抖刷新并正确摘除监听，避免泄漏。
 */
public final class HexDumpViewportHelper {

    private HexDumpViewportHelper() {
    }

    /**
     * 是否仍应保持 Hex 检视（通常为当前 Tab / 对话框处于十六进制模式）。
     */
    public interface HexModeGate {
        boolean shouldStillShowHexDump();
    }

    /** 挂载在 View 标签上：{@link View#removeCallbacks} / 摘除监听均需用到。 */
    private static final class HexReflowToken {
        @NonNull
        final View.OnLayoutChangeListener listener;
        @NonNull
        final Runnable deferredApply;

        HexReflowToken(@NonNull View.OnLayoutChangeListener listener,
                       @NonNull Runnable deferredApply) {
            this.listener = listener;
            this.deferredApply = deferredApply;
        }
    }

    /**
     * 移除此前挂载在 {@code host} 上的布局监听与待执行的 {@link Runnable}。
     */
    public static void clearHexReflow(@Nullable View host) {
        if (host == null) {
            return;
        }
        Object tag = host.getTag(R.id.tag_hex_dump_layout_listener);
        if (tag instanceof HexReflowToken) {
            HexReflowToken token = (HexReflowToken) tag;
            host.removeOnLayoutChangeListener(token.listener);
            host.removeCallbacks(token.deferredApply);
        }
        host.setTag(R.id.tag_hex_dump_layout_listener, null);
    }

    /**
     * 挂载宽度感知刷新：宿主为正文区宽度占位（id 常为 {@code detail_body_text_scroll} / {@code stream_body_hscroll}），
     * **不包含横向滑动**；首次 {@link View#post} 与宿主<strong>横向宽度变化</strong>时再执行 {@code applyHexFormatting}，
     * 合并到下一帧以避免同帧多次布局连打。
     */
    public static void attachHexReflow(@Nullable View horizontalScrollHost,
                                       @Nullable View fallbackWidthView,
                                       @NonNull HexModeGate hexGate,
                                       @NonNull Runnable applyHexFormatting) {
        View host = horizontalScrollHost != null ? horizontalScrollHost : fallbackWidthView;
        clearHexReflow(host);
        if (host == null) {
            applyHexFormatting.run();
            return;
        }
        Runnable deferredApply = () -> {
            if (!hexGate.shouldStillShowHexDump()) {
                return;
            }
            applyHexFormatting.run();
        };
        View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (!hexGate.shouldStillShowHexDump()) {
                    return;
                }
                int nw = right - left;
                int ow = oldRight - oldLeft;
                /* 忽略 1px 级抖动，避免 Snackbar/Decoration 触发的无效 reflow 整表 notifyDataSetChanged */
                if (nw == ow || Math.abs(nw - ow) <= 1) {
                    return;
                }
                host.removeCallbacks(deferredApply);
                host.post(deferredApply);
            }
        };
        host.setTag(R.id.tag_hex_dump_layout_listener, new HexReflowToken(listener, deferredApply));
        host.addOnLayoutChangeListener(listener);
        host.post(deferredApply);
    }

    /**
     * 宿主内水平可视宽度减去内边距后的像素宽度；宿主一般为占满卡片/弹窗内容区的 {@link ViewGroup}。
     * 首帧尚未 {@link View#layout} 时优先 {@link View#getMeasuredWidth()}，避免误退回整屏宽度高估列数、
     * 三栏总长超出宿主导致右侧 ANSI 列被裁切。
     */
    public static int viewportWidthPx(@Nullable View horizontalScrollHost, @Nullable View textViewFallback) {
        View v = horizontalScrollHost != null ? horizontalScrollHost : textViewFallback;
        if (v != null) {
            int inner = insetInnerWidthPx(v);
            if (inner > 0) {
                return inner;
            }
        }
        if (textViewFallback != null && horizontalScrollHost != null
                && textViewFallback != horizontalScrollHost) {
            int inner = insetInnerWidthPx(textViewFallback);
            if (inner > 0) {
                return inner;
            }
        }
        if (textViewFallback != null) {
            return textViewFallback.getResources().getDisplayMetrics().widthPixels;
        }
        if (v != null) {
            return v.getResources().getDisplayMetrics().widthPixels;
        }
        return 0;
    }

    /** 控件已布局或可测量时的内部可用宽（减去水平 padding）。 */
    public static int insetInnerWidthPx(@NonNull View v) {
        int w = resolveWidthOrMeasured(v);
        if (w <= 0) {
            return 0;
        }
        int inner = w - v.getPaddingLeft() - v.getPaddingRight();
        return Math.max(0, inner);
    }

    private static int resolveWidthOrMeasured(@NonNull View v) {
        int w = v.getWidth();
        if (w <= 0) {
            w = v.getMeasuredWidth();
        }
        return w > 0 ? w : 0;
    }

    /**
     * 三栏 Hex 宿主内宽：<strong>优先</strong>{@code hexRecycler} 本体，其次内容根；
     * 测不到时返回 <strong>0</strong>（<strong>勿</strong>回退 {@link #viewportWidthPx} 整屏宽）——否则误判列数、
     * 三栏总行宽远大于弹窗/卡片正文，{@link androidx.recyclerview.widget.RecyclerView} {@code clipChildren}
     * 裁掉右侧 ANSI，首帧仅剩一字；宽高稳定后二次 layout / 滑动才恢复正常。
     *
     * @param textViewFallback 保留与历史调用一致，当前不参与宽度解析。
     */
    @SuppressWarnings("unused")
    public static int resolveTripletHostInnerWidthPx(@Nullable View hexRecyclerOrList,
                                                     @Nullable View contentRootFallback,
                                                     @Nullable View textViewFallback) {
        int inner = 0;
        if (hexRecyclerOrList != null) {
            inner = insetInnerWidthPx(hexRecyclerOrList);
        }
        if (inner <= 0 && contentRootFallback != null) {
            inner = insetInnerWidthPx(contentRootFallback);
        }
        return Math.max(0, inner);
    }

    /** 宿主宽未就绪时每帧延后排版的次数上限，超限则按宽度 0 走 {@link HexDumpFormatter#MIN_BYTES_PER_ROW}。 */
    private static final int TRIPLET_HOST_MEASURE_MAX_POSTS = 48;

    /**
     * 在 {@link #resolveTripletHostInnerWidthPx} 返回 &gt; 0（或用尽延后次数）时再执行排版，
     * 避免首帧用伪宽误算列数。
     */
    public static void scheduleTripletLayoutWhenHostMeasured(@NonNull View scheduleOn,
                                                            @Nullable View hexRecyclerOrList,
                                                            @Nullable View contentRootFallback,
                                                            @Nullable View textViewFallback,
                                                            @Nullable HexModeGate continueGate,
                                                            @NonNull Runnable layoutAction) {
        scheduleTripletLayoutWhenHostMeasuredImpl(scheduleOn, hexRecyclerOrList, contentRootFallback,
                textViewFallback, continueGate, layoutAction, 0);
    }

    private static void scheduleTripletLayoutWhenHostMeasuredImpl(@NonNull View scheduleOn,
                                                                @Nullable View hexRecyclerOrList,
                                                                @Nullable View contentRootFallback,
                                                                @Nullable View textViewFallback,
                                                                @Nullable HexModeGate continueGate,
                                                                @NonNull Runnable layoutAction,
                                                                int depth) {
        if (continueGate != null && !continueGate.shouldStillShowHexDump()) {
            return;
        }
        int inner = resolveTripletHostInnerWidthPx(hexRecyclerOrList, contentRootFallback, textViewFallback);
        if (inner > 0) {
            layoutAction.run();
            return;
        }
        if (depth >= TRIPLET_HOST_MEASURE_MAX_POSTS) {
            layoutAction.run();
            return;
        }
        scheduleOn.post(() -> scheduleTripletLayoutWhenHostMeasuredImpl(scheduleOn, hexRecyclerOrList,
                contentRootFallback, textViewFallback, continueGate, layoutAction, depth + 1));
    }

    /**
     * 按单行 item 绑定<strong>满列探针行</strong>后实测三栏宽选列数。
     */
    public static int computeBytesPerRowTripletBounded(@NonNull View probeRow, int hostInnerWidthPx) {
        if (hostInnerWidthPx <= 0) {
            return HexDumpFormatter.MIN_BYTES_PER_ROW;
        }
        HexDumpRowLayoutHelper.prepareTripletRowTypography(probeRow);
        int[] palette = HexDumpFormatter.resolveThemePalette(probeRow.getContext());
        final int budget = Math.max(1, hostInnerWidthPx - 2);
        for (int cols = HexDumpFormatter.MAX_BYTES_PER_ROW; cols >= HexDumpFormatter.MIN_BYTES_PER_ROW; cols--) {
            int[] ww = computeTripletWidthsFromInflatedRow(probeRow, cols, palette);
            int sum = ww[0] + ww[1] + ww[2];
            if (sum <= budget) {
                return cols;
            }
        }
        return HexDumpFormatter.MIN_BYTES_PER_ROW;
    }

    /**
     * 绑定满列探针字节（0xFF → Hex「FF」、ANSI「.」）后逐栏 {@link View#measure}，与屏上排版一致。
     */
    @NonNull
    public static int[] computeTripletWidthsFromInflatedRow(@NonNull View rowRoot, int cols) {
        int[] palette = HexDumpFormatter.resolveThemePalette(rowRoot.getContext());
        return computeTripletWidthsFromInflatedRow(rowRoot, cols, palette);
    }

    @NonNull
    public static int[] computeTripletWidthsFromInflatedRow(@NonNull View rowRoot, int cols,
                                                            @NonNull int[] palette) {
        TextView ot = rowRoot.findViewById(R.id.hex_line_offset);
        TextView ht = rowRoot.findViewById(R.id.hex_line_bytes);
        TextView at = rowRoot.findViewById(R.id.hex_line_ascii);
        if (ot == null || ht == null || at == null) {
            return new int[]{1, 1, 1};
        }
        HexDumpRowLayoutHelper.prepareTripletRowTypography(rowRoot);
        final int c = HexDumpFormatter.clampBytesPerRow(cols);
        byte[] probeBytes = new byte[c];
        Arrays.fill(probeBytes, (byte) 0xFF);
        HexDumpFormatter.bindTripletRowSpan(probeBytes, 0, c,
                palette[0], palette[1], palette[2], palette[3],
                ot, ht, at, null, 0, 0, 0);
        return new int[]{
                HexDumpRowLayoutHelper.measureColumnIntrinsicWidthPx(ot),
                HexDumpRowLayoutHelper.measureColumnIntrinsicWidthPx(ht),
                HexDumpRowLayoutHelper.measureColumnIntrinsicWidthPx(at)
        };
    }

    /**
     * 将 Hex {@link RecyclerView} 高度限制在「行数×行高」与 {@code maxHeightPx} 之间取较小；
     * 可选同步缩小外层 {@link androidx.core.widget.NestedScrollView}，避免弹窗正文区底部留空。
     * @param minHeightPx 正文区高度下限（HTTP 详情 {@link R.dimen#detail_hex_dump_recycler_min_px}；流弹窗 {@link R.dimen#stream_body_scroll_min_px}）
     */
    public static void scheduleClampHexRecyclerHeight(@NonNull HexDumpRecyclerView hexRecycler,
                                                      @NonNull View widthHost,
                                                      @NonNull View probeRow,
                                                      int cols,
                                                      int byteLength,
                                                      int wOff, int wHex, int wAsc,
                                                      int maxHeightPx,
                                                      int minHeightPx,
                                                      @Nullable View scrollViewportToSync,
                                                      int attempt) {
        applyTripletWidthsToProbe(probeRow, wOff, wHex, wAsc);
        final int minH = Math.max(0, minHeightPx);
        final int maxH = Math.max(maxHeightPx, minH);
        hexRecycler.post(() -> {
            if (hexRecycler.getVisibility() != View.VISIBLE) {
                return;
            }
            if (widthHost.getWidth() <= 0 && attempt < 12) {
                scheduleClampHexRecyclerHeight(hexRecycler, widthHost, probeRow, cols, byteLength,
                        wOff, wHex, wAsc, maxHeightPx, minHeightPx, scrollViewportToSync, attempt + 1);
                return;
            }
            int hostW = Math.max(1, widthHost.getWidth());
            probeRow.measure(
                    View.MeasureSpec.makeMeasureSpec(hostW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int rowH = probeRow.getMeasuredHeight();
            if (rowH <= 0) {
                TextView pb = probeRow.findViewById(R.id.hex_line_bytes);
                rowH = (int) Math.ceil(pb != null ? pb.getTextSize() * 1.4f : 40f);
            }
            int rowCount = byteLength <= 0 ? 0 : (byteLength + cols - 1) / cols;
            int pad = hexRecycler.getPaddingTop() + hexRecycler.getPaddingBottom();
            int contentH = pad + rowCount * rowH;
            int clamped = rowCount == 0
                    ? maxH
                    : Math.min(maxH, Math.max(rowH + pad, contentH));
            clamped = Math.max(minH, clamped);
            ViewGroup.LayoutParams lp = hexRecycler.getLayoutParams();
            if (lp != null) {
                lp.height = clamped;
                hexRecycler.setLayoutParams(lp);
            }
            if (scrollViewportToSync != null) {
                ViewGroup.LayoutParams slp = scrollViewportToSync.getLayoutParams();
                if (slp != null) {
                    slp.height = clamped;
                    scrollViewportToSync.setLayoutParams(slp);
                }
            }
            hexRecycler.requestLayout();
        });
    }

    private static void applyTripletWidthsToProbe(@NonNull View probeRow, int wOff, int wHex, int wAsc) {
        applyExactWidthToProbeColumn(probeRow.findViewById(R.id.hex_line_offset), wOff);
        applyExactWidthToProbeColumn(probeRow.findViewById(R.id.hex_line_bytes), wHex);
        applyExactWidthToProbeColumn(probeRow.findViewById(R.id.hex_line_ascii), wAsc);
    }

    private static void applyExactWidthToProbeColumn(@Nullable TextView tv, int widthPx) {
        if (tv == null || widthPx <= 0) {
            return;
        }
        ViewGroup.LayoutParams lp = tv.getLayoutParams();
        if (lp != null) {
            lp.width = widthPx;
            tv.setLayoutParams(lp);
        }
    }

    /**
     * 单 TextView 整表（不推荐用于需分栏复制的场景）；仍用于保持 API 完整性。
     */
    public static void formatIntoTextView(@Nullable View horizontalScrollHost,
                                          @NonNull TextView bodyText,
                                          @NonNull Context context,
                                          @NonNull byte[] raw,
                                          @NonNull HexModeGate hexGate) {
        if (!hexGate.shouldStillShowHexDump() || raw.length == 0) {
            return;
        }
        bodyText.setTypeface(Typeface.MONOSPACE);
        bodyText.setBackgroundColor(Color.TRANSPARENT);
        int vw = viewportWidthPx(horizontalScrollHost, bodyText);
        int cols = HexDumpFormatter.computeBytesPerRow(bodyText, vw);
        bodyText.setText(HexDumpFormatter.format(context, raw, cols));
    }
}
