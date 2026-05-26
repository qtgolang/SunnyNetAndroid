package com.sunnynet.tools.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sunnynet.tools.R;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 将详情页「正文」图像原始字节写入系统相册目录 {@code Pictures/SunnyNet}（API 29+ 使用 RELATIVE_PATH）。
 */
public final class DetailBodyPictureSaveHelper {

    /** 保存在相册中的相对目录名（{@link Environment#DIRECTORY_PICTURES} 下）。 */
    private static final String ALBUM_RELATIVE_DIR = Environment.DIRECTORY_PICTURES + "/SunnyNet";

    private DetailBodyPictureSaveHelper() {
    }

    /**
     * 将图像字节写入公共图片媒体库；文件名带时间戳，扩展名优先按解码得到的 MIME。
     *
     * @return 成功时返回写入的 Uri，失败返回 null（已弹出 Toast）
     */
    @Nullable
    public static Uri saveImageBytes(@NonNull Context context, @NonNull byte[] imageBytes) {
        if (imageBytes.length == 0) {
            Toast.makeText(context, R.string.detail_body_image_save_failed, Toast.LENGTH_SHORT).show();
            return null;
        }
        String mime = guessImageMime(imageBytes);
        String ext = extensionFromMime(mime);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String displayName = "SunnyNet_" + fmt.format(new Date()) + ext;

        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM_RELATIVE_DIR);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = resolver.insert(collection, cv);
        if (uri == null) {
            Toast.makeText(context, R.string.detail_body_image_save_failed, Toast.LENGTH_SHORT).show();
            return null;
        }

        boolean ok = false;
        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os != null) {
                os.write(imageBytes);
                os.flush();
                ok = true;
            }
        } catch (IOException ignored) {
            ok = false;
        }

        if (!ok) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
                // noop
            }
            Toast.makeText(context, R.string.detail_body_image_save_failed, Toast.LENGTH_SHORT).show();
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        }

        Toast.makeText(context, R.string.detail_body_image_saved, Toast.LENGTH_SHORT).show();
        return uri;
    }

    @NonNull
    private static String guessImageMime(@NonNull byte[] imageBytes) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, o);
        if (o.outMimeType != null && !o.outMimeType.isEmpty()) {
            return o.outMimeType;
        }
        return "image/jpeg";
    }

    @NonNull
    private static String extensionFromMime(@NonNull String mime) {
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext != null && !ext.isEmpty()) {
            return "." + ext;
        }
        if (mime.endsWith("/png")) {
            return ".png";
        }
        if (mime.endsWith("/webp")) {
            return ".webp";
        }
        if (mime.endsWith("/gif")) {
            return ".gif";
        }
        return ".jpg";
    }
}
