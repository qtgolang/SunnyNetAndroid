package com.sunnynet.tools.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.HttpBodyReadableFormatter;

/**
 * HTTP 详情「正文」图像：全屏大图预览（**等比** {@link ImageView.ScaleType#FIT_CENTER}，黑底留边不拉伸），支持将原始字节写入系统相册（Pictures/SunnyNet）。
 */
public final class DetailBodyImageFullscreenHelper {

    /** 全屏解码长边上限（像素），略高于常见屏幕物理像素，仍用 inSampleSize 控内存。 */
    private static final int FULLSCREEN_DECODE_MAX_SIDE_PX = 8192;

    private DetailBodyImageFullscreenHelper() {
    }

    /**
     * 弹出全屏对话框展示图像；关闭时回收解码用于展示的 {@link Bitmap}。
     *
     * @param context   用于取主题与 {@link Toast}；会从 {@link ContextWrapper} 解出 {@link Activity}
     * @param imageBytes 正文原始图像字节（保存时按原样写入 MediaStore）
     */
    public static void show(@NonNull Context context, @NonNull byte[] imageBytes) {
        Activity activity = unwrapActivity(context);
        if (activity == null || activity.isFinishing()) {
            Toast.makeText(context.getApplicationContext(),
                    R.string.detail_body_image_preview_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bmp = HttpBodyReadableFormatter.tryDecodeImageBitmap(imageBytes, FULLSCREEN_DECODE_MAX_SIDE_PX);
        if (bmp == null) {
            Toast.makeText(activity, R.string.detail_body_image_preview_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(activity, R.style.Theme_SunnyNet_FullscreenImageDialog);
        dialog.setContentView(LayoutInflater.from(activity)
                .inflate(R.layout.dialog_detail_body_image_fullscreen, null));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                w.setStatusBarColor(Color.BLACK);
            }
        }

        ImageView image = dialog.findViewById(R.id.fullscreen_body_image);
        MaterialButton save = dialog.findViewById(R.id.fullscreen_body_image_save);
        MaterialButton close = dialog.findViewById(R.id.fullscreen_body_image_close);

        if (image == null || save == null || close == null) {
            bmp.recycle();
            return;
        }
        image.setAdjustViewBounds(false);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bmp);
        image.setContentDescription(activity.getString(R.string.detail_body_image_fullscreen_content_desc));

        save.setOnClickListener(v ->
                DetailBodyPictureSaveHelper.saveImageBytes(activity, imageBytes));

        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setCancelable(true);
        dialog.setOnDismissListener(di -> {
            if (!bmp.isRecycled()) {
                bmp.recycle();
            }
            image.setImageDrawable(null);
        });
        dialog.show();
    }

    @Nullable
    private static Activity unwrapActivity(@Nullable Context context) {
        for (Context c = context; c != null; ) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            if (c instanceof ContextWrapper) {
                c = ((ContextWrapper) c).getBaseContext();
            } else {
                break;
            }
        }
        return null;
    }
}
