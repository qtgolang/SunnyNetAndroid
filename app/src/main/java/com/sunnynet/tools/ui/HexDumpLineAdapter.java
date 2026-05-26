package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;

import java.util.Arrays;
import java.util.List;

/**
 * 十六进制三栏按「物理行」懒加载：仅绑定可见 Row，适用于大体积响应体以免主线程构造巨型 Spannable。
 * 跨行选区由宿主 {@link HexDumpRecyclerView} 维护，单行内不再使用系统文本拖选。
 */
public final class HexDumpLineAdapter extends RecyclerView.Adapter<HexDumpLineAdapter.LineHolder> {

    @NonNull
    private final int[] palette;
    @NonNull
    private byte[] payload = new byte[0];
    private int cols = HexDumpFormatter.MIN_BYTES_PER_ROW;
    private int wOffPx;
    private int wHexPx;
    private int wAscPx;
    private final int rowLayoutRes;

    /** 宿主列表，用于按需读取字体与列宽等；跨行选区由 ItemDecoration 叠绘，绑定行不传 Span 快照。 */
    @Nullable
    private final HexDumpRecyclerView hostRv;

    /**
     * @param themedContext Fragment/Activity Context，用于主题色调色板。
     */
    public HexDumpLineAdapter(@NonNull Context themedContext,
                              @LayoutRes int hexRowLayoutRes,
                              @Nullable HexDumpRecyclerView hostRecyclerView) {
        this.rowLayoutRes = hexRowLayoutRes;
        this.hostRv = hostRecyclerView;
        palette = HexDumpFormatter.resolveThemePalette(themedContext);
    }

    @NonNull
    byte[] peekPayload() {
        return payload;
    }

    int peekPayloadLength() {
        return payload.length;
    }

    int peekColsForSelection() {
        return cols;
    }

    /**
     * 更新正文与子项列布置；在主线程调用。
     */
    public void setPayloadAndLayout(@NonNull byte[] raw, int columns, int offsetColPx,
                                    int hexColPx, int asciiColPx) {
        columns = HexDumpFormatter.clampBytesPerRow(columns);
        if (columns == cols && offsetColPx == wOffPx && hexColPx == wHexPx && asciiColPx == wAscPx
                && payload.length == raw.length && Arrays.equals(payload, raw)) {
            return;
        }
        if (hostRv != null) {
            hostRv.resetCrossRowSelectionStateQuietly();
        }
        payload = raw;
        cols = columns;
        wOffPx = offsetColPx;
        wHexPx = hexColPx;
        wAscPx = asciiColPx;
        notifyDataSetChanged();
    }

    /** 释放可见行缓冲，切换到字符串模式或未展开 Hex 时使用。 */
    public void clearRows() {
        payload = new byte[0];
        if (hostRv != null) {
            hostRv.resetCrossRowSelectionStateQuietly();
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (payload.length == 0) {
            return 0;
        }
        return (payload.length + cols - 1) / cols;
    }

    @NonNull
    @Override
    public LineHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext()).inflate(rowLayoutRes, parent, false);
        HexDumpRowLayoutHelper.forceRowWrapContentWidth(row);
        return new LineHolder(row);
    }

    /** 跨行选区 payload：仅重绑 Span，不改列宽/行 layout，避免拖选时整行重排导致拖柄乱跳。 */
    static final String PAYLOAD_SELECTION = "hex_selection";

    /** 宿主在可见行上刷新选区 Span（松手/清除时用 payload 通知）。 */
    void notifyVisibleRowsSelectionSpans() {
        if (hostRv == null) {
            return;
        }
        for (int i = 0; i < hostRv.getChildCount(); i++) {
            View child = hostRv.getChildAt(i);
            int pos = hostRv.getChildAdapterPosition(child);
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos, PAYLOAD_SELECTION);
            }
        }
    }

    /**
     * 拖动过程中同步重绑可见行 Span（不经 {@link #notifyItemChanged} 队列），消除拖选延迟。
     * 可见行全部重绑以便缩选时及时清掉旧高亮。
     */
    void applySelectionSpansToVisibleHoldersSync() {
        if (hostRv == null) {
            return;
        }
        for (int i = 0; i < hostRv.getChildCount(); i++) {
            View child = hostRv.getChildAt(i);
            int pos = hostRv.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) {
                continue;
            }
            RecyclerView.ViewHolder vh = hostRv.getChildViewHolder(child);
            if (vh instanceof LineHolder) {
                bindSelectionSpansOnly((LineHolder) vh, pos);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull LineHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_SELECTION)) {
            bindSelectionSpansOnly(holder, position);
            return;
        }
        onBindViewHolder(holder, position);
    }

    /** 仅更新三栏 Spannable 选区高亮，跳过列宽与行宽 layout。 */
    private void bindSelectionSpansOnly(@NonNull LineHolder holder, int position) {
        int rowStart = position * cols;
        HexDumpCrossRowSnapshot crossRow = hostRv != null ? hostRv.buildCrossRowSnapshot() : null;
        HexDumpFormatter.bindTripletRowSpan(payload, rowStart, cols,
                palette[0], palette[1], palette[2], palette[3],
                holder.offset, holder.hex, holder.ascii,
                crossRow,
                hostRv != null ? hostRv.getCrossRowSelectionBgArgb() : 0,
                hostRv != null ? hostRv.getCrossRowSelectionHexFgArgb() : 0,
                hostRv != null ? hostRv.getCrossRowSelectionAsciiFgArgb() : 0);
    }

    @Override
    public void onBindViewHolder(@NonNull LineHolder holder, int position) {
        int hostInner = 0;
        if (hostRv != null) {
            hostInner = hostRv.getWidth() - hostRv.getPaddingLeft() - hostRv.getPaddingRight();
            if (hostInner > 0) {
                HexDumpRowLayoutHelper.forceRowMatchHostInnerWidth(holder.itemView, hostInner);
            } else {
                HexDumpRowLayoutHelper.forceRowWrapContentWidth(holder.itemView);
            }
        } else {
            HexDumpRowLayoutHelper.forceRowWrapContentWidth(holder.itemView);
        }
        int rowStart = position * cols;
        applyExactWidth(holder.offset, wOffPx);
        applyExactWidth(holder.hex, wHexPx);
        applyExactWidth(holder.ascii, wAscPx);
        HexDumpCrossRowSnapshot crossRow = hostRv != null ? hostRv.buildCrossRowSnapshot() : null;
        HexDumpFormatter.bindTripletRowSpan(payload, rowStart, cols,
                palette[0], palette[1], palette[2], palette[3],
                holder.offset, holder.hex, holder.ascii,
                crossRow,
                hostRv != null ? hostRv.getCrossRowSelectionBgArgb() : 0,
                hostRv != null ? hostRv.getCrossRowSelectionHexFgArgb() : 0,
                hostRv != null ? hostRv.getCrossRowSelectionAsciiFgArgb() : 0);
    }

    private static void applyExactWidth(@NonNull TextView tv, int widthPx) {
        ViewGroup.LayoutParams lp = tv.getLayoutParams();
        if (lp == null || widthPx <= 0) {
            return;
        }
        lp.width = widthPx;
        /* 必须与「跳过相同 width」区分： Decoration/父级触发二次 layout 时仍要强制同步，否则列宽会与 StaticLayout 短暂脱节 */
        tv.setLayoutParams(lp);
    }

    static final class LineHolder extends RecyclerView.ViewHolder {

        final TextView offset;
        final TextView hex;
        final TextView ascii;

        LineHolder(@NonNull View row) {
            super(row);
            offset = row.findViewById(R.id.hex_line_offset);
            hex = row.findViewById(R.id.hex_line_bytes);
            ascii = row.findViewById(R.id.hex_line_ascii);
            HexDumpRowLayoutHelper.prepareTripletRowTypography(row);
            disableChildTextViewTouchCapture(offset);
            disableChildTextViewTouchCapture(hex);
            disableChildTextViewTouchCapture(ascii);
            offset.setBackgroundColor(Color.TRANSPARENT);
            hex.setBackgroundColor(Color.TRANSPARENT);
            ascii.setBackgroundColor(Color.TRANSPARENT);
        }

        /** 避免 TextView 长按/焦点抢走 {@link HexDumpRecyclerView} 的跨行选区手势（尤其 ANSI 列）。 */
        private static void disableChildTextViewTouchCapture(@NonNull TextView tv) {
            tv.setClickable(false);
            tv.setLongClickable(false);
            tv.setFocusable(false);
            tv.setFocusableInTouchMode(false);
            tv.setTextIsSelectable(false);
            tv.setHorizontallyScrolling(false);
            tv.setMovementMethod(null);
        }
    }
}
