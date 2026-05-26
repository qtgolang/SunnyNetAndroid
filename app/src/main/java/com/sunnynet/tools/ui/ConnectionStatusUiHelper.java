package com.sunnynet.tools.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 会话连接状态在列表中的颜色高亮与左侧强调条；HTTP 单行在具备响应体字节时附加「/ N Bytes」。
 */
public final class ConnectionStatusUiHelper {

    private ConnectionStatusUiHelper() {
    }

    public static void applyListItem(@NonNull TextView summaryView, @NonNull View accentView,
                                     @NonNull CaptureRecord record) {
        if (!record.isStreamSession()) {
            summaryView.setText(buildHttpSummary(summaryView, record));
            summaryView.setTextColor(summaryView.getContext().getColor(R.color.sunny_on_surface_variant));
            accentView.setBackgroundResource(R.drawable.bg_item_accent);
            return;
        }
        summaryView.setText(buildHighlightedSummary(summaryView, record));
        accentView.setBackgroundResource(accentDrawableForState(record.getConnectionState()));
    }

    /**
     * HTTP 摘要：仍为「HTTP 203」着色规则；若已记录响应体字节数则拼接「/ N 字节」。
     */
    @NonNull
    private static CharSequence buildHttpSummary(@NonNull TextView summaryView,
                                                  @NonNull CaptureRecord record) {
        Context ctx = summaryView.getContext();
        String summary = record.getSummary() != null ? record.getSummary() : "";
        boolean hasBytesSize = CaptureRecord.TYPE_HTTP.equals(record.getProtocol())
                && record.getResponseBodyBytes() >= 0;
        String display = hasBytesSize && summary.startsWith("HTTP ")
                ? ctx.getString(R.string.capture_summary_http_with_bytes, summary, record.getResponseBodyBytes())
                : summary;

        if (!display.startsWith("HTTP ")) {
            return display;
        }
        int codeStart = 5;
        int codeEnd = display.length();
        for (int i = codeStart; i < display.length(); i++) {
            char c = display.charAt(i);
            if (!Character.isDigit(c)) {
                codeEnd = i;
                break;
            }
        }
        if (codeEnd <= codeStart) {
            return display;
        }
        int code;
        try {
            code = Integer.parseInt(display.substring(codeStart, codeEnd));
        } catch (NumberFormatException e) {
            return display;
        }
        int color;
        if (code >= 200 && code < 300) {
            color = ctx.getColor(R.color.status_connected);
        } else if (code >= 400) {
            color = ctx.getColor(R.color.status_disconnected);
        } else if (code >= 300) {
            color = ctx.getColor(R.color.status_connecting);
        } else {
            color = ctx.getColor(R.color.sunny_on_surface_variant);
        }
        SpannableString spannable = new SpannableString(display);
        spannable.setSpan(new ForegroundColorSpan(color), 0, display.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, display.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @NonNull
    private static CharSequence buildHighlightedSummary(@NonNull TextView summaryView,
                                                          @NonNull CaptureRecord record) {
        String summary = record.getSummary() != null ? record.getSummary() : "";
        String stateLabel = CaptureRecord.stateLabel(record.getConnectionState());
        int defaultColor = summaryView.getContext().getColor(R.color.sunny_on_surface_variant);
        int stateColor = stateColor(summaryView, record.getConnectionState());

        SpannableString spannable = new SpannableString(summary);
        int start = summary.indexOf(stateLabel);
        if (start >= 0) {
            int end = start + stateLabel.length();
            spannable.setSpan(new ForegroundColorSpan(stateColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        summaryView.setTextColor(defaultColor);
        return spannable;
    }

    @ColorInt
    private static int stateColor(@NonNull TextView view, @NonNull String state) {
        if (CaptureRecord.STATE_CONNECTED.equals(state)) {
            return view.getContext().getColor(R.color.status_connected);
        }
        if (CaptureRecord.STATE_DISCONNECTED.equals(state)) {
            return view.getContext().getColor(R.color.status_disconnected);
        }
        if (CaptureRecord.STATE_CONNECTING.equals(state)) {
            return view.getContext().getColor(R.color.status_connecting);
        }
        return view.getContext().getColor(R.color.sunny_on_surface_variant);
    }

    /** 详情概览 Tab：高亮「状态」行 */
    @NonNull
    public static CharSequence buildStreamOverview(@NonNull Context context, @NonNull CaptureRecord record) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String stateLabel = CaptureRecord.stateLabel(record.getConnectionState());
        String overview = "协议: " + record.getProtocol()
                + "\n连接: " + record.getTitle()
                + "\n状态: " + stateLabel
                + "\n流数量: " + record.getStreamCount()
                + "\n会话开始: " + fmt.format(new Date(record.getTimestampMs()));
        SpannableString spannable = new SpannableString(overview);
        int start = overview.indexOf(stateLabel);
        if (start >= 0) {
            int color = stateColor(context, record.getConnectionState());
            int end = start + stateLabel.length();
            spannable.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    @ColorInt
    private static int stateColor(@NonNull Context context, @NonNull String state) {
        if (CaptureRecord.STATE_CONNECTED.equals(state)) {
            return context.getColor(R.color.status_connected);
        }
        if (CaptureRecord.STATE_DISCONNECTED.equals(state)) {
            return context.getColor(R.color.status_disconnected);
        }
        if (CaptureRecord.STATE_CONNECTING.equals(state)) {
            return context.getColor(R.color.status_connecting);
        }
        return context.getColor(R.color.sunny_on_surface_variant);
    }

    private static int accentDrawableForState(@NonNull String state) {
        if (CaptureRecord.STATE_CONNECTED.equals(state)) {
            return R.drawable.bg_item_accent_connected;
        }
        if (CaptureRecord.STATE_DISCONNECTED.equals(state)) {
            return R.drawable.bg_item_accent_disconnected;
        }
        if (CaptureRecord.STATE_CONNECTING.equals(state)) {
            return R.drawable.bg_item_accent_connecting;
        }
        return R.drawable.bg_item_accent;
    }
}
