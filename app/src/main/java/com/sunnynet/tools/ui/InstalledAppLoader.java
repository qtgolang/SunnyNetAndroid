package com.sunnynet.tools.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 加载设备上可启动的应用列表（排除本应用），按名称排序。
 */
public final class InstalledAppLoader {

    private InstalledAppLoader() {
    }

    /** 在后台线程调用，避免阻塞 UI。 */
    public static List<InstalledAppInfo> load(Context context) {
        String selfPkg = context.getPackageName();
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);

        Set<String> seen = new HashSet<>();
        List<InstalledAppInfo> apps = new ArrayList<>();
        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (selfPkg.equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            apps.add(new InstalledAppInfo(pkg, label, info.loadIcon(pm)));
        }
        Collections.sort(apps, (a, b) ->
                a.label.toString().compareToIgnoreCase(b.label.toString()));
        return apps;
    }
}
