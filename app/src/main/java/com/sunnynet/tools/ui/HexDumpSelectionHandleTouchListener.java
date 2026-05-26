package com.sunnynet.tools.ui;

import android.view.MotionEvent;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 命中选区两端橙色拖柄时拦截触摸，调整选区起止字节；否则交给列表滚动与长按逻辑。
 */
final class HexDumpSelectionHandleTouchListener implements RecyclerView.OnItemTouchListener {

    private final HexDumpRecyclerView host;
    private int dragging = HexDumpRecyclerView.HIT_HANDLE_NONE;

    HexDumpSelectionHandleTouchListener(@NonNull HexDumpRecyclerView host) {
        this.host = host;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!(rv instanceof HexDumpRecyclerView)) {
            return false;
        }
        if (!host.isCrossRowSelectionActiveForOverlay()) {
            dragging = HexDumpRecyclerView.HIT_HANDLE_NONE;
            return false;
        }
        float x = e.getX();
        float y = e.getY();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int hit = host.hitTestSelectionHandle(x, y);
            if (hit == HexDumpRecyclerView.HIT_HANDLE_START || hit == HexDumpRecyclerView.HIT_HANDLE_END) {
                dragging = hit;
                host.setDraggingSelectionHandle(true);
                ViewParent p = host.getParent();
                if (p != null) {
                    p.requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            dragging = HexDumpRecyclerView.HIT_HANDLE_NONE;
            return false;
        }
        if (dragging != HexDumpRecyclerView.HIT_HANDLE_NONE
                && (action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL)) {
            return true;
        }
        return false;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (dragging == HexDumpRecyclerView.HIT_HANDLE_NONE) {
            return;
        }
        float rawX = e.getRawX();
        float rawY = e.getRawY();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            if (dragging == HexDumpRecyclerView.HIT_HANDLE_START) {
                host.moveSelectionStartHandleToFinger(e.getX(), e.getY(), rawX, rawY);
            } else {
                host.moveSelectionEndHandleToFinger(e.getX(), e.getY(), rawX, rawY);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            host.notifyCrossRowCopyAfterHandleRelease();
            dragging = HexDumpRecyclerView.HIT_HANDLE_NONE;
            host.setDraggingSelectionHandle(false);
            ViewParent p = host.getParent();
            if (p != null) {
                p.requestDisallowInterceptTouchEvent(false);
            }
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }
}
