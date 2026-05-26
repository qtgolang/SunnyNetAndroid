package com.sunnynet.tools.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

/**
 * 通过 {@link ConnectivityManager} 判断是否仍存在宣告 {@link NetworkCapabilities#TRANSPORT_VPN} 的网络，
 * 避免因仅查「默认网络」在部分 OEM / 链路切换瞬间误判为本应用 TUN 已掉线。
 */
public final class VpnTransportHelper {

    private VpnTransportHelper() {
    }

    /**
     * 任一已注册网络上是否带有 VPN Transport（{@code hasTransport(VPN)}）。
     * 比仅用 {@link ConnectivityManager#getActiveNetwork()} 更稳：部分机型在刚建立 TUN
     * 或 Wi‑Fi/蜂窝切换时，默认网络的 Capability 会短暂不出现 VPN。
     */
    public static boolean anyNetworkHasVpnTransport(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        for (Network network : cm.getAllNetworks()) {
            if (network == null) {
                continue;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兼容旧名：与 {@link #anyNetworkHasVpnTransport(Context)} 同义。
     *
     * @deprecated 语义已改为「全表扫描」，请优先使用 {@link #anyNetworkHasVpnTransport}。
     */
    @Deprecated
    public static boolean defaultNetworkHasVpn(@NonNull Context context) {
        return anyNetworkHasVpnTransport(context);
    }
}
