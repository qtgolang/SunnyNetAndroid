package com.sunnynet.tools.ui;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sunnynet.tools.R;

/**
 * Hex 三栏单行：RecyclerView 子项宽度、三栏字号对齐，以及绑定后实测列宽。
 */
final class HexDumpRowLayoutHelper {

    private HexDumpRowLayoutHelper() {
    }

    /**
     * 垂直列表默认给 item {@code MATCH_PARENT} 宽，会撑满宿主并在 ANSI 右侧留大块空白；
     * 三栏为固定像素宽时必须改为 {@code WRAP_CONTENT}。
     */
    static void forceRowWrapContentWidth(@NonNull View row) {
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp instanceof RecyclerView.LayoutParams) {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            row.setLayoutParams(lp);
        }
    }

    /**
     * 行宽与列表内宽对齐，保证最右侧 ANSI 列落在 {@link RecyclerView} 可触摸区域内。
     */
    static void forceRowMatchHostInnerWidth(@NonNull View row, int hostInnerWidthPx) {
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (!(lp instanceof RecyclerView.LayoutParams) || hostInnerWidthPx <= 0) {
            return;
        }
        lp.width = hostInnerWidthPx;
        row.setLayoutParams(lp);
    }

    /** 与 {@link HexDumpLineAdapter.LineHolder} 一致：三栏等宽字号、等宽字体。 */
    static void prepareTripletRowTypography(@NonNull View rowRoot) {
        TextView ot = rowRoot.findViewById(R.id.hex_line_offset);
        TextView ht = rowRoot.findViewById(R.id.hex_line_bytes);
        TextView at = rowRoot.findViewById(R.id.hex_line_ascii);
        if (ot == null || ht == null || at == null) {
            return;
        }
        Typeface mono = Typeface.MONOSPACE;
        ot.setTypeface(mono);
        ht.setTypeface(mono);
        at.setTypeface(mono);
        float sizePx = ot.getTextSize();
        if (sizePx > 0f) {
            ht.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
            at.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        }
    }

    /** 在已 {@link HexDumpFormatter#bindTripletRowSpan} 的前提下测单列 intrinsic 宽（含 padding）。 */
    static int measureColumnIntrinsicWidthPx(@NonNull TextView tv) {
        ViewGroup.LayoutParams lp = tv.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            tv.setLayoutParams(lp);
        }
        int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        tv.measure(wSpec, hSpec);
        return Math.max(1, tv.getMeasuredWidth());
    }
}
