package com.sunnynet.tools;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

import com.sunnynet.tools.capture.CaptureEngine;
import com.sunnynet.tools.capture.CaptureRepository;
import com.sunnynet.tools.data.CaptureRecordPersistence;

/**
 * 全局初始化；修复 SDK {@code VpnAuth} 在已授权时未 finish 导致透明页挡住触摸的问题。
 */
public class SunnyNetApplication extends Application {

    private static final String VPN_AUTH_CLASS = "com.SunnyNet.tun.VpnAuth";

    @Override
    public void onCreate() {
        super.onCreate();
        CaptureRecordPersistence.init(this);
        CaptureRepository.get().resetSession();
        CaptureEngine.initAppContext(this);
        registerVpnAuthFinishFix();
    }

    /**
     * SunnyNet SDK 的 VpnAuth：若用户此前已授权 VPN，onCreate 里会 startVpn() 但不会 finish，
     * 透明 Activity 会一直盖在主界面上，表现为抓包开始后无法点击任何控件。
     */
    private void registerVpnAuthFinishFix() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (!VPN_AUTH_CLASS.equals(activity.getClass().getName())) {
                    return;
                }
                // 已授权时 prepare 返回 null，说明走的是「直接 startVpn」分支，应关闭本页
                Intent needAuth = VpnService.prepare(activity);
                if (needAuth == null && !activity.isFinishing()) {
                    activity.finish();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
