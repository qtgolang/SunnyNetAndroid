package com.sunnynet.tools.capture;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.widget.Toast;

import com.SunnyNet.SunnyNet;
import com.SunnyNet.api;
import com.SunnyNet.tun.VpnConfig;
import com.SunnyNet.tun.VpnEvent;
import com.sunnynet.tools.R;
import com.sunnynet.tools.net.VpnTransportHelper;
import com.sunnynet.tools.service.CaptureNotificationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 封装 SunnyNet 启停与 VPN 模式抓包配置；监听系统网络是否仍存在 VPN Transport，
 * 在明确掉线后自动停止（全表扫描 Capability + 二次延时确认，减轻 OEM 误判）。
 */
public final class CaptureEngine {

    public static final int DEFAULT_PORT = 2025;
    public static final long OPEN_DRIVE_TUN = 2;

    public interface StatusListener {
        void onEngineStarted();

        void onVpnSuccess();

        void onVpnFailed(String message);

        void onEngineStopped();

        /**
         * 默认网络检测到 VPN 已断开或被其他 VPN 顶替（先于 {@link #onEngineStopped()}）。
         */
        default void onVpnTransportLost() {
        }
    }

    private static CaptureEngine instance;
    private static Context appContext;

    private final SunnyNet sunny = new SunnyNet();
    private final PacketCaptureCallback callback = new PacketCaptureCallback();
    private final List<StatusListener> listeners = new ArrayList<>();
    /** VPN 回调在 SDK 后台线程触发，通知 UI 须切回主线程 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean running;
    /** 防止 UI 与通知栏并发 stop 重复调用 native */
    private volatile boolean stopInProgress;

    /** VPN 刚连上后短暂不做「无 VPN」判定，避免链路未就绪误停 */
    private long vpnWatchGraceUntilElapsed;
    @Nullable
    private ConnectivityManager.NetworkCallback vpnNetworkCallback;

    /** 启动监听后一段时间内忽略「未检测到 VPN」避免 TUN / Connectivity 就绪顺序差异 */
    private static final long VPN_WATCH_GRACE_MS = 4500L;
    /** 网络回调防抖，合并短时间多次 onLost / onCapabilitiesChanged */
    private static final long VPN_LOSS_DEBOUNCE_MS = 800L;
    /**
     * 首次未扫到 TRANSPORT_VPN 后再延后一次复查；仅两次均失败才停抓，降低单次瞬时误判概率。
     */
    private static final long VPN_ABSENT_CONFIRM_DELAY_MS = 1800L;

    /** 须在 {@link #vpnPresenceDebouncedCheck} 之前声明：后者 lambda 会 {@code postDelayed} 本 Runnable。 */
    private final Runnable vpnAbsentConfirmRetry = () -> {
        if (!running || stopInProgress) {
            return;
        }
        if (SystemClock.elapsedRealtime() < vpnWatchGraceUntilElapsed) {
            return;
        }
        Context ctx = appContext();
        if (ctx == null || VpnTransportHelper.anyNetworkHasVpnTransport(ctx)) {
            return;
        }
        notifyVpnTransportLost();
        stop();
    };

    private final Runnable vpnPresenceDebouncedCheck = () -> {
        if (!running || stopInProgress) {
            return;
        }
        if (SystemClock.elapsedRealtime() < vpnWatchGraceUntilElapsed) {
            return;
        }
        Context ctx = appContext();
        if (ctx == null || VpnTransportHelper.anyNetworkHasVpnTransport(ctx)) {
            return;
        }
        mainHandler.removeCallbacks(vpnAbsentConfirmRetry);
        mainHandler.postDelayed(vpnAbsentConfirmRetry, VPN_ABSENT_CONFIRM_DELAY_MS);
    };

    private void scheduleVpnPresenceCheck() {
        mainHandler.removeCallbacks(vpnPresenceDebouncedCheck);
        mainHandler.removeCallbacks(vpnAbsentConfirmRetry);
        mainHandler.postDelayed(vpnPresenceDebouncedCheck, VPN_LOSS_DEBOUNCE_MS);
    }

    /** VPN 连通后监听默认网络，若不再走 VPN（含被其他 VPN 顶替时的短暂断层）则自动 {@link #stop()}。 */
    private void registerVpnDefaultNetworkWatch() {
        unregisterVpnDefaultNetworkWatch();
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        vpnWatchGraceUntilElapsed = SystemClock.elapsedRealtime() + VPN_WATCH_GRACE_MS;
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        vpnNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                scheduleVpnPresenceCheck();
            }

            @Override
            public void onLost(@NonNull Network network) {
                scheduleVpnPresenceCheck();
            }
        };
        cm.registerDefaultNetworkCallback(vpnNetworkCallback);
    }

    private void unregisterVpnDefaultNetworkWatch() {
        mainHandler.removeCallbacks(vpnPresenceDebouncedCheck);
        mainHandler.removeCallbacks(vpnAbsentConfirmRetry);
        Context ctx = appContext();
        if (vpnNetworkCallback == null || ctx == null) {
            vpnNetworkCallback = null;
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(vpnNetworkCallback);
            } catch (RuntimeException ignored) {
            }
        }
        vpnNetworkCallback = null;
    }

    private void notifyVpnTransportLost() {
        dispatchToMain(() -> {
            List<StatusListener> copy = new ArrayList<>(listeners);
            for (StatusListener listener : copy) {
                listener.onVpnTransportLost();
            }
            if (copy.isEmpty()) {
                Context ctx = appContext();
                if (ctx != null) {
                    Toast.makeText(ctx, ctx.getString(R.string.capture_stopped_vpn_takeover), Toast.LENGTH_LONG)
                            .show();
                }
            }
        });
    }

    /** 为 false 时引擎仍运行，但回调不再写入列表（对应桌面「隐藏捕获」） */
    private boolean captureVisible = true;
    /** 为空时抓取除本应用外全部应用；非空时仅抓取列表内包名 */
    private final List<String> targetPackages = new ArrayList<>();
    private int listenPort = DEFAULT_PORT;
    private boolean captureHttpEnabled = true;
    private boolean captureWebSocketEnabled = true;
    private boolean captureTcpEnabled = true;
    private boolean captureUdpEnabled = true;

    public static synchronized CaptureEngine get() {
        if (instance == null) {
            instance = new CaptureEngine();
        }
        return instance;
    }

    public static void initAppContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
            CaptureEngine engine = get();
            engine.loadListenPort();
            engine.loadCaptureRangePrefs();
            engine.syncCaptureRangeToGate();
            CaptureRuleStore.init(context);
        }
    }

    private static Context appContext() {
        return appContext;
    }

    public SunnyNet getSunny() {
        return sunny;
    }

    public boolean isRunning() {
        return running;
    }

    /** 是否将新会话展示到列表（隐藏捕获时为 false） */
    public boolean isCaptureVisible() {
        return captureVisible;
    }

    public void setCaptureVisible(boolean captureVisible) {
        this.captureVisible = captureVisible;
        PacketCaptureGate.setCaptureVisible(captureVisible);
    }

    public boolean isCaptureHttpEnabled() {
        return captureHttpEnabled;
    }

    public boolean isCaptureWebSocketEnabled() {
        return captureWebSocketEnabled;
    }

    public boolean isCaptureTcpEnabled() {
        return captureTcpEnabled;
    }

    public boolean isCaptureUdpEnabled() {
        return captureUdpEnabled;
    }

    /** 至少保留一种协议，否则回调层无法写入任何记录。 */
    public boolean isAnyProtocolCaptureEnabled() {
        return captureHttpEnabled || captureWebSocketEnabled
                || captureTcpEnabled || captureUdpEnabled;
    }

    public void setCaptureHttpEnabled(boolean enabled) {
        captureHttpEnabled = enabled;
        persistCaptureRangePrefs();
        syncCaptureRangeToGate();
    }

    public void setCaptureWebSocketEnabled(boolean enabled) {
        captureWebSocketEnabled = enabled;
        persistCaptureRangePrefs();
        syncCaptureRangeToGate();
    }

    public void setCaptureTcpEnabled(boolean enabled) {
        captureTcpEnabled = enabled;
        persistCaptureRangePrefs();
        syncCaptureRangeToGate();
    }

    public void setCaptureUdpEnabled(boolean enabled) {
        captureUdpEnabled = enabled;
        persistCaptureRangePrefs();
        syncCaptureRangeToGate();
    }

    private void syncCaptureRangeToGate() {
        PacketCaptureGate.setProtocolFlags(captureHttpEnabled, captureWebSocketEnabled,
                captureTcpEnabled, captureUdpEnabled);
    }

    private void loadCaptureRangePrefs() {
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        android.content.SharedPreferences prefs =
                ctx.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE);
        captureHttpEnabled = prefs.getBoolean(KEY_CAPTURE_HTTP, true);
        captureWebSocketEnabled = prefs.getBoolean(KEY_CAPTURE_WEBSOCKET, true);
        captureTcpEnabled = prefs.getBoolean(KEY_CAPTURE_TCP, true);
        captureUdpEnabled = prefs.getBoolean(KEY_CAPTURE_UDP, true);
    }

    private void persistCaptureRangePrefs() {
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        ctx.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CAPTURE_HTTP, captureHttpEnabled)
                .putBoolean(KEY_CAPTURE_WEBSOCKET, captureWebSocketEnabled)
                .putBoolean(KEY_CAPTURE_TCP, captureTcpEnabled)
                .putBoolean(KEY_CAPTURE_UDP, captureUdpEnabled)
                .apply();
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        if (isValidListenPort(listenPort)) {
            this.listenPort = listenPort;
            persistListenPort();
        }
    }

    public static boolean isValidListenPort(int port) {
        return port > 0 && port < 65536;
    }

    /** 监听端口被占用（如 bind: address already in use）。 */
    public static boolean isListenPortInUseError(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("address already in use") || lower.contains("eaddrinuse");
    }

    /** 监听绑定被拒绝（常见于端口号过小等，SDK 报错含 bind: permission denied）。 */
    public static boolean isListenPortPermissionDeniedError(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("bind: permission denied")) {
            return true;
        }
        if (lower.contains("permission denied") && lower.contains("listen ")) {
            return true;
        }
        return lower.contains("eacces") && (lower.contains("listen") || lower.contains("bind"));
    }

    /** 端口应用结果 */
    public enum PortApplyResult {
        /** 与当前端口相同，未操作 */
        UNCHANGED,
        /** 未抓包，仅保存新端口 */
        APPLIED_IDLE,
        /** 抓包中已停止并以新端口重启成功 */
        RESTARTED,
        /** 端口非法 */
        INVALID,
        /** 抓包中重启失败（已停止，需手动开始） */
        RESTART_FAILED
    }

    /**
     * 设置监听端口；若正在抓包则先停止再以新端口启动。
     * 回调在主线程执行。
     */
    public void applyListenPortAsync(int port, @Nullable PortApplyListener listener) {
        if (!isValidListenPort(port)) {
            dispatchToMain(() -> {
                if (listener != null) {
                    listener.onResult(PortApplyResult.INVALID);
                }
            });
            return;
        }
        if (port == listenPort) {
            dispatchToMain(() -> {
                if (listener != null) {
                    listener.onResult(PortApplyResult.UNCHANGED);
                }
            });
            return;
        }
        new Thread(() -> {
            boolean wasRunning = running;
            if (wasRunning) {
                stop();
            }
            listenPort = port;
            persistListenPort();
            if (wasRunning) {
                dispatchToMain(() -> {
                    boolean ok = start();
                    if (listener != null) {
                        listener.onResult(ok ? PortApplyResult.RESTARTED : PortApplyResult.RESTART_FAILED);
                    }
                });
            } else {
                dispatchToMain(() -> {
                    if (listener != null) {
                        listener.onResult(PortApplyResult.APPLIED_IDLE);
                    }
                });
            }
        }, "apply-listen-port").start();
    }

    public interface PortApplyListener {
        void onResult(PortApplyResult result);
    }

    private static final String PREFS_ENGINE = "sunnynet_engine";
    private static final String KEY_LISTEN_PORT = "listen_port";
    private static final String KEY_CAPTURE_HTTP = "capture_http";
    private static final String KEY_CAPTURE_WEBSOCKET = "capture_websocket";
    private static final String KEY_CAPTURE_TCP = "capture_tcp";
    private static final String KEY_CAPTURE_UDP = "capture_udp";

    private void persistListenPort() {
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        ctx.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LISTEN_PORT, listenPort)
                .apply();
    }

    private void loadListenPort() {
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        int saved = ctx.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
                .getInt(KEY_LISTEN_PORT, DEFAULT_PORT);
        if (isValidListenPort(saved)) {
            listenPort = saved;
        }
    }

    /** 目标包名为空时表示抓取除本应用外的全部应用 */
    public boolean isCaptureAllApps() {
        return targetPackages.isEmpty();
    }

    public List<String> getTargetPackages() {
        return new ArrayList<>(targetPackages);
    }

    public void setTargetPackages(List<String> packages) {
        targetPackages.clear();
        String selfPkg = selfPackageName();
        if (packages != null) {
            for (String pkg : packages) {
                if (pkg == null) {
                    continue;
                }
                String trimmed = pkg.trim();
                if (trimmed.isEmpty() || trimmed.equals(selfPkg)) {
                    continue;
                }
                if (!targetPackages.contains(trimmed)) {
                    targetPackages.add(trimmed);
                }
            }
        }
    }

    private String selfPackageName() {
        Context ctx = appContext();
        return ctx != null ? ctx.getPackageName() : "com.sunnynet.tools";
    }

    public void addListener(StatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(StatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * 启动 SunnyNet 并打开 Android TUN（VPN）抓包。
     * 需在主线程调用；VPN 授权由 SDK 弹出。
     */
    public boolean start() {
        if (running) {
            return true;
        }
        PacketCaptureGate.setSessionActive(true);
        PacketCaptureGate.setCaptureVisible(captureVisible);
        syncCaptureRangeToGate();
        CaptureRepository.get().setSuppressLiveUi(true);

        VpnConfig.Callback = new VpnEvent() {
            @Override
            public void onSuccess() {
                running = true;
                Context ctx = appContext();
                if (ctx != null) {
                    CaptureNotificationHelper.showCapturing(ctx);
                }
                notifyVpnSuccess();
            }

            @Override
            public void onFail(String message) {
                running = false;
                PacketCaptureGate.setSessionActive(false);
                CaptureRepository.get().setSuppressLiveUi(false);
                Context ctx = appContext();
                if (ctx != null) {
                    CaptureNotificationHelper.dismiss(ctx);
                }
                notifyVpnFailed(message != null ? message : "VPN 启动失败");
            }
        };

        sunny.SetPort(listenPort);
        sunny.RandomJa3(true);
        sunny.SetCallback(callback);

        if (!sunny.Start()) {
            notifyVpnFailed(sunny.Error());
            return false;
        }
        notifyEngineStarted();

        applyCaptureScope();
        sunny.OpenDrive(OPEN_DRIVE_TUN);
        return true;
    }

    public void stop() {
        if (stopInProgress) {
            return;
        }
        stopInProgress = true;
        try {
            unregisterVpnDefaultNetworkWatch();
            PacketCaptureGate.setSessionActive(false);
            CaptureRepository.get().setSuppressLiveUi(false);
            CaptureRepository.get().markOpenStreamSessionsDisconnected();
            Context ctx = appContext();
            if (ctx != null) {
                CaptureNotificationHelper.dismiss(ctx);
            }
            if (!running && sunny.Context != 0) {
                sunny.Stop();
                CaptureRepository.get().requestUiRefresh();
                return;
            }
            sunny.Stop();
            running = false;
            VpnConfig.Callback = null;
            CaptureRepository.get().requestUiRefresh();
            notifyEngineStopped();
        } finally {
            stopInProgress = false;
        }
    }

    private void applyCaptureScope() {
        sunny.ProcessCancelAll();
        if (targetPackages.isEmpty()) {
            sunny.ProcessAllName(true, false);
            return;
        }
        for (String pkg : targetPackages) {
            sunny.ProcessAddName(pkg);
        }
    }

    public static String versionText() {
        return api.GetSunnyVersion();
    }

    private void notifyEngineStarted() {
        dispatchToMain(() -> {
            for (StatusListener listener : new ArrayList<>(listeners)) {
                listener.onEngineStarted();
            }
        });
    }

    private void notifyVpnSuccess() {
        dispatchToMain(() -> {
            registerVpnDefaultNetworkWatch();
            for (StatusListener listener : new ArrayList<>(listeners)) {
                listener.onVpnSuccess();
            }
        });
    }

    private void notifyVpnFailed(String message) {
        final String msg = message;
        dispatchToMain(() -> {
            for (StatusListener listener : new ArrayList<>(listeners)) {
                listener.onVpnFailed(msg);
            }
        });
    }

    private void notifyEngineStopped() {
        dispatchToMain(() -> {
            for (StatusListener listener : new ArrayList<>(listeners)) {
                listener.onEngineStopped();
            }
        });
    }

    /** 若已在主线程则同步执行，避免不必要的 post 延迟 */
    private void dispatchToMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }
}
