package com.sunnynet.tools.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 通知栏「停止抓包」按钮：在后台线程停止引擎，不依赖 Activity 主线程是否空闲。
 */
public class CaptureStopReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP_CAPTURE = "com.sunnynet.tools.action.STOP_CAPTURE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_STOP_CAPTURE.equals(intent.getAction())) {
            return;
        }
        Intent stopService = new Intent(context, CaptureForegroundService.class);
        stopService.setAction(CaptureForegroundService.ACTION_STOP);
        context.startService(stopService);
    }
}
