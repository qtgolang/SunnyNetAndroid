package com.sunnynet.tools.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.sunnynet.tools.R;

/**
 * UI 写入剪贴板并给出轻提示，与其它复制入口保持一致。
 */
public final class ClipboardUiHelper {

    private ClipboardUiHelper() {
    }

    /**
     * @param clipLabel 仅用于系统剪贴板元数据，便于调试区分来源
     */
    public static void copyPlain(@NonNull Context context, @NonNull CharSequence text,
                                 @NonNull String clipLabel) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text));
        Toast.makeText(context, R.string.detail_overview_copied, Toast.LENGTH_SHORT).show();
    }
}
