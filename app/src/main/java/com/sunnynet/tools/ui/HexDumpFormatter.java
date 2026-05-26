package com.sunnynet.tools.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.sunnynet.tools.R;

/**
 * 十六进制经典三栏视图：偏移（次强调色）、Hex（主题主色）、ASCII/文本列（表面主字色）。
 * Hex 栏内前导/与 ANSI 间隙由 {@link #HEX_PANE_LEADING_CHAR_COUNT}、{@link #HEX_PANE_TRAILING_GAP_CHAR_COUNT} 控制，
 * 与 {@link HexDumpRecyclerView} 字节命中换算、选区 Overlay 保持一致。
 * {@link #formatTriplet} 将偏移、Hex 字节区、文本列拆成三段，便于偏移不可选且 Hex 拷贝不含 offset。
 */
public final class HexDumpFormatter {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /** 动态列宽下限（过窄可读性）。 */
    public static final int MIN_BYTES_PER_ROW = 4;

    /** 动态列宽上限（避免单列过长失去「按宽度换列」意义）。 */
    public static final int MAX_BYTES_PER_ROW = 56;

    /** Hex 栏前导等宽空格个数（与原 4 改为 2，收窄 offset–Hex 留白）；拖动命中须与此一致 */
    public static final int HEX_PANE_LEADING_CHAR_COUNT = 2;
    /** Hex 栏末尾与 ANSI 栏之间的等宽空格个数（原 2 改为 1） */
    public static final int HEX_PANE_TRAILING_GAP_CHAR_COUNT = 1;

    private HexDumpFormatter() {
    }

    /**
     * 三列 Spannable：偏移列 · Hex（含行前沟槽空格与末尾间隔）· 文本列，与单列 {@link #format} 逐行对齐。
     */
    public static final class TripletHexDump {
        @NonNull
        public final CharSequence offsetColumn;
        @NonNull
        public final CharSequence hexBytesColumn;
        @NonNull
        public final CharSequence asciiColumn;

        TripletHexDump(@NonNull CharSequence offsetColumn, @NonNull CharSequence hexBytesColumn,
                       @NonNull CharSequence asciiColumn) {
            this.offsetColumn = offsetColumn;
            this.hexBytesColumn = hexBytesColumn;
            this.asciiColumn = asciiColumn;
        }
    }

    /**
     * 使用当前主题语义色格式化；占位与空已由调用方处理。
     *
     * @param bytesPerRow 单行展示的字节个数（一般由 {@link #computeBytesPerRow} 得出）
     */
    @NonNull
    public static CharSequence format(@NonNull Context context, @NonNull byte[] raw, int bytesPerRow) {
        if (raw.length == 0) {
            return "";
        }
        int[] palette = resolveThemePalette(context);
        return format(raw, palette[0], palette[1], palette[2], palette[3], bytesPerRow);
    }

    /**
     * 使用指定调色板；Hex 整块（含字节间空格）使用 {@code colorHex}，
     * 沟槽/outline 间隔段使用 {@code colorGutter}（避免细粒度 Span 导致大正文卡顿）。
     */
    @NonNull
    public static CharSequence format(@NonNull byte[] raw, @ColorInt int colorOffset,
                                      @ColorInt int colorHex, @ColorInt int colorGutter,
                                      @ColorInt int colorAscii, int bytesPerRow) {
        if (raw.length == 0) {
            return "";
        }
        int cols = clampBytesPerRow(bytesPerRow);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int rowStart = 0; rowStart < raw.length; rowStart += cols) {
            appendOffsetOnlySegments(ssb, rowStart, colorOffset);
            appendHexBytesPaneSegments(ssb, raw, rowStart, cols, colorHex, colorGutter);
            appendAsciiColumnSegments(ssb, raw, rowStart, cols, colorAscii);
            if (needsRowNewline(raw, rowStart, cols)) {
                ssb.append('\n');
            }
        }
        return ssb;
    }

    /**
     * 三列表检视：偏移 / Hex（可选中复制且不含 offset）/ 文本列。
     */
    @NonNull
    public static TripletHexDump formatTriplet(@NonNull Context context, @NonNull byte[] raw,
                                               int bytesPerRow) {
        if (raw.length == 0) {
            return new TripletHexDump("", "", "");
        }
        int[] palette = resolveThemePalette(context);
        return formatTriplet(raw, palette[0], palette[1], palette[2], palette[3], bytesPerRow);
    }

    @NonNull
    public static TripletHexDump formatTriplet(@NonNull byte[] raw, @ColorInt int colorOffset,
                                              @ColorInt int colorHex, @ColorInt int colorGutter,
                                              @ColorInt int colorAscii, int bytesPerRow) {
        if (raw.length == 0) {
            return new TripletHexDump("", "", "");
        }
        int cols = clampBytesPerRow(bytesPerRow);
        SpannableStringBuilder off = new SpannableStringBuilder();
        SpannableStringBuilder hexCol = new SpannableStringBuilder();
        SpannableStringBuilder ascii = new SpannableStringBuilder();
        for (int rowStart = 0; rowStart < raw.length; rowStart += cols) {
            appendOffsetOnlySegments(off, rowStart, colorOffset);
            appendHexBytesPaneSegments(hexCol, raw, rowStart, cols, colorHex, colorGutter);
            appendAsciiColumnSegments(ascii, raw, rowStart, cols, colorAscii);
            if (needsRowNewline(raw, rowStart, cols)) {
                off.append('\n');
                hexCol.append('\n');
                ascii.append('\n');
            }
        }
        return new TripletHexDump(off, hexCol, ascii);
    }

    /** 将每行字节数限制在合法区间。 */
    public static int clampBytesPerRow(int requested) {
        if (requested < MIN_BYTES_PER_ROW) {
            return MIN_BYTES_PER_ROW;
        }
        if (requested > MAX_BYTES_PER_ROW) {
            return MAX_BYTES_PER_ROW;
        }
        return requested;
    }

    /**
     * 单行在「等宽 + 本文本属性」假设下的近似字符个数（不含换行）。
     * Hex 区各字节之间仅用单空格，避免因「每 8 字节多加缝」在动态列数下出现某处间隙变宽。
     */
    public static int lineMonoCharacterCount(int cols) {
        cols = clampBytesPerRow(cols);
        return offsetColumnMonoCharacters() + hexBytesPaneMonoCharacterCount(cols)
                + asciiColumnMonoCharacterCount(cols);
    }

    /**
     * 偏移单列占用的等宽字符数（仅为 8 位地址，不含与 Hex 栏之间的留白；留白在 {@link #computeBytesPerRowTriplet} 中用 dp 粗略扣减）。
     */
    public static int offsetColumnMonoCharacters() {
        return 8;
    }

    /**
     * 去掉偏移后，单行 Hex 沟槽+字节+与文本列分隔 + 文本列所占等宽字符数。
     */
    public static int lineMonoTailCharacterCount(int cols) {
        cols = clampBytesPerRow(cols);
        return lineMonoCharacterCount(cols) - offsetColumnMonoCharacters();
    }

    /**
     * Hex 字节栏单行等宽字符数（与 {@link #appendHexBytesPaneSegments} 每行正文一致）。
     */
    public static int hexBytesPaneMonoCharacterCount(int cols) {
        cols = clampBytesPerRow(cols);
        return HEX_PANE_LEADING_CHAR_COUNT + cols * 3 + HEX_PANE_TRAILING_GAP_CHAR_COUNT;
    }

    /** 文本列单行字符数（等于当前每行字节数）。 */
    public static int asciiColumnMonoCharacterCount(int cols) {
        return clampBytesPerRow(cols);
    }

    /**
     * 三列表并排时换算每行字节数：在总像素宽中预留「偏移栏 + 列间留白」近似值，再给 Hex+文本列合体使用。
     */
    public static int computeBytesPerRowTriplet(@NonNull TextView textView, int viewportWidthPx) {
        /* 宿主宽未就绪时勿用整屏宽度，否则会低估「一行」占位、列数偏多，三栏总长超出宿主（常见于 AlertDialog Hex）。 */
        if (viewportWidthPx <= 0) {
            return MIN_BYTES_PER_ROW;
        }
        TextPaint paint = new TextPaint(textView.getPaint());
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(textView.getTextSize());
        float charWidth = paint.measureText("m");
        if (charWidth <= 0f) {
            charWidth = textView.getTextSize() / 2f;
        }
        float density = textView.getResources().getDisplayMetrics().density;
        float slack = 16f * density;
        /* 偏移 8 字宽 + ~20dp（列间距、控件 padding 与安全余量），与布局中分栏留白同量级 */
        float reservedLeading = offsetColumnMonoCharacters() * charWidth + 20f * density;
        float availableTail = viewportWidthPx - slack - reservedLeading;
        if (availableTail <= 0f) {
            return clampBytesPerRow(MIN_BYTES_PER_ROW);
        }
        for (int cols = MAX_BYTES_PER_ROW; cols >= MIN_BYTES_PER_ROW; cols--) {
            float linePx = charWidth * lineMonoTailCharacterCount(cols);
            if (linePx <= availableTail) {
                return cols;
            }
        }
        return MIN_BYTES_PER_ROW;
    }

    /**
     * 依据横向像素宽度与 {@code textView} 的字号画笔，换算每行可容纳的字节数。
     *
     * @param viewportWidthPx 一般为正文宿主（占满卡片/弹窗可视宽）减去内边距后的像素宽度
     */
    public static int computeBytesPerRow(@NonNull TextView textView, int viewportWidthPx) {
        if (viewportWidthPx <= 0) {
            viewportWidthPx = textView.getResources().getDisplayMetrics().widthPixels;
        }
        TextPaint paint = new TextPaint(textView.getPaint());
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(textView.getTextSize());
        float charWidth = paint.measureText("m");
        if (charWidth <= 0f) {
            charWidth = textView.getTextSize() / 2f;
        }
        float density = textView.getResources().getDisplayMetrics().density;
        float slack = 16f * density;
        float available = viewportWidthPx - slack;
        if (available <= 0f) {
            return clampBytesPerRow(MIN_BYTES_PER_ROW);
        }
        for (int cols = MAX_BYTES_PER_ROW; cols >= MIN_BYTES_PER_ROW; cols--) {
            float linePx = charWidth * lineMonoCharacterCount(cols);
            if (linePx <= available) {
                return cols;
            }
        }
        return MIN_BYTES_PER_ROW;
    }

    @NonNull
    public static int[] resolveThemePalette(@NonNull Context context) {
        int fbVar = ContextCompat.getColor(context, R.color.sunny_on_surface_variant);
        int fbOn = ContextCompat.getColor(context, R.color.sunny_on_surface);
        int fbPri = ContextCompat.getColor(context, R.color.sunny_primary);
        int fbOut = ContextCompat.getColor(context, R.color.sunny_outline);

        int cOff = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, fbVar);
        int cHex = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, fbPri);
        int cAscii = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, fbOn);
        int cGutter = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, fbOut);
        return new int[]{cOff, cHex, cGutter, cAscii};
    }

    /**
     * 与检视右侧「文本列」一致：可打印 ASCII 用原字符，否则为「.」；整块无换行。
     */
    @NonNull
    public static String toAsciiColumnPlain(@NonNull byte[] raw) {
        if (raw.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length);
        for (byte b : raw) {
            sb.append(asciiGlyph(b));
        }
        return sb.toString();
    }

    /**
     * 与界面三栏检视结构相同的纯文本（偏移 + Hex + 文本列），{@code bytesPerRow} 应与屏上换列一致。
     */
    @NonNull
    public static String toPlainTextDump(@NonNull byte[] raw, int bytesPerRow) {
        if (raw.length == 0) {
            return "";
        }
        int cols = clampBytesPerRow(bytesPerRow);
        StringBuilder sb = new StringBuilder(raw.length * 4);
        for (int rowStart = 0; rowStart < raw.length; rowStart += cols) {
            appendPlainRow(sb, raw, rowStart, cols);
        }
        return sb.toString();
    }

    private static void appendPlainRow(@NonNull StringBuilder sb, @NonNull byte[] raw, int rowStart,
                                       int cols) {
        appendOffset8(sb, rowStart);
        for (int s = 0; s < HEX_PANE_LEADING_CHAR_COUNT; s++) {
            sb.append(' ');
        }
        int rowLen = Math.min(cols, raw.length - rowStart);
        for (int i = 0; i < cols; i++) {
            if (i < rowLen) {
                appendHexByte(sb, raw[rowStart + i]);
                sb.append(' ');
            } else {
                sb.append("   ");
            }
        }
        for (int s = 0; s < HEX_PANE_TRAILING_GAP_CHAR_COUNT; s++) {
            sb.append(' ');
        }
        for (int i = 0; i < cols; i++) {
            char ch = i < rowLen ? asciiGlyph(raw[rowStart + i]) : ' ';
            sb.append(ch);
        }
        if (rowStart + rowLen < raw.length) {
            sb.append('\n');
        }
    }

    /** 当前行是否与下一行之间存在换行。 */
    private static boolean needsRowNewline(@NonNull byte[] raw, int rowStart, int cols) {
        int rowLen = Math.min(cols, raw.length - rowStart);
        return rowStart + rowLen < raw.length;
    }

    /**
     * 绑定单行三栏 Spannable；可选 {@link HexDumpCrossRowSnapshot} 在 Hex 栏或文本列上叠选区底色。
     */
    public static void bindTripletRowSpan(@NonNull byte[] raw, int rowStart, int cols,
                                         @ColorInt int colorOffset, @ColorInt int colorHex,
                                         @ColorInt int colorGutter, @ColorInt int colorAscii,
                                         @NonNull TextView offsetTv, @NonNull TextView hexBytesTv,
                                         @NonNull TextView asciiTv,
                                         @Nullable HexDumpCrossRowSnapshot crossRow,
                                         @ColorInt int crossRowBgArgb,
                                         @ColorInt int crossRowHexFgArgb,
                                         @ColorInt int crossRowAsciiFgArgb) {
        SpannableStringBuilder o = new SpannableStringBuilder();
        SpannableStringBuilder h = new SpannableStringBuilder();
        SpannableStringBuilder a = new SpannableStringBuilder();
        appendOffsetOnlySegments(o, rowStart, colorOffset);
        appendHexBytesPaneSegments(h, raw, rowStart, cols, colorHex, colorGutter);
        appendAsciiColumnSegments(a, raw, rowStart, cols, colorAscii);
        if (crossRow != null && raw.length > 0) {
            applyCrossRowHighlight(h, a, raw.length, rowStart, cols, crossRow,
                    crossRowBgArgb, crossRowHexFgArgb, crossRowAsciiFgArgb);
        }
        offsetTv.setText(o);
        hexBytesTv.setText(h);
        asciiTv.setText(a);
    }

    /**
     * 与单行 Hex 检视一致的空格分隔大写 Hex，区间为全局字节下标（含端点）。
     */
    @NonNull
    public static String toSpacedHexFromByteRangeInclusive(@NonNull byte[] raw,
                                                           int loInclusive, int hiInclusive) {
        if (raw.length == 0 || loInclusive > hiInclusive) {
            return "";
        }
        int lo = Math.max(0, loInclusive);
        int hi = Math.min(raw.length - 1, hiInclusive);
        if (lo > hi) {
            return "";
        }
        StringBuilder sb = new StringBuilder((hi - lo + 1) * 3);
        for (int i = lo; i <= hi; i++) {
            if (i > lo) {
                sb.append(' ');
            }
            appendHexByte(sb, raw[i]);
        }
        return sb.toString();
    }

    /**
     * 与右侧文本列.glyph 一致的字节切片（含端点），常用于跨行复制的剪贴板内容。
     */
    @NonNull
    public static String toAsciiGlyphsFromByteRangeInclusive(@NonNull byte[] raw,
                                                               int loInclusive, int hiInclusive) {
        if (raw.length == 0 || loInclusive > hiInclusive) {
            return "";
        }
        int lo = Math.max(0, loInclusive);
        int hi = Math.min(raw.length - 1, hiInclusive);
        if (lo > hi) {
            return "";
        }
        StringBuilder sb = new StringBuilder(hi - lo + 1);
        for (int i = lo; i <= hi; i++) {
            sb.append(asciiGlyph(raw[i]));
        }
        return sb.toString();
    }

    private static void applyCrossRowHighlight(@NonNull SpannableStringBuilder h,
                                              @NonNull SpannableStringBuilder a,
                                              int payloadLen,
                                              int rowStart, int cols,
                                              @NonNull HexDumpCrossRowSnapshot sel,
                                              @ColorInt int bg,
                                              @ColorInt int hexFg,
                                              @ColorInt int asciiFg) {
        int rowLen = Math.min(cols, payloadLen - rowStart);
        if (rowLen <= 0) {
            return;
        }
        int rowTail = rowStart + rowLen - 1;
        int gb0 = Math.max(sel.loInclusive, rowStart);
        int gb1 = Math.min(sel.hiInclusive, rowTail);
        if (gb0 > gb1) {
            return;
        }
        int lb0 = gb0 - rowStart;
        int lb1 = gb1 - rowStart;
        /* 双栏同步：无论起选手势在 Hex 还是 ANSI，同字节区间两栏均高亮 */
        int lead = HEX_PANE_LEADING_CHAR_COUNT;
        int hStart = lead + lb0 * 3;
        int hEnd = lead + (lb1 + 1) * 3;
        int hLen = h.length();
        if (hStart >= 0 && hEnd <= hLen) {
            h.setSpan(new BackgroundColorSpan(bg), hStart, hEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.setSpan(new ForegroundColorSpan(hexFg), hStart, hEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int aStart = lb0;
        int aEnd = lb1 + 1;
        int aLen = a.length();
        if (aStart >= 0 && aEnd <= aLen) {
            a.setSpan(new BackgroundColorSpan(bg), aStart, aEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            a.setSpan(new ForegroundColorSpan(asciiFg), aStart, aEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** 与 {@link #HEX_PANE_LEADING_CHAR_COUNT} / TRAILING 一致的连续空格，供 Spannable 沟槽上色。 */
    @NonNull
    private static String monoSpaceRun(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder b = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            b.append(' ');
        }
        return b.toString();
    }

    /** 偏移列单行：8 位地址；不含换行。 */
    private static void appendOffsetOnlySegments(@NonNull SpannableStringBuilder ssb, int rowStart,
                                               @ColorInt int colorOffset) {
        appendSpan(ssb, offset8String(rowStart), colorOffset);
    }

    /**
     * Hex 栏单行：行前沟槽空格、各字节 Hex、末行占位、与文本列之间间隔；不含偏移与换行。
     */
    private static void appendHexBytesPaneSegments(@NonNull SpannableStringBuilder ssb, @NonNull byte[] raw,
                                                   int rowStart, int cols, @ColorInt int colorHex,
                                                   @ColorInt int colorGutter) {
        int rowLen = Math.min(cols, raw.length - rowStart);
        appendSpan(ssb, monoSpaceRun(HEX_PANE_LEADING_CHAR_COUNT), colorGutter);
        StringBuilder hexRun = new StringBuilder(rowLen * 3);
        for (int i = 0; i < rowLen; i++) {
            appendHexByte(hexRun, raw[rowStart + i]);
            hexRun.append(' ');
        }
        if (hexRun.length() > 0) {
            appendSpan(ssb, hexRun.toString(), colorHex);
        }
        if (rowLen < cols) {
            int padCols = cols - rowLen;
            StringBuilder pad = new StringBuilder(padCols * 3);
            for (int p = 0; p < padCols; p++) {
                pad.append("   ");
            }
            appendSpan(ssb, pad.toString(), colorGutter);
        }
        appendSpan(ssb, monoSpaceRun(HEX_PANE_TRAILING_GAP_CHAR_COUNT), colorGutter);
    }

    /**
     * 右栏单行：每字节对应的可打印 ASCII 形或「.」；不含换行。
     */
    private static void appendAsciiColumnSegments(@NonNull SpannableStringBuilder ssb, @NonNull byte[] raw,
                                                  int rowStart, int cols, @ColorInt int colorAscii) {
        int rowLen = Math.min(cols, raw.length - rowStart);
        StringBuilder asciiRun = new StringBuilder(cols);
        for (int i = 0; i < cols; i++) {
            asciiRun.append(i < rowLen ? asciiGlyph(raw[rowStart + i]) : ' ');
        }
        appendSpan(ssb, asciiRun.toString(), colorAscii);
    }

    /** 8 位十六进制偏移（大写），无「0x」前缀。 */
    @NonNull
    private static String offset8String(int address) {
        char[] buf = new char[8];
        for (int i = 7, a = address; i >= 0; i--) {
            buf[i] = HEX_DIGITS[a & 0xF];
            a >>>= 4;
        }
        return new String(buf);
    }

    private static void appendOffset8(@NonNull StringBuilder sb, int address) {
        for (int sh = 28; sh >= 0; sh -= 4) {
            sb.append(HEX_DIGITS[(address >>> sh) & 0xF]);
        }
    }

    private static void appendHexByte(@NonNull StringBuilder sb, byte b) {
        int v = b & 0xFF;
        sb.append(HEX_DIGITS[v >>> 4]).append(HEX_DIGITS[v & 15]);
    }

    private static void appendSpan(@NonNull SpannableStringBuilder ssb, @NonNull String chunk,
                                   @ColorInt int color) {
        int start = ssb.length();
        ssb.append(chunk);
        ssb.setSpan(new ForegroundColorSpan(color), start, ssb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static char asciiGlyph(byte b) {
        int v = b & 0xFF;
        return (v >= 0x20 && v <= 0x7E) ? (char) v : '.';
    }
}
