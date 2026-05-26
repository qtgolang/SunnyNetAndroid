package com.sunnynet.tools.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureStreamEntry;
import com.sunnynet.tools.capture.StreamPayloadFormatter;

/**
 * 单条流的 UI 文案：卡片方向徽标与弹窗标题等。
 */
public final class StreamEntryUi {

    private StreamEntryUi() {
    }

    /**
     * 方向 + 本条负载语义字节数，例如「接收（共256字节）」；
     * 字节数口径与正文 Hex/字符串展示一致。
     */
    @NonNull
    public static String directionWithByteCount(@NonNull Context context, @NonNull CaptureStreamEntry entry) {
        String raw = entry.getBody() != null ? entry.getBody() : "";
        int bytes = StreamPayloadFormatter.logicalPayloadByteCount(raw);
        return context.getString(R.string.stream_direction_with_bytes, entry.getDirection(), bytes);
    }

    /** 工作台搜索等非 UI 上下文使用，与字符串资源等价拼接，避免出现 Android 依赖。 */
    @NonNull
    static String plainDirectionByteSuffix(@NonNull String directionLabel, @Nullable String rawBody) {
        int bytes = StreamPayloadFormatter.logicalPayloadByteCount(rawBody != null ? rawBody : "");
        return directionLabel + "（共" + bytes + "字节）";
    }
}
