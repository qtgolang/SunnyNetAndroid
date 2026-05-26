package com.sunnynet.tools.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import com.sunnynet.tools.R;
import com.sunnynet.tools.capture.CaptureClientIp;
import com.sunnynet.tools.capture.CaptureRecord;

/**
 * 按包名解析应用图标、名称与包名展示文案，供抓包列表等 UI 复用。
 */
public final class AppIconHelper {

    private static final int CACHE_SIZE = 128;
    private static final LruCache<String, Drawable.ConstantState> ICON_CACHE = new LruCache<>(CACHE_SIZE);
    private static final LruCache<String, String> LABEL_CACHE = new LruCache<>(CACHE_SIZE);

    private AppIconHelper() {
    }

    /**
     * 绑定列表卡片中的应用图标、应用名与包名。
     * 包名为空时：应用名显示「代理请求」或「系统进程」，包名行留空。
     */
    public static void bindListItem(@NonNull ImageView iconView, @NonNull TextView appNameView,
                                    @NonNull TextView packageView, @NonNull CaptureRecord record) {
        bindListItem(iconView, appNameView, packageView, record.getPackageName(), record.getClientIp());
    }

    public static void bindListItem(@NonNull ImageView iconView, @NonNull TextView appNameView,
                                    @NonNull TextView packageView, @Nullable String packageName,
                                    @Nullable String clientIp) {
        Context context = iconView.getContext();
        if (packageName == null || packageName.isEmpty()) {
            String label = resolveEmptyPackageLabel(context, clientIp);
            bindPlaceholderIcon(iconView, isProxyClient(clientIp));
            appNameView.setText(label);
            packageView.setText("");
            packageView.setVisibility(View.GONE);
            return;
        }
        packageView.setVisibility(View.VISIBLE);
        bindAppIcon(iconView, packageName);
        appNameView.setText(loadInstalledAppLabel(context, packageName));
        packageView.setText(packageName);
    }

    /** 供搜索筛选拼接可匹配文本（应用名 + 包名 + 系统进程/代理请求别名）。 */
    @NonNull
    public static String searchableText(@NonNull Context context, @NonNull CaptureRecord record) {
        return searchableText(context, record.getPackageName(), record.getClientIp());
    }

    @NonNull
    public static String searchableText(@NonNull Context context, @Nullable String packageName,
                                        @Nullable String clientIp) {
        if (packageName == null || packageName.isEmpty()) {
            return resolveEmptyPackageLabel(context, clientIp);
        }
        return loadInstalledAppLabel(context, packageName) + " " + packageName;
    }

    public static void bind(@NonNull ImageView view, @Nullable String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            bindPlaceholderIcon(view, false);
            return;
        }
        bindAppIcon(view, packageName);
    }

    /** 无包名占位：代理连接用地球+转发图标，系统进程用齿轮。 */
    private static void bindPlaceholderIcon(@NonNull ImageView view, boolean proxy) {
        int padding = (int) (view.getResources().getDisplayMetrics().density * (proxy ? 7f : 6f));
        view.setPadding(padding, padding, padding, padding);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (proxy) {
            view.setBackgroundResource(R.drawable.bg_capture_proxy_icon);
            view.setImageResource(R.drawable.ic_capture_proxy);
            return;
        }
        view.setBackgroundResource(R.drawable.bg_capture_app_icon);
        view.setImageResource(R.drawable.ic_capture_system_process);
    }

    private static void bindAppIcon(@NonNull ImageView view, @NonNull String packageName) {
        int padding = (int) (view.getResources().getDisplayMetrics().density * 3f);
        view.setPadding(padding, padding, padding, padding);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackgroundResource(R.drawable.bg_capture_app_icon);
        Drawable.ConstantState cached = ICON_CACHE.get(packageName);
        if (cached != null) {
            view.setImageDrawable(cached.newDrawable(view.getResources()));
            return;
        }
        PackageManager pm = view.getContext().getPackageManager();
        try {
            Drawable icon = pm.getApplicationIcon(packageName);
            if (icon.getConstantState() != null) {
                ICON_CACHE.put(packageName, icon.getConstantState());
            }
            view.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            bindPlaceholderIcon(view, false);
        }
    }

    private static boolean isProxyClient(@Nullable String clientIp) {
        return !CaptureClientIp.isLocalLoopback(clientIp);
    }

    /** 解析应用显示名称；包名为空时按 ClientIP 返回「代理请求」或「系统进程」。 */
    @NonNull
    public static String resolveAppLabel(@NonNull Context context, @NonNull CaptureRecord record) {
        return resolveAppLabel(context, record.getPackageName(), record.getClientIp());
    }

    @NonNull
    public static String resolveAppLabel(@NonNull Context context, @Nullable String packageName) {
        return resolveAppLabel(context, packageName, null);
    }

    @NonNull
    public static String resolveAppLabel(@NonNull Context context, @Nullable String packageName,
                                         @Nullable String clientIp) {
        if (packageName == null || packageName.isEmpty()) {
            return resolveEmptyPackageLabel(context, clientIp);
        }
        return loadInstalledAppLabel(context, packageName);
    }

    @NonNull
    private static String resolveEmptyPackageLabel(@NonNull Context context, @Nullable String clientIp) {
        if (isProxyClient(clientIp)) {
            return context.getString(R.string.capture_proxy_request);
        }
        return context.getString(R.string.capture_system_process);
    }

    @NonNull
    private static String loadInstalledAppLabel(@NonNull Context context, @NonNull String packageName) {
        String cached = LABEL_CACHE.get(packageName);
        if (cached != null) {
            return cached;
        }
        PackageManager pm = context.getPackageManager();
        try {
            CharSequence label = pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
            String text = label != null ? label.toString() : packageName;
            LABEL_CACHE.put(packageName, text);
            return text;
        } catch (PackageManager.NameNotFoundException e) {
            LABEL_CACHE.put(packageName, packageName);
            return packageName;
        }
    }
}
