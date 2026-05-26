package com.sunnynet.tools.ui;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.sunnynet.tools.R;

/**
 * HTTP 详情「请求/响应」Tab 内协议头区块：标题行切换展开收起，箭头与无障碍说明同步更新。
 */
public final class DetailProtocolHeadersSectionBinder {

    private DetailProtocolHeadersSectionBinder() {
    }

    /**
     * 默认收起；点击标题整行切换 {@code headersContent}（一般为头部 {@link androidx.recyclerview.widget.RecyclerView}）显隐。
     */
    public static void bindDefaultCollapsed(@NonNull View headerRow, @NonNull ImageView expandIcon,
                                            @NonNull View headersContent) {
        headersContent.setVisibility(View.GONE);
        applyCollapsedUi(headerRow.getContext(), expandIcon);
        headerRow.setOnClickListener(v -> toggle(headerRow.getContext(), expandIcon, headersContent));
    }

    private static void toggle(@NonNull Context ctx,
                               @NonNull ImageView expandIcon, @NonNull View headersContent) {
        boolean wasVisible = headersContent.getVisibility() == View.VISIBLE;
        if (wasVisible) {
            headersContent.setVisibility(View.GONE);
            applyCollapsedUi(ctx, expandIcon);
        } else {
            headersContent.setVisibility(View.VISIBLE);
            expandIcon.setImageResource(R.drawable.ic_expand_less);
            expandIcon.setContentDescription(ctx.getString(R.string.detail_headers_content_desc_expanded));
        }
    }

    private static void applyCollapsedUi(@NonNull Context ctx, @NonNull ImageView expandIcon) {
        expandIcon.setImageResource(R.drawable.ic_expand_more);
        expandIcon.setContentDescription(ctx.getString(R.string.detail_headers_content_desc_collapsed));
    }
}
