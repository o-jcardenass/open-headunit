package z7;

import a0.s0;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.support.v4.media.session.t;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class j {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final Context f13290a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public WifiManager.LocalOnlyHotspotReservation f13291b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public WifiP2pManager f13292c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public WifiP2pManager.Channel f13293d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public volatile String f13294e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public volatile boolean f13295f = false;

    public j(Context context) {
        this.f13290a = context.getApplicationContext();
    }

    public static String a(String str) {
        if (str == null) {
            return null;
        }
        return (str.length() >= 2 && str.startsWith("\"") && str.endsWith("\"")) ? str.substring(1, str.length() - 1) : str;
    }

    public static void b(j jVar, t tVar) {
        i iVar = new i(1, tVar, jVar);
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                jVar.f13292c.createGroup(jVar.f13293d, new WifiP2pConfig.Builder().setGroupOperatingBand(2).build(), iVar);
                Log.d("HUR-Hotspot", "P2P createGroup requested on 5 GHz band");
                return;
            } catch (Throwable th) {
                Log.w("HUR-Hotspot", "5 GHz P2P config unavailable, using default band: " + th);
            }
        }
        jVar.f13292c.createGroup(jVar.f13293d, iVar);
    }

    public static String c() {
        String strD;
        try {
            ArrayList list = Collections.list(NetworkInterface.getNetworkInterfaces());
            int size = list.size();
            int i5 = 0;
            while (i5 < size) {
                Object obj = list.get(i5);
                i5++;
                NetworkInterface networkInterface = (NetworkInterface) obj;
                if (networkInterface.isUp() && !networkInterface.isLoopback() && e(networkInterface.getName()) && (strD = d(networkInterface)) != null && f(strD)) {
                    Log.d("HUR-Hotspot", "AP IP " + strD + " on interface " + networkInterface.getName());
                    return strD;
                }
            }
        } catch (Exception e10) {
            Log.w("HUR-Hotspot", "apIpAddress discovery failed: " + e10);
        }
        Log.w("HUR-Hotspot", "AP IP not found on an ap interface, falling back to 192.168.49.1");
        return "192.168.49.1";
    }

    public static String d(NetworkInterface networkInterface) {
        try {
            ArrayList list = Collections.list(networkInterface.getInetAddresses());
            int size = list.size();
            int i5 = 0;
            while (i5 < size) {
                Object obj = list.get(i5);
                i5++;
                InetAddress inetAddress = (InetAddress) obj;
                if ((inetAddress instanceof Inet4Address) && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                    return inetAddress.getHostAddress();
                }
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static boolean e(String str) {
        if (str != null) {
            return str.contains("p2p") || str.startsWith("ap") || str.startsWith("swlan") || str.equals("wlan1");
        }
        return false;
    }

    public static boolean f(String str) {
        if (str == null) {
            return false;
        }
        if (str.startsWith("192.168.") || str.startsWith("10.")) {
            return true;
        }
        if (str.startsWith("172.")) {
            try {
                int i5 = Integer.parseInt(str.split("\\.")[1]);
                if (i5 >= 16 && i5 <= 31) {
                    return true;
                }
            } catch (Exception unused) {
            }
        }
        return false;
    }

    public static String g(String str) {
        Matcher matcher;
        if (str == null) {
            return null;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(new String[]{"ip", "link", "show", str}).getInputStream()));
            Pattern patternCompile = Pattern.compile("link/ether ([0-9a-fA-F:]{17})");
            do {
                String line = bufferedReader.readLine();
                if (line == null) {
                    bufferedReader.close();
                    return null;
                }
                matcher = patternCompile.matcher(line);
            } while (!matcher.find());
            bufferedReader.close();
            return matcher.group(1);
        } catch (Exception unused) {
        }
    }

    public static String h(String str) {
        List<NetworkInterface> list;
        try {
            if (str != null) {
                NetworkInterface byName = NetworkInterface.getByName(str);
                list = byName != null ? Collections.singletonList(byName) : Collections.EMPTY_LIST;
            } else {
                list = Collections.list(NetworkInterface.getNetworkInterfaces());
            }
            for (NetworkInterface networkInterface : list) {
                String name = networkInterface.getName() == null ? "" : networkInterface.getName();
                if (str != null || e(name)) {
                    String strI = i(networkInterface);
                    if (strI != null) {
                        Log.d("HUR-Hotspot", "AP MAC via IPv6 link-local on " + name + " = " + strI);
                        return strI;
                    }
                }
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static String i(NetworkInterface networkInterface) {
        String strN;
        try {
            ArrayList list = Collections.list(networkInterface.getInetAddresses());
            int size = list.size();
            int i5 = 0;
            while (i5 < size) {
                Object obj = list.get(i5);
                i5++;
                InetAddress inetAddress = (InetAddress) obj;
                if ((inetAddress instanceof Inet6Address) && inetAddress.isLinkLocalAddress()) {
                    byte[] address = inetAddress.getAddress();
                    if (address.length == 16 && (address[11] & 255) == 255 && (address[12] & 255) == 254 && (strN = n(String.format("%02X:%02X:%02X:%02X:%02X:%02X", Integer.valueOf((address[8] & 255) ^ 2), Integer.valueOf(address[9] & 255), Integer.valueOf(address[10] & 255), Integer.valueOf(address[13] & 255), Integer.valueOf(address[14] & 255), Integer.valueOf(address[15] & 255)))) != null) {
                        return strN;
                    }
                }
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static String j(String str) {
        byte[] hardwareAddress;
        if (str == null) {
            return null;
        }
        try {
            NetworkInterface byName = NetworkInterface.getByName(str);
            if (byName != null && (hardwareAddress = byName.getHardwareAddress()) != null && hardwareAddress.length == 6) {
                StringBuilder sb = new StringBuilder();
                for (int i5 = 0; i5 < hardwareAddress.length; i5++) {
                    if (i5 > 0) {
                        sb.append(':');
                    }
                    sb.append(String.format("%02X", Byte.valueOf(hardwareAddress[i5])));
                }
                return sb.toString();
            }
        } catch (Exception unused) {
        }
        return null;
    }

    public static String k(String str) {
        if (str == null) {
            return null;
        }
        File file = new File(s0.w("/sys/class/net/", str, "/address"));
        if (!file.exists()) {
            return null;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            try {
                String line = bufferedReader.readLine();
                String strTrim = line == null ? null : line.trim();
                bufferedReader.close();
                return strTrim;
            } catch (Throwable th) {
                try {
                    bufferedReader.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Exception unused) {
            return null;
        }
    }

    public static String l() {
        try {
            File[] fileArrListFiles = new File("/sys/class/net").listFiles();
            if (fileArrListFiles == null) {
                return null;
            }
            for (File file : fileArrListFiles) {
                String lowerCase = file.getName().toLowerCase();
                if (lowerCase.contains("p2p") || lowerCase.contains("wlan") || lowerCase.contains("ap")) {
                    String strK = k(file.getName());
                    if (n(strK) != null) {
                        return strK;
                    }
                }
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static String n(String str) {
        if (str == null) {
            return null;
        }
        String upperCase = str.trim().toUpperCase();
        if (upperCase.isEmpty() || upperCase.equals("00:00:00:00:00:00") || upperCase.equals("02:00:00:00:00:00")) {
            return null;
        }
        return upperCase;
    }

    /* JADX WARN: Type inference failed for: r3v3, types: [z7.e] */
    public final void m(t tVar) {
        Context context = this.f13290a;
        WifiP2pManager wifiP2pManager = (WifiP2pManager) context.getSystemService("wifip2p");
        this.f13292c = wifiP2pManager;
        if (wifiP2pManager != null) {
            WifiP2pManager.Channel channelInitialize = wifiP2pManager.initialize(context, context.getMainLooper(), null);
            this.f13293d = channelInitialize;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    this.f13292c.requestDeviceInfo(channelInitialize, new WifiP2pManager.DeviceInfoListener() { // from class: z7.e
                        @Override // android.net.wifi.p2p.WifiP2pManager.DeviceInfoListener
                        public final void onDeviceInfoAvailable(WifiP2pDevice wifiP2pDevice) {
                            j jVar = this.f13276a;
                            jVar.getClass();
                            String str = wifiP2pDevice == null ? null : wifiP2pDevice.deviceAddress;
                            Log.d("HUR-Hotspot", "requestDeviceInfo -> deviceAddress=" + str);
                            if (j.n(str) != null) {
                                jVar.f13294e = str;
                            }
                        }
                    });
                } catch (Throwable th) {
                    Log.w("HUR-Hotspot", "requestDeviceInfo failed: " + th);
                }
            }
            this.f13292c.removeGroup(this.f13293d, new i(0, tVar, this));
            return;
        }
        Log.w("HUR-Hotspot", "WiFi-P2P failed - falling back to LocalOnlyHotspot (video will cap to 720p)");
        this.f13295f = true;
        if (Build.VERSION.SDK_INT < 26) {
            tVar.L("No usable hotspot mechanism (P2P failed, LOH needs API 26+)");
            return;
        }
        WifiManager wifiManager = (WifiManager) this.f13290a.getSystemService("wifi");
        if (wifiManager == null) {
            tVar.L("WifiManager unavailable");
            return;
        }
        try {
            wifiManager.startLocalOnlyHotspot(new h(this, tVar), null);
        } catch (Throwable th2) {
            Log.e("HUR-Hotspot", "startLocalOnlyHotspot threw", th2);
            tVar.L("startLocalOnlyHotspot: " + th2.getMessage());
        }
    }
}
