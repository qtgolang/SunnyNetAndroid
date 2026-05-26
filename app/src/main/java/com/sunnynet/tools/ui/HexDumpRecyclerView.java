package com.sunnynet.tools.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.sunnynet.tools.R;

/**
 * Hex 检视专用列表：
 * <ul>
 * <li>与外层 {@link androidx.core.widget.NestedScrollView} 协调手势；</li>
 * <li>跨行选区<strong>浅琥珀底</strong>在文字下层绘制（{@link HexDumpSelectionOverlayDecoration}），橙色拖柄在顶层；拖柄由 {@link HexDumpSelectionHandleTouchListener} 处理；列表内短按可 {@link #clearCrossRowSelection()}；</li>
 * <li>嵌入弹窗 {@link androidx.core.widget.NestedScrollView} 时可将 {@link #setApplySnackRecyclerPadding(boolean)} 置为 false，
 *     避免 Snackbar 为列表增加底 {@code padding} 触发整块重排版、三栏列宽错乱。</li>
 * </ul>
 */
public class HexDumpRecyclerView extends RecyclerView {

    /** false：弹出跨行 Snackbar 时不再增加 RecyclerView 底 padding（详见类文档）。 */
    private boolean applySnackRecyclerPadding = true;

    private final GestureDetector gestureDetector;
    /** 叠在 Hex/文本列字母上的选区底色（实色琥珀，与未选区明显区分）。 */
    private final @ColorInt int selectionBgArgb;
    private final @ColorInt int selectionHexFgArgb;
    private final @ColorInt int selectionAsciiFgArgb;

    private float downRvX;
    private float downRvY;
    private boolean fingerStartedOnSelectableColumn;

    /** 已从锚点拖动过 finger，松手时出现复制条（单点长按也会提示）。 */
    private boolean awaitingCopySnackAfterFingerUp;
    private boolean draggingCrossRowSelection;
    /** 拖柄调整选区中：与 {@link #draggingCrossRowSelection} 一样只刷 Overlay、不整行 rebind。 */
    private boolean draggingSelectionHandle;
    private boolean crossRowSelectionActive;
    /** true：中间 Hex 栏；false：右侧文本列。 */
    private boolean selectionIsHexColumn;
    private int anchorGlobalByteIndex;
    private int focusGlobalByteIndex;

    private float lastRawX;
    private float lastRawY;

    /** 与 {@link HexDumpSelectionOverlayDecoration} 一致，用于命中拖柄。 */
    private final float selectionHandleRadiusPx;

    private float handleStartCx = Float.NaN;
    private float handleStartCy;
    private float handleEndCx;
    private float handleEndCy;

    /** 当前跨行复制提示条，避免重复叠加并便于清除选区时收起。 */
    private @Nullable Snackbar activeCrossRowSnackbar;

    /** Snackbar 弹出前保存的列表底 padding，收起时恢复以免结束拖柄/末尾行被挡。 */
    private int snackbarRestoredPaddingBottom = Integer.MIN_VALUE;

    static final int HIT_HANDLE_NONE = 0;
    static final int HIT_HANDLE_START = 1;
    static final int HIT_HANDLE_END = 2;

    public HexDumpRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public HexDumpRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HexDumpRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs,
                               int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Context ctx = context;
        selectionBgArgb = ContextCompat.getColor(ctx, R.color.hex_selection_fill);
        selectionHexFgArgb = ContextCompat.getColor(ctx, R.color.hex_selection_fg_hex);
        selectionAsciiFgArgb = ContextCompat.getColor(ctx, R.color.hex_selection_fg_ascii);
        selectionHandleRadiusPx = getResources().getDisplayMetrics().density * 10f;
        addItemDecoration(new HexDumpSelectionOverlayDecoration(this));
        addOnItemTouchListener(new HexDumpSelectionHandleTouchListener(this));
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                HexDumpRecyclerView.this.onLongPressStartCrossSelection(e.getX(), e.getY());
            }
        });
    }

    /**
     * 跨行选中松手后出现 Snackbar 时，是否在列表本体上追加底 padding 以露出选区末尾。
     * 弹窗内 NestedScrollView 场景建议置 false，否则易与父级二次 measure 交织导致三栏错乱。
     */
    public void setApplySnackRecyclerPadding(boolean applySnackRecyclerPadding) {
        this.applySnackRecyclerPadding = applySnackRecyclerPadding;
    }

    /** 供 {@link HexDumpLineAdapter#setPayloadAndLayout} 在正文替换前调用，不写回 UI。 */
    void resetCrossRowSelectionStateQuietly() {
        dismissActiveCrossRowSnackbar();
        clearCrossRowSnackPaddingSession();
        crossRowSelectionActive = false;
        draggingCrossRowSelection = false;
        draggingSelectionHandle = false;
        awaitingCopySnackAfterFingerUp = false;
        clearHandlePositionsForHitTest();
    }

    /**
     * 清除跨行选区并刷新绘制层。
     */
    public void clearCrossRowSelection() {
        resetCrossRowSelectionStateQuietly();
        refreshSelectionVisuals(true);
    }

    public @ColorInt int getCrossRowSelectionBgArgb() {
        return selectionBgArgb;
    }

    @ColorInt
    int getCrossRowSelectionHexFgArgb() {
        return selectionHexFgArgb;
    }

    @ColorInt
    int getCrossRowSelectionAsciiFgArgb() {
        return selectionAsciiFgArgb;
    }

    /** 供 {@link HexDumpLineAdapter} 绑定行时叠选区 Span。 */
    @Nullable
    HexDumpCrossRowSnapshot buildCrossRowSnapshot() {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return null;
        }
        return HexDumpCrossRowSnapshot.maybeFrom(crossRowSelectionActive, selectionIsHexColumn,
                anchorGlobalByteIndex, focusGlobalByteIndex, ad.peekPayloadLength());
    }

    float getSelectionHandleRadiusPx() {
        return selectionHandleRadiusPx;
    }

    boolean isCrossRowSelectionActiveForOverlay() {
        return crossRowSelectionActive;
    }

    /** 手指拖选或拖柄调整中：Decoration 负责实时高亮，勿触发 Adapter 整行重绑。 */
    boolean isSelectionRangeAdjusting() {
        return draggingCrossRowSelection || draggingSelectionHandle;
    }

    void setDraggingSelectionHandle(boolean dragging) {
        draggingSelectionHandle = dragging;
    }

    boolean isSelectionHexColumn() {
        return selectionIsHexColumn;
    }

    /** 载荷内有序下标；无适配器时返回 0。 */
    int getSelectionLoOrdered() {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return 0;
        }
        int plen = ad.peekPayloadLength();
        if (plen <= 0) {
            return 0;
        }
        int lo = Math.min(anchorGlobalByteIndex, focusGlobalByteIndex);
        return Math.max(0, Math.min(lo, plen - 1));
    }

    int getSelectionHiOrdered() {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return 0;
        }
        int plen = ad.peekPayloadLength();
        if (plen <= 0) {
            return 0;
        }
        int hi = Math.max(anchorGlobalByteIndex, focusGlobalByteIndex);
        return Math.max(0, Math.min(hi, plen - 1));
    }

    void clearHandlePositionsForHitTest() {
        handleStartCx = Float.NaN;
    }

    void setHandlePositionsForHitTest(float sx, float sy, float ex, float ey) {
        handleStartCx = sx;
        handleStartCy = sy;
        handleEndCx = ex;
        handleEndCy = ey;
    }

    /** 供 {@link HexDumpSelectionHandleTouchListener} 在拖柄松手后触发（该手势不经过 {@link #onTouchEvent}）。 */
    void notifyCrossRowCopyAfterHandleRelease() {
        if (!crossRowSelectionActive) {
            return;
        }
        awaitingCopySnackAfterFingerUp = true;
        refreshSelectionVisuals(true);
        maybeOfferCrossRowCopySnack();
    }

    int hitTestSelectionHandle(float rvX, float rvY) {
        if (!crossRowSelectionActive || Float.isNaN(handleStartCx)) {
            return HIT_HANDLE_NONE;
        }
        float extra = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        float r = selectionHandleRadiusPx + extra;
        float d0 = (float) Math.hypot(rvX - handleStartCx, rvY - handleStartCy);
        float d1 = (float) Math.hypot(rvX - handleEndCx, rvY - handleEndCy);
        if (d0 <= r && d1 <= r) {
            return d0 <= d1 ? HIT_HANDLE_START : HIT_HANDLE_END;
        }
        if (d0 <= r) {
            return HIT_HANDLE_START;
        }
        if (d1 <= r) {
            return HIT_HANDLE_END;
        }
        return HIT_HANDLE_NONE;
    }

    /** 拖动左/上侧拖柄调整选区起点（字节下标）。 */
    void moveSelectionStartHandleToFinger(float rvX, float rvY, float rawX, float rawY) {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        MappedHit hit = mapFingerDuringDrag(rvX, rvY, rawX, rawY,
                selectionIsHexColumn, ad);
        if (hit == null) {
            return;
        }
        int plen = ad.peekPayloadLength();
        if (plen <= 0) {
            return;
        }
        int loOrd = Math.min(anchorGlobalByteIndex, focusGlobalByteIndex);
        int hiOrd = Math.max(anchorGlobalByteIndex, focusGlobalByteIndex);
        int nb = Math.max(0, Math.min(hit.globalByte, Math.min(hiOrd, plen - 1)));
        anchorGlobalByteIndex = nb;
        focusGlobalByteIndex = hiOrd;
        refreshSelectionDuringDrag();
    }

    /** 拖动右/下侧拖柄调整选区终点。 */
    void moveSelectionEndHandleToFinger(float rvX, float rvY, float rawX, float rawY) {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        MappedHit hit = mapFingerDuringDrag(rvX, rvY, rawX, rawY,
                selectionIsHexColumn, ad);
        if (hit == null) {
            return;
        }
        int plen = ad.peekPayloadLength();
        if (plen <= 0) {
            return;
        }
        int loOrd = Math.min(anchorGlobalByteIndex, focusGlobalByteIndex);
        int hiOrd = Math.max(anchorGlobalByteIndex, focusGlobalByteIndex);
        int nb = Math.max(loOrd, Math.min(hit.globalByte, plen - 1));
        anchorGlobalByteIndex = loOrd;
        focusGlobalByteIndex = nb;
        refreshSelectionDuringDrag();
    }

    /**
     * 自直接父视图起沿 View 树向上标记，避免中间的 {@link FrameLayout}、
     * {@link androidx.core.widget.NestedScrollView} 抢走 Hex 列表纵向滑动。
     */
    private static void propagateDisallowInterceptTouchEventToAncestors(@Nullable View fromChild,
                                                                       boolean disallow) {
        if (fromChild == null) {
            return;
        }
        for (ViewParent p = fromChild.getParent(); p instanceof ViewGroup;
             p = ((ViewGroup) p).getParent()) {
            ((ViewGroup) p).requestDisallowInterceptTouchEvent(disallow);
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (draggingCrossRowSelection || isTouchOnSelectableColumn(ev.getX(), ev.getY())) {
            gestureDetector.onTouchEvent(ev);
            onInterceptTouchEvent(ev);
            return onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 触摸点是否落在 Hex 或 ANSI 栏（含行内 padding 区）。 */
    private boolean isTouchOnSelectableColumn(float rvX, float rvY) {
        View row = findChildViewUnder(rvX, rvY);
        if (!(row instanceof ViewGroup)) {
            return false;
        }
        float lx = rvX - row.getLeft();
        float ly = rvY - row.getTop();
        return touchHitsTripletColumn((ViewGroup) row, lx, ly) != HIT_COLUMN_NONE;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent e) {
        lastRawX = e.getRawX();
        lastRawY = e.getRawY();

        final int masked = e.getActionMasked();

        if (draggingCrossRowSelection) {
            switch (masked) {
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    break;
            }
        }

        if (masked == MotionEvent.ACTION_DOWN) {
            propagateDisallowInterceptTouchEventToAncestors(this, true);
            downRvX = e.getX();
            downRvY = e.getY();
            View row = findChildViewUnder(downRvX, downRvY);
            if (row instanceof ViewGroup) {
                float lx = downRvX - row.getLeft();
                float ly = downRvY - row.getTop();
                fingerStartedOnSelectableColumn =
                        touchHitsTripletColumn((ViewGroup) row, lx, ly) != HIT_COLUMN_NONE;
            } else {
                fingerStartedOnSelectableColumn = false;
            }
        } else if (!draggingCrossRowSelection
                && (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL)) {
            fingerStartedOnSelectableColumn = false;
            propagateDisallowInterceptTouchEventToAncestors(this, false);
        } else if (!draggingCrossRowSelection && masked == MotionEvent.ACTION_MOVE
                && fingerStartedOnSelectableColumn) {
            float dx = e.getX() - downRvX;
            float dy = e.getY() - downRvY;
            float dist = (float) Math.hypot(dx, dy);
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (dist <= slop) {
                return false;
            }
            fingerStartedOnSelectableColumn = false;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent e) {
        lastRawX = e.getRawX();
        lastRawY = e.getRawY();

        final int masked = e.getActionMasked();
        if (draggingCrossRowSelection) {
            switch (masked) {
                case MotionEvent.ACTION_MOVE:
                    extendCrossRowToFinger(e.getX(), e.getY(), lastRawX, lastRawY);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    draggingCrossRowSelection = false;
                    fingerStartedOnSelectableColumn = false;
                    propagateDisallowInterceptTouchEventToAncestors(this, false);
                    refreshSelectionVisuals(true);
                    maybeOfferCrossRowCopySnack();
                    return true;
                default:
                    break;
            }
        }
        if (masked == MotionEvent.ACTION_UP && crossRowSelectionActive && !draggingCrossRowSelection) {
            float dx = e.getX() - downRvX;
            float dy = e.getY() - downRvY;
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if ((dx * dx + dy * dy) <= slop * slop
                    && hitTestSelectionHandle(e.getX(), e.getY()) == HIT_HANDLE_NONE) {
                clearCrossRowSelection();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void onLongPressStartCrossSelection(float rvX, float rvY) {
        if (getHexDumpAdapter() == null) {
            return;
        }
        MappedHit hit = mapTouchToPayloadByte(rvX, rvY, lastRawX, lastRawY);
        if (hit == null) {
            return;
        }
        awaitingCopySnackAfterFingerUp = true;
        crossRowSelectionActive = true;
        selectionIsHexColumn = hit.hexColumn;
        anchorGlobalByteIndex = focusGlobalByteIndex = hit.globalByte;
        draggingCrossRowSelection = true;
        refreshSelectionDuringDrag();
    }

    private void extendCrossRowToFinger(float rvLocalX, float rvLocalY,
                                        float rawX, float rawY) {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        MappedHit hit = mapFingerDuringDrag(rvLocalX, rvLocalY, rawX, rawY,
                selectionIsHexColumn, ad);
        if (hit == null) {
            return;
        }
        focusGlobalByteIndex = hit.globalByte;
        refreshSelectionDuringDrag();
    }

    /** 拖选进行时：只允许落在开始时的同一栏，避免 Hex/文本列混选语义不清。 */
    @Nullable
    private MappedHit mapFingerDuringDrag(float rvX, float rvY, float rawX, float rawY,
                                          boolean hexColumn,
                                          @NonNull HexDumpLineAdapter ad) {
        byte[] raw = ad.peekPayload();
        int cols = ad.peekColsForSelection();
        if (raw.length == 0 || cols <= 0) {
            return null;
        }
        View row = findChildViewUnder(rvX, rvY);
        if (!(row instanceof ViewGroup)) {
            return null;
        }
        int pos = getChildAdapterPosition(row);
        if (pos == NO_POSITION) {
            return null;
        }
        int rowStart = pos * cols;
        int rowLen = Math.min(cols, raw.length - rowStart);
        if (rowLen <= 0) {
            return null;
        }
        int id = hexColumn ? R.id.hex_line_bytes : R.id.hex_line_ascii;
        TextView tv = row.findViewById(id);
        if (tv == null) {
            return null;
        }
        MappedHit mh = resolveByteInsideTextView(tv, rawX, rawY, rowStart, rowLen,
                hexColumn);
        return mh;
    }

    @Nullable
    private MappedHit mapTouchToPayloadByte(float rvX, float rvY, float rawX, float rawY) {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return null;
        }
        byte[] raw = ad.peekPayload();
        int cols = ad.peekColsForSelection();
        if (raw.length == 0 || cols <= 0) {
            return null;
        }
        View row = findChildViewUnder(rvX, rvY);
        if (!(row instanceof ViewGroup)) {
            return null;
        }
        int pos = getChildAdapterPosition(row);
        if (pos == NO_POSITION) {
            return null;
        }
        int rowStart = pos * cols;
        int rowLen = Math.min(cols, raw.length - rowStart);
        if (rowLen <= 0) {
            return null;
        }
        float lx = rvX - row.getLeft();
        float ly = rvY - row.getTop();
        int columnHit = touchHitsTripletColumn((ViewGroup) row, lx, ly);
        if (columnHit == HIT_COLUMN_HEX) {
            TextView hexTv = row.findViewById(R.id.hex_line_bytes);
            if (hexTv != null) {
                return resolveByteInsideTextView(hexTv, rawX, rawY, rowStart, rowLen, true);
            }
        } else if (columnHit == HIT_COLUMN_ASCII) {
            TextView asciiTv = row.findViewById(R.id.hex_line_ascii);
            if (asciiTv != null) {
                return resolveByteInsideTextView(asciiTv, rawX, rawY, rowStart, rowLen, false);
            }
        }
        return null;
    }

    private static final int HIT_COLUMN_NONE = 0;
    private static final int HIT_COLUMN_HEX = 1;
    private static final int HIT_COLUMN_ASCII = 2;

    /** 按三栏子 View 的 layout bounds 判定命中列，比 leaf 递归更稳（ANSI 列 padding 区也能命中）。 */
    private static int touchHitsTripletColumn(@NonNull ViewGroup row, float lx, float ly) {
        TextView asciiTv = row.findViewById(R.id.hex_line_ascii);
        if (asciiTv != null && asciiTv.getVisibility() == VISIBLE
                && lx >= asciiTv.getLeft() && lx < asciiTv.getRight()
                && ly >= asciiTv.getTop() && ly < asciiTv.getBottom()) {
            return HIT_COLUMN_ASCII;
        }
        TextView hexTv = row.findViewById(R.id.hex_line_bytes);
        if (hexTv != null && hexTv.getVisibility() == VISIBLE
                && lx >= hexTv.getLeft() && lx < hexTv.getRight()
                && ly >= hexTv.getTop() && ly < hexTv.getBottom()) {
            return HIT_COLUMN_HEX;
        }
        return HIT_COLUMN_NONE;
    }

    /** 命中栏内字节；{@code Layout} 未就绪时用行首兜底，避免长按无响应。 */
    @NonNull
    private static MappedHit resolveByteInsideTextView(@NonNull TextView tv,
                                                       float rawX, float rawY,
                                                       int rowStart, int rowLen,
                                                       boolean hexColumn) {
        if (tv.getLayout() == null) {
            return new MappedHit(hexColumn, rowStart);
        }
        int[] loc = new int[2];
        tv.getLocationOnScreen(loc);
        float lx = rawX - loc[0];
        float ly = rawY - loc[1];
        float textL = tv.getTotalPaddingLeft();
        float textR = tv.getWidth() - tv.getTotalPaddingRight();
        float textT = tv.getTotalPaddingTop();
        float textB = tv.getHeight() - tv.getTotalPaddingBottom();
        if (textR > textL) {
            if (lx < textL) {
                lx = textL;
            } else if (lx >= textR) {
                lx = textR - 1f;
            }
        }
        if (textB > textT) {
            if (ly < textT) {
                ly = textT;
            } else if (ly >= textB) {
                ly = textB - 1f;
            }
        }
        int charOff = tv.getOffsetForPosition(lx, ly);
        int localByte = hexColumn
                ? hexCharOffsetToByteIndex(Math.max(0, charOff), rowLen)
                : asciiCharOffsetToByteIndex(Math.max(0, charOff), rowLen);
        localByte = Math.max(0, Math.min(localByte, rowLen - 1));
        return new MappedHit(hexColumn, rowStart + localByte);
    }

    private static int hexCharOffsetToByteIndex(int charOff, int rowLen) {
        if (rowLen <= 0) {
            return 0;
        }
        int lead = HexDumpFormatter.HEX_PANE_LEADING_CHAR_COUNT;
        if (charOff < lead) {
            return 0;
        }
        int rel = charOff - lead;
        int b = rel / 3;
        return Math.min(b, rowLen - 1);
    }

    private static int asciiCharOffsetToByteIndex(int charOff, int rowLen) {
        if (rowLen <= 0) {
            return 0;
        }
        return Math.min(Math.max(charOff, 0), rowLen - 1);
    }

    /** 拖动/拖柄过程中：同步 Span + Decoration + 立即 invalidate，避免 notify 队列延迟。 */
    private void refreshSelectionDuringDrag() {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad != null) {
            ad.applySelectionSpansToVisibleHoldersSync();
        }
        invalidateItemDecorations();
        invalidate();
    }

    /** 选区落定或清除：Overlay + 可见行 Span。 */
    private void refreshSelectionVisuals(boolean applySpans) {
        if (applySpans) {
            HexDumpLineAdapter ad = getHexDumpAdapter();
            if (ad != null) {
                ad.applySelectionSpansToVisibleHoldersSync();
            }
        }
        invalidateItemDecorations();
        invalidate();
    }

    private void dismissActiveCrossRowSnackbar() {
        Snackbar prev = activeCrossRowSnackbar;
        activeCrossRowSnackbar = null;
        if (prev != null) {
            prev.dismiss();
        } else {
            clearCrossRowSnackPaddingSession();
        }
    }

    /** 约一条 Snackbar + 安全间距，给列表底部留白，配合 {@code clipToPadding=false} 可把末尾选区滚出遮挡区。 */
    private int computeCrossRowSnackBottomInsetPx() {
        return (int) (getResources().getDisplayMetrics().density * 72f + 0.5f);
    }

    private void applyCrossRowSnackPaddingSession() {
        if (!applySnackRecyclerPadding) {
            return;
        }
        if (snackbarRestoredPaddingBottom == Integer.MIN_VALUE) {
            snackbarRestoredPaddingBottom = getPaddingBottom();
        }
        int inset = computeCrossRowSnackBottomInsetPx();
        setPaddingRelative(ViewCompat.getPaddingStart(this), getPaddingTop(),
                ViewCompat.getPaddingEnd(this), snackbarRestoredPaddingBottom + inset);
    }

    /** 无活动的 Snackbar 时恢复底部 padding（更换/关闭条时调用）。 */
    private void clearCrossRowSnackPaddingSession() {
        if (snackbarRestoredPaddingBottom == Integer.MIN_VALUE) {
            return;
        }
        setPaddingRelative(ViewCompat.getPaddingStart(this), getPaddingTop(),
                ViewCompat.getPaddingEnd(this), snackbarRestoredPaddingBottom);
        snackbarRestoredPaddingBottom = Integer.MIN_VALUE;
    }

    private void nudgeScrollAfterCrossRowSnackPadding() {
        if (!applySnackRecyclerPadding) {
            return;
        }
        int nudge = computeCrossRowSnackBottomInsetPx();
        post(() -> smoothScrollBy(0, nudge));
    }

    /**
     * Snackbar 宿主沿父链找 {@link CoordinatorLayout} 或 {@code android.R.id.content}；
     * 再用 {@link Snackbar#setAnchorView} 贴在列表上方，并为列表加底 padding，减轻挡住选区末端的问题。
     */
    @NonNull
    private static View findSnackbarAnchorView(@NonNull View from) {
        View best = from;
        for (ViewParent walk = from.getParent(); walk instanceof View;
             walk = ((View) walk).getParent()) {
            View v = (View) walk;
            if (v instanceof CoordinatorLayout) {
                return v;
            }
            if (v.getId() == android.R.id.content) {
                return v;
            }
            best = v;
        }
        return best;
    }

    /** 
     * 保证 Snackbar 文案与「复制」清晰可读。部分依赖树里可能没有 M3 {@code colorInverseSurface}，
     * 改用 {@code colorOnSurface}/{@code colorSurface} 互换来得到反色对比条。
     */
    private void tuneCrossRowSnackbarColors(@NonNull Snackbar sb) {
        Context ctx = getContext();
        View root = sb.getView();
        int onSurfFallback = ContextCompat.getColor(ctx, R.color.sunny_on_surface);
        int surfFallback = ContextCompat.getColor(ctx, R.color.sunny_surface);
        int barBg = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface,
                onSurfFallback);
        int barFg = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface,
                surfFallback);
        root.setBackgroundTintList(ColorStateList.valueOf(barBg));
        TextView msg = root.findViewById(com.google.android.material.R.id.snackbar_text);
        if (msg != null) {
            msg.setTextColor(barFg);
        }
        int actionInk = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.sunny_primary));
        sb.setActionTextColor(ColorStateList.valueOf(actionInk));
        TextView act = root.findViewById(com.google.android.material.R.id.snackbar_action);
        if (act != null) {
            act.setTextColor(actionInk);
        }
    }

    private void maybeOfferCrossRowCopySnack() {
        if (!awaitingCopySnackAfterFingerUp || !crossRowSelectionActive) {
            awaitingCopySnackAfterFingerUp = false;
            return;
        }
        awaitingCopySnackAfterFingerUp = false;
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        byte[] raw = ad.peekPayload();
        if (raw.length == 0) {
            return;
        }
        int anchorLo = Math.min(anchorGlobalByteIndex, focusGlobalByteIndex);
        int anchorHi = Math.max(anchorGlobalByteIndex, focusGlobalByteIndex);
        final int selLo = Math.max(0, Math.min(anchorLo, raw.length - 1));
        final int selHi = Math.max(0, Math.min(anchorHi, raw.length - 1));
        if (selLo > selHi) {
            return;
        }
        int byteCount = selHi - selLo + 1;
        dismissActiveCrossRowSnackbar();
        View anchor = findSnackbarAnchorView(this);
        Snackbar sb = Snackbar.make(anchor,
                getContext().getString(R.string.hex_cross_row_snack, byteCount),
                Snackbar.LENGTH_LONG);
        sb.setAction(R.string.hex_cross_row_copy, v -> copyCrossRowRangeToClipboard(selLo, selHi));
        tuneCrossRowSnackbarColors(sb);
        sb.setAnchorView(this);
        applyCrossRowSnackPaddingSession();
        nudgeScrollAfterCrossRowSnackPadding();
        sb.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (activeCrossRowSnackbar == transientBottomBar) {
                    activeCrossRowSnackbar = null;
                }
                if (activeCrossRowSnackbar == null) {
                    clearCrossRowSnackPaddingSession();
                }
            }
        });
        activeCrossRowSnackbar = sb;
        sb.show();
    }

    private void copyCrossRowRangeToClipboard(int loInclusive, int hiInclusive) {
        HexDumpLineAdapter ad = getHexDumpAdapter();
        if (ad == null) {
            return;
        }
        byte[] raw = ad.peekPayload();
        if (raw.length == 0 || loInclusive > hiInclusive) {
            return;
        }
        Context ctx = getContext();
        if (selectionIsHexColumn) {
            ClipboardUiHelper.copyPlain(ctx,
                    HexDumpFormatter.toSpacedHexFromByteRangeInclusive(raw, loInclusive, hiInclusive),
                    "hex_cross_row_clip");
        } else {
            ClipboardUiHelper.copyPlain(ctx,
                    HexDumpFormatter.toAsciiGlyphsFromByteRangeInclusive(raw, loInclusive, hiInclusive),
                    "hex_cross_row_ascii_clip");
        }
    }

    @Nullable
    HexDumpLineAdapter getHexDumpAdapter() {
        RecyclerView.Adapter<?> a = getAdapter();
        return a instanceof HexDumpLineAdapter ? (HexDumpLineAdapter) a : null;
    }

    /** 触点命中信息与全局字节索引。 */
    private static final class MappedHit {
        final boolean hexColumn;
        final int globalByte;

        MappedHit(boolean hexColumn, int globalByte) {
            this.hexColumn = hexColumn;
            this.globalByte = globalByte;
        }
    }
}
