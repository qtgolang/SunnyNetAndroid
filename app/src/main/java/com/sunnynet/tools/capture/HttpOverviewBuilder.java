package com.sunnynet.tools.capture;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.R;
import com.sunnynet.tools.ui.AppIconHelper;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 构建 HTTP 详情「概览」Tab 的键值行（表格展示）。
 */
public final class HttpOverviewBuilder {

    /** 概览表格一行：标签 + 展示值 + 可复制语义（{@link com.sunnynet.tools.ui.DetailKvTableAdapter} 中单点/长按）。 */
    public static final class Row {
        public final String label;
        public final String value;
        /** 单击「复制字段值」时优先使用该文本；为空则用 {@link #value}。*/
        @Nullable
        public final String copyText;

        public Row(@NonNull String label, @NonNull String value, @Nullable String copyText) {
            this.label = label;
            this.value = value;
            this.copyText = copyText;
        }

        public Row(@NonNull String label, @NonNull String value) {
            this(label, value, isCopyable(value) ? value : null);
        }

        private static boolean isCopyable(@NonNull String value) {
            return !value.isEmpty() && !"—".equals(value);
        }
    }

    private HttpOverviewBuilder() {
    }

    @NonNull
    public static List<Row> build(@NonNull Context context, @NonNull CaptureRecord record) {
        String detail = record.getDetail() != null ? record.getDetail() : "";
        String[] methodUrl = HttpDetailSections.parseMethodAndUrl(record);
        List<Row> rows = new ArrayList<>();

        rows.add(new Row("应用名称",
                AppIconHelper.resolveAppLabel(context, record),
                copyOrNull(AppIconHelper.resolveAppLabel(context, record))));
        rows.add(new Row("包名", formatPackageValue(context, record),
                copyOrNull(formatPackageRaw(record))));

        rows.add(new Row("请求方式", emptyToDash(methodUrl[0])));
        rows.add(new Row(context.getString(R.string.detail_table_url), emptyToDash(methodUrl[1])));
        rows.add(new Row("请求协议", emptyToDash(record.getRequestHttpProto(), "HTTP/1.1")));
        rows.add(new Row("响应协议", emptyToDash(record.getResponseHttpProto())));
        rows.add(new Row("响应状态码", formatStatusCode(record)));
        rows.add(new Row("提交数据长度", formatByteSize(resolveRequestBodyBytes(record, detail))));
        rows.add(new Row("响应数据长度", formatByteSize(resolveResponseBodyBytes(record, detail))));
        rows.add(new Row("请求时间", formatTimestamp(record.getTimestampMs())));
        rows.add(new Row("耗时", formatDuration(record)));
        return rows;
    }

    @NonNull
    private static String formatPackageValue(@NonNull Context context, @NonNull CaptureRecord record) {
        if (record.getPackageName() != null && !record.getPackageName().isEmpty()) {
            return record.getPackageName();
        }
        return "—";
    }

    @Nullable
    private static String formatPackageRaw(@NonNull CaptureRecord record) {
        if (record.getPackageName() == null || record.getPackageName().isEmpty()) {
            return null;
        }
        return record.getPackageName();
    }

    @Nullable
    private static String copyOrNull(@Nullable String text) {
        if (text == null || text.isEmpty() || "—".equals(text)) {
            return null;
        }
        return text;
    }

    @NonNull
    private static String formatStatusCode(@NonNull CaptureRecord record) {
        int code = record.getHttpStatusCode();
        if (code > 0) {
            return String.valueOf(code);
        }
        String summary = record.getSummary();
        if (summary != null && summary.startsWith("HTTP ")) {
            String part = summary.substring(5).trim();
            int space = part.indexOf(' ');
            return space >= 0 ? part.substring(0, space) : part;
        }
        if ("请求".equals(summary)) {
            return "—";
        }
        return emptyToDash(summary);
    }

    private static long resolveRequestBodyBytes(@NonNull CaptureRecord record, @NonNull String detail) {
        if (record.getRequestBodyBytes() >= 0) {
            return record.getRequestBodyBytes();
        }
        return utf8Length(HttpDetailSections.buildRequestBodyTab(detail));
    }

    private static long resolveResponseBodyBytes(@NonNull CaptureRecord record, @NonNull String detail) {
        if (record.getResponseBodyBytes() >= 0) {
            return record.getResponseBodyBytes();
        }
        return utf8Length(HttpDetailSections.buildResponseBodyTab(detail));
    }

    private static long utf8Length(@Nullable String text) {
        if (text == null || text.isEmpty() || isPlaceholder(text)) {
            return 0;
        }
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isPlaceholder(@NonNull String text) {
        return text.startsWith("（无") || text.startsWith("（读取");
    }

    @NonNull
    static String formatByteSize(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        if (bytes == 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    @NonNull
    private static String formatTimestamp(long timestampMs) {
        if (timestampMs <= 0) {
            return "—";
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return fmt.format(new Date(timestampMs));
    }

    @NonNull
    private static String formatDuration(@NonNull CaptureRecord record) {
        long responseAt = record.getResponseTimestampMs();
        long requestAt = record.getTimestampMs();
        if (responseAt <= 0 || requestAt <= 0 || responseAt < requestAt) {
            return "—";
        }
        return (responseAt - requestAt) + " ms";
    }

    @NonNull
    private static String emptyToDash(@Nullable String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    @NonNull
    private static String emptyToDash(@Nullable String value, @NonNull String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }
}
