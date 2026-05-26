package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.WindowCallbackWrapper;

/**
 * 在用户点击文本框以外的区域时收起软键盘，避免必须点返回键方可关闭输入法。
 */
public final class KeyboardDismissHelper {

    private KeyboardDismissHelper() {}

    /**
     * 在非 Activity 自带的窗口（如 Dialog）上挂载 {@link Window.Callback}，
     * 从而在点击输入框外侧时也能先于子 View 收起键盘。
     */
    public static void installOutsideTapHideIme(@NonNull Window window) {
        Window.Callback cb = window.getCallback();
        if (cb instanceof TouchOutsideHideImeCallback) {
            return;
        }
        if (cb == null) {
            return;
        }
        window.setCallback(new TouchOutsideHideImeCallback(cb, window));
    }

    /**
     * 供 {@link android.app.Activity#dispatchTouchEvent} 在最前面调用；
     * 仅处理 {@link MotionEvent#ACTION_DOWN}，不消费触摸事件。
     */
    public static void consumeOutsideTapHideIme(@NonNull Window window, @NonNull MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            return;
        }
        View focus = window.getCurrentFocus();
        if (!(focus instanceof EditText)) {
            return;
        }
        if (isInsideViewContents(focus, ev)) {
            return;
        }
        hideSoftInputAndClearFocus(focus);
    }

    private static boolean isInsideViewContents(@NonNull View v, @NonNull MotionEvent ev) {
        Rect bounds = new Rect();
        // 与可见区域对齐：滚动或未完全露出时仅用当前可见矩形判断
        v.getGlobalVisibleRect(bounds);
        int x = (int) ev.getRawX();
        int y = (int) ev.getRawY();
        return bounds.contains(x, y);
    }

    /** 收起输入法并取消焦点（焦点返还给窗口，后续点击走正常控件逻辑）。 */
    private static void hideSoftInputAndClearFocus(@NonNull View focus) {
        Context ctx = focus.getContext();
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        focus.clearFocus();
    }

    /** 对话框等独立 Window：在框架派发触摸链路最前收起 IME。 */
    private static final class TouchOutsideHideImeCallback extends WindowCallbackWrapper {

        @NonNull
        private final Window window;

        TouchOutsideHideImeCallback(@NonNull Window.Callback wrapped, @NonNull Window window) {
            super(wrapped);
            this.window = window;
        }

        @Override
        public boolean dispatchTouchEvent(@Nullable MotionEvent event) {
            if (event != null) {
                consumeOutsideTapHideIme(window, event);
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
