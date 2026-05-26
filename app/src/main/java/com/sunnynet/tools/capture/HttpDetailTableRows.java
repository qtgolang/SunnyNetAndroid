package com.sunnynet.tools.capture;

import android.content.Context;

import androidx.annotation.NonNull;

import com.sunnynet.tools.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 HTTP 请求/响应摘要行与协议头文本转为「总览」同款 {@link HttpOverviewBuilder.Row} 列表，供表格 RecyclerView 展示。
 */
public final class HttpDetailTableRows {

    private HttpDetailTableRows() {
    }

    /**
     * 请求 Tab「请求信息」表格：方法、请求地址、协议（不展示「请求行」，与列表/总览字段区分）。
     */
    @NonNull
    public static List<HttpOverviewBuilder.Row> buildRequestSummary(@NonNull Context ctx,
                                                                    @NonNull CaptureRecord record) {
        List<HttpOverviewBuilder.Row> rows = new ArrayList<>();
        String[] mu = HttpDetailSections.parseMethodAndUrl(record);
        String proto = record.getRequestHttpProto();
        if (proto == null || proto.isEmpty()) {
            proto = "HTTP/1.1";
        }
        rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_method),
                dashIfEmpty(mu[0])));
        rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_url),
                dashIfEmpty(mu[1])));
        rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_protocol), proto));
        return rows;
    }

    /**
     * 响应 Tab「响应信息」表格：状态行（可点复制）。
     */
    @NonNull
    public static List<HttpOverviewBuilder.Row> buildResponseSummary(@NonNull Context ctx,
                                                                     @NonNull CaptureRecord record) {
        List<HttpOverviewBuilder.Row> rows = new ArrayList<>();
        String line = HttpDetailSections.buildResponseStatusLineForTab(record);
        String display = line.isEmpty() ? ctx.getString(R.string.detail_empty) : line;
        rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_status_line),
                display, line.isEmpty() ? null : line));
        return rows;
    }

    /**
     * 将协议头块解析为「名称 | 值」多行；空块时一行占位。
     */
    @NonNull
    public static List<HttpOverviewBuilder.Row> buildHeaderRows(@NonNull Context ctx,
                                                                @NonNull String headerBlock) {
        List<HttpOverviewBuilder.Row> rows = new ArrayList<>();
        String trimmed = headerBlock.trim();
        if (trimmed.isEmpty()) {
            rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_no_headers),
                    ctx.getString(R.string.detail_empty)));
            return rows;
        }
        for (String line : trimmed.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            int c = t.indexOf(':');
            if (c > 0) {
                String name = t.substring(0, c).trim();
                String val = t.substring(c + 1).trim();
                rows.add(new HttpOverviewBuilder.Row(name, val, val));
            } else {
                rows.add(new HttpOverviewBuilder.Row("", t, t));
            }
        }
        if (rows.isEmpty()) {
            rows.add(new HttpOverviewBuilder.Row(ctx.getString(R.string.detail_table_no_headers),
                    ctx.getString(R.string.detail_empty)));
        }
        return rows;
    }

    @NonNull
    private static String dashIfEmpty(@NonNull String s) {
        return s.isEmpty() ? "—" : s;
    }
}
