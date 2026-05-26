package com.sunnynet.tools.ui;

import android.graphics.drawable.Drawable;

/**
 * 已安装应用条目，供抓包目标选择器展示。
 */
public final class InstalledAppInfo {

    public final String packageName;
    public final CharSequence label;
    public final Drawable icon;

    public InstalledAppInfo(String packageName, CharSequence label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }
}
