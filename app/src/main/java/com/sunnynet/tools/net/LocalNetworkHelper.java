package com.sunnynet.tools.net;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 读取本机局域网 IPv4，供端口设置页展示「内网访问」地址。
 */
public final class LocalNetworkHelper {

    private LocalNetworkHelper() {
    }

    /**
     * 获取首选局域网 IPv4；未连接 Wi‑Fi/以太网等时返回 null。
     */
    @Nullable
    public static String getPrimaryLanIpv4() {
        List<String> preferred = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                String name = ni.getName();
                if (isVirtualOrCellularInterface(name)) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) {
                        continue;
                    }
                    String host = addr.getHostAddress();
                    if (host == null || host.startsWith("169.254.")) {
                        continue;
                    }
                    if (isPreferredLanInterface(name)) {
                        preferred.add(host);
                    } else {
                        fallback.add(host);
                    }
                }
            }
        } catch (Exception ignored) {
            // 无网卡权限或系统异常时视为不可用
        }
        if (!preferred.isEmpty()) {
            return preferred.get(0);
        }
        if (!fallback.isEmpty()) {
            return fallback.get(0);
        }
        return null;
    }

    /** 拼接内网代理地址，例如 192.168.1.8:8888。 */
    @Nullable
    public static String formatLanAccessEndpoint(@Nullable String ipv4, int port) {
        if (ipv4 == null || ipv4.isEmpty() || port <= 0 || port >= 65536) {
            return null;
        }
        return ipv4 + ':' + port;
    }

    private static boolean isPreferredLanInterface(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("en");
    }

    /** 跳过 VPN TUN、蜂窝 rmnet 等不适合作为内网访问展示的网卡。 */
    private static boolean isVirtualOrCellularInterface(@Nullable String name) {
        if (name == null) {
            return true;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("tun") || n.startsWith("tap") || n.startsWith("ppp")
                || n.startsWith("dummy") || n.startsWith("rmnet");
    }
}
