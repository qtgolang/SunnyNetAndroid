package com.sunnynet.tools.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;

/**
 * 跨行选区：底色在 {@link #onDraw}（文字下方）绘制，避免 {@link #onDrawOver} 半透明盖字导致 Hex 发灰；
 * 边界竖线与拖柄仍在 {@link #onDrawOver} 最上层。
 */
final class HexDumpSelectionOverlayDecoration extends RecyclerView.ItemDecoration {

    private final HexDumpRecyclerView host;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmpRect = new Rect();
    private final RectF tmpRf = new RectF();
    private final float handleRadiusPx;
    private final float edgeStrokePx;
    private final float stemStrokePx;

    /** 与 {@link #onDraw} 同帧写入，供 {@link #onDrawOver} 画拖柄。 */
    @Nullable
    private Float handleFirstLeft;
    private float handleFirstTop;
    private float handleFirstBottom;
    @Nullable
    private Float handleLastRight;
    private float handleLastTop;
    private float handleLastBottom;

    HexDumpSelectionOverlayDecoration(@NonNull HexDumpRecyclerView host) {
        this.host = host;
        this.handleRadiusPx = host.getSelectionHandleRadiusPx();
        float density = host.getResources().getDisplayMetrics().density;
        edgeStrokePx = Math.max(2f, density * 2f);
        stemStrokePx = Math.max(1.5f, density * 1.2f);
        fillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setColor(ColorUtils.setAlphaComponent(0xFFFF6D00, 0xE8));
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(Math.max(2f, density * 1.5f));
        handleStrokePaint.setColor(0xFFFFFFFF);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(edgeStrokePx);
        edgePaint.setColor(0xFFE65100);
        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeWidth(stemStrokePx);
        stemPaint.setColor(0xFFE65100);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent,
                       @NonNull RecyclerView.State state) {
        handleFirstLeft = null;
        handleLastRight = null;
        if (!host.isCrossRowSelectionActiveForOverlay()) {
            return;
        }
        fillPaint.setColor(host.getCrossRowSelectionBgArgb());
        collectSelectionBoundsAndMaybeFill(c, parent, true);
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
        if (!host.isCrossRowSelectionActiveForOverlay()) {
            host.clearHandlePositionsForHitTest();
            return;
        }
        if (handleFirstLeft == null || handleLastRight == null) {
            fillPaint.setColor(host.getCrossRowSelectionBgArgb());
            collectSelectionBoundsAndMaybeFill(c, parent, false);
        }
        if (handleFirstLeft == null || handleLastRight == null) {
            host.clearHandlePositionsForHitTest();
            return;
        }

        float firstLeft = handleFirstLeft;
        c.drawLine(firstLeft, handleFirstTop, firstLeft, handleFirstBottom, edgePaint);
        c.drawLine(handleLastRight, handleLastTop, handleLastRight, handleLastBottom, edgePaint);

        float below = handleRadiusPx * 1.3f;
        float startCx = firstLeft;
        float startCy = handleFirstBottom + below;
        float endCx = handleLastRight;
        float endCy = handleLastBottom + below;

        c.drawLine(startCx, handleFirstBottom, startCx, startCy - handleRadiusPx, stemPaint);
        c.drawLine(endCx, handleLastBottom, endCx, endCy - handleRadiusPx, stemPaint);

        c.drawCircle(startCx, startCy, handleRadiusPx, handleFillPaint);
        c.drawCircle(startCx, startCy, handleRadiusPx, handleStrokePaint);
        c.drawCircle(endCx, endCy, handleRadiusPx, handleFillPaint);
        c.drawCircle(endCx, endCy, handleRadiusPx, handleStrokePaint);

        host.setHandlePositionsForHitTest(startCx, startCy, endCx, endCy);
    }

    /**
     * 遍历可见行计算选区矩形；{@code drawFill} 为 true 时在文字下层填充（{@link #onDraw}）。
     */
    private void collectSelectionBoundsAndMaybeFill(@NonNull Canvas c,
                                                    @NonNull RecyclerView parent,
                                                    boolean drawFill) {
        HexDumpLineAdapter ad = host.getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        byte[] payload = ad.peekPayload();
        int cols = ad.peekColsForSelection();
        if (payload.length == 0 || cols <= 0) {
            return;
        }
        int selLo = host.getSelectionLoOrdered();
        int selHi = host.getSelectionHiOrdered();
        if (selLo > selHi) {
            return;
        }

        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (lm == null) {
            return;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            View row = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(row);
            if (pos == RecyclerView.NO_POSITION) {
                continue;
            }
            int rowStart = pos * cols;
            int rowLen = Math.min(cols, payload.length - rowStart);
            if (rowLen <= 0) {
                continue;
            }
            int rowTail = rowStart + rowLen - 1;
            int gb0 = Math.max(selLo, rowStart);
            int gb1 = Math.min(selHi, rowTail);
            if (gb0 > gb1) {
                continue;
            }
            int lb0 = gb0 - rowStart;
            int lb1 = gb1 - rowStart;
            /* 拖动过程由 Span 实时高亮；Decoration 仅画拖柄/边界线 */
            if (drawFill && host.isSelectionRangeAdjusting()) {
                paintColumnSelectionFill(c, parent, lm, row, lb0, lb1, true);
                paintColumnSelectionFill(c, parent, lm, row, lb0, lb1, false);
            }
        }
        assignHandleBoundsFromSelectionEndpoints(parent, lm, cols, payload.length, selLo, selHi);
    }

    /**
     * 拖柄锚定选区首尾字节所在行（非「最后一个 layout 成功的可见行」），避免高亮与拖柄脱节。
     */
    private void assignHandleBoundsFromSelectionEndpoints(@NonNull RecyclerView parent,
                                                            @NonNull RecyclerView.LayoutManager lm,
                                                            int cols, int payloadLen,
                                                            int selLo, int selHi) {
        if (payloadLen <= 0 || cols <= 0) {
            return;
        }
        boolean hexColumn = host.isSelectionHexColumn();
        int firstRowPos = selLo / cols;
        int lastRowPos = selHi / cols;
        int firstRowStart = firstRowPos * cols;
        int lastRowStart = lastRowPos * cols;
        int firstLb0 = selLo - firstRowStart;
        int lastRowLen = Math.min(cols, payloadLen - lastRowStart);
        int lastLb1 = Math.min(selHi - lastRowStart, lastRowLen - 1);

        View firstRow = lm.findViewByPosition(firstRowPos);
        if (firstRow != null) {
            applyHandleBoundsFromColumn(firstRow, lm, firstLb0, firstLb0, hexColumn, true);
        }
        View lastRow = lm.findViewByPosition(lastRowPos);
        if (lastRow != null) {
            int lastLb0 = Math.max(0, selLo - lastRowStart);
            applyHandleBoundsFromColumn(lastRow, lm, lastLb0, lastLb1, hexColumn, false);
        }
    }

    private void applyHandleBoundsFromColumn(@NonNull View row,
                                             @NonNull RecyclerView.LayoutManager lm,
                                             int lb0, int lb1,
                                             boolean hexColumn,
                                             boolean isStartEndpoint) {
        ColumnBounds bounds = measureColumnSelectionBounds(lm, row, lb0, lb1, hexColumn);
        if (bounds == null) {
            return;
        }
        if (isStartEndpoint || handleFirstLeft == null) {
            handleFirstLeft = bounds.left;
            handleFirstTop = bounds.top;
            handleFirstBottom = bounds.bottom;
        }
        if (!isStartEndpoint) {
            handleLastRight = bounds.right;
            handleLastTop = bounds.top;
            handleLastBottom = bounds.bottom;
        }
    }

    /**
     * 计算单列选区矩形并可选填充（拖动过程中双栏同步）。
     */
    private void paintColumnSelectionFill(@NonNull Canvas c,
                                          @NonNull RecyclerView parent,
                                          @NonNull RecyclerView.LayoutManager lm,
                                          @NonNull View row,
                                          int lb0, int lb1,
                                          boolean hexColumn) {
        ColumnBounds bounds = measureColumnSelectionBounds(lm, row, lb0, lb1, hexColumn);
        if (bounds == null) {
            return;
        }
        tmpRf.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        c.drawRoundRect(tmpRf, 6f, 6f, fillPaint);
    }

    @Nullable
    private ColumnBounds measureColumnSelectionBounds(@NonNull RecyclerView.LayoutManager lm,
                                                      @NonNull View row,
                                                      int lb0, int lb1,
                                                      boolean hexColumn) {
        TextView tv = row.findViewById(hexColumn
                ? R.id.hex_line_bytes
                : R.id.hex_line_ascii);
        if (tv == null) {
            return null;
        }
        Layout layout = tv.getLayout();
        if (layout == null) {
            if (tv.getWidth() <= 0) {
                return null;
            }
            if (!(row instanceof ViewGroup)) {
                return null;
            }
            tv.getDrawingRect(tmpRect);
            ((ViewGroup) row).offsetDescendantRectToMyCoords(tv, tmpRect);
            float left = lm.getDecoratedLeft(row) + tmpRect.left + tv.getTotalPaddingLeft();
            float right = lm.getDecoratedLeft(row) + tmpRect.right - tv.getTotalPaddingRight();
            float rTop = lm.getDecoratedTop(row) + tmpRect.top + tv.getTotalPaddingTop();
            float rBottom = lm.getDecoratedTop(row) + tmpRect.bottom - tv.getTotalPaddingBottom();
            return new ColumnBounds(left, rTop, right, rBottom);
        }
        int lead = HexDumpFormatter.HEX_PANE_LEADING_CHAR_COUNT;
        int startChar = hexColumn ? (lead + lb0 * 3) : lb0;
        int endCharExclusive = hexColumn ? (lead + (lb1 + 1) * 3) : (lb1 + 1);
        int line = layout.getLineForOffset(startChar);
        int lineEnd = layout.getLineForOffset(Math.max(endCharExclusive - 1, startChar));
        if (line != lineEnd) {
            lineEnd = line;
        }

        float x0 = layout.getPrimaryHorizontal(startChar) + tv.getTotalPaddingLeft();
        float x1 = layout.getPrimaryHorizontal(endCharExclusive) + tv.getTotalPaddingLeft();
        if (x1 < x0) {
            float t = x0;
            x0 = x1;
            x1 = t;
        }
        int top = layout.getLineTop(line) + tv.getExtendedPaddingTop();
        int bottom = layout.getLineBottom(line) + tv.getExtendedPaddingTop();

        if (!(row instanceof ViewGroup)) {
            return null;
        }
        tv.getDrawingRect(tmpRect);
        ((ViewGroup) row).offsetDescendantRectToMyCoords(tv, tmpRect);

        float left = lm.getDecoratedLeft(row) + tmpRect.left + x0;
        float right = lm.getDecoratedLeft(row) + tmpRect.left + x1;
        float rTop = lm.getDecoratedTop(row) + tmpRect.top + top;
        float rBottom = lm.getDecoratedTop(row) + tmpRect.top + bottom;
        return new ColumnBounds(left, rTop, right, rBottom);
    }

    private static final class ColumnBounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        ColumnBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
