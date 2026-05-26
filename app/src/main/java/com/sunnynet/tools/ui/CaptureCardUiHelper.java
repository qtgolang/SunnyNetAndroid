package com.sunnynet.tools.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;

/**
 * 抓包列表卡片标题、摘要等文案样式。
 */
public final class CaptureCardUiHelper {

    private static final String[] HTTP_METHODS = {
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "CONNECT", "TRACE"
    };

    private CaptureCardUiHelper() {
    }

    /** HTTP 标题：方法加粗着色，URL 保持常规样式。 */
    public static void bindTitle(@NonNull TextView view, @NonNull CaptureRecord record) {
        String title = record.getTitle() != null ? record.getTitle() : "";
        if (!CaptureRecord.TYPE_HTTP.equals(record.getProtocol())) {
            view.setText(title);
            return;
        }
        int methodEnd = indexOfHttpMethodEnd(title);
        if (methodEnd <= 0) {
            view.setText(title);
            return;
        }
        SpannableString spannable = new SpannableString(title);
        int methodColor = view.getContext().getColor(R.color.sunny_primary_dark);
        spannable.setSpan(new ForegroundColorSpan(methodColor), 0, methodEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, methodEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(spannable);
    }

    private static int indexOfHttpMethodEnd(@NonNull String title) {
        for (String method : HTTP_METHODS) {
            String prefix = method + " ";
            if (title.startsWith(prefix)) {
                return method.length();
            }
        }
        int space = title.indexOf(' ');
        return space > 0 ? space : -1;
    }
}
