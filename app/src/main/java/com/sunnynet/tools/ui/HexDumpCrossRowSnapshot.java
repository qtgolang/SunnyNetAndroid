package com.sunnynet.tools.ui;

import androidx.annotation.Nullable;

/**
 * Hex/文本列「跨行字节选区」的一帧快照，供 {@link HexDumpFormatter#bindTripletRowSpan} 在高亮时只读使用。
 */
public final class HexDumpCrossRowSnapshot {

    /** 起选手势所在列（true=Hex），仅用于复制内容与拖柄锚点；高亮始终双栏同步。 */
    public final boolean hexColumn;
    /** 含端点的全局字节下标。 */
    public final int loInclusive;
    public final int hiInclusive;

    public HexDumpCrossRowSnapshot(boolean hexColumn, int loInclusive, int hiInclusive) {
        this.hexColumn = hexColumn;
        this.loInclusive = loInclusive;
        this.hiInclusive = hiInclusive;
    }

    /**
     * @return 非空且区间合法时返回快照，否则 null（无高亮）
     */
    @Nullable
    public static HexDumpCrossRowSnapshot maybeFrom(boolean active, boolean hexColumn,
                                                     int loInclusive, int hiInclusive,
                                                     int payloadLen) {
        if (!active || payloadLen <= 0) {
            return null;
        }
        int lo = clamp(Math.min(loInclusive, hiInclusive), 0, payloadLen - 1);
        int hi = clamp(Math.max(loInclusive, hiInclusive), 0, payloadLen - 1);
        if (lo > hi) {
            return null;
        }
        return new HexDumpCrossRowSnapshot(hexColumn, lo, hi);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (Math.min(v, max));
    }
}
