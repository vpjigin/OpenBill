package app.billing.channel;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Finds this computer's addresses on the shop network (private IPv4 on real, active adapters). */
public final class NetworkInfo {

    private static final List<String> VIRTUAL_HINTS = List.of(
            "docker", "veth", "br-", "vbox", "virtualbox", "vmware", "vmnet", "hyper-v", "vethernet",
            "wsl", "tailscale", "zerotier", "utun", "tun", "tap", "loopback", "bluetooth");

    private NetworkInfo() {
    }

    public static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || isVirtualByName(nif)) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return List.of();
        }
        // Prefer the usual home/shop router ranges first.
        result.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        return result;
    }

    private static boolean isVirtualByName(NetworkInterface nif) {
        String n = (nif.getName() + " " + nif.getDisplayName()).toLowerCase(Locale.ROOT);
        return VIRTUAL_HINTS.stream().anyMatch(n::contains);
    }

    private static int rank(String ip) {
        if (ip.startsWith("192.168.")) {
            return 0;
        }
        if (ip.startsWith("10.")) {
            return 1;
        }
        return 2;
    }
}
