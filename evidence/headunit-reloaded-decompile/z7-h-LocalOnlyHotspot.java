package z7;

import a0.s0;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.media.session.t;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class h extends WifiManager.LocalOnlyHotspotCallback {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final /* synthetic */ t f13285a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final /* synthetic */ j f13286b;

    public h(j jVar, t tVar) {
        this.f13286b = jVar;
        this.f13285a = tVar;
    }

    @Override // android.net.wifi.WifiManager.LocalOnlyHotspotCallback
    public final void onFailed(int i5) {
        Log.e("HUR-Hotspot", "LocalOnlyHotspot failed: " + i5);
        this.f13285a.L("LocalOnlyHotspot failed (" + i5 + ")");
    }

    /* JADX WARN: Code duplicated, block: B:34:0x00c1  */
    /* JADX WARN: Code duplicated, block: B:37:0x00e4 A[Catch: Exception -> 0x0106, TryCatch #0 {Exception -> 0x0106, blocks: (B:35:0x00c3, B:37:0x00e4, B:39:0x00f7, B:41:0x00fd), top: B:65:0x00c3 }] */
    /* JADX WARN: Code duplicated, block: B:78:0x0109 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:80:0x0106 A[SYNTHETIC] */
    @Override // android.net.wifi.WifiManager.LocalOnlyHotspotCallback
    public final void onStarted(final WifiManager.LocalOnlyHotspotReservation localOnlyHotspotReservation) {
        final String str;
        final String str2;
        String strN;
        String str3;
        String passphrase;
        String strN2;
        String[] strArr;
        String upperCase;
        File file;
        String line;
        this.f13286b.f13291b = localOnlyHotspotReservation;
        if (Build.VERSION.SDK_INT >= 30) {
            SoftApConfiguration softApConfiguration = localOnlyHotspotReservation.getSoftApConfiguration();
            if (softApConfiguration != null) {
                String ssid = softApConfiguration.getSsid();
                passphrase = softApConfiguration.getPassphrase();
                MacAddress bssid = softApConfiguration.getBssid();
                Log.d("HUR-Hotspot", "LocalOnlyHotspot SoftApConfiguration.getBssid() = " + bssid);
                StringBuilder sb = new StringBuilder("LocalOnlyHotspot Active Hotspot Mac = ");
                int i5 = 0;
                try {
                    ArrayList list = Collections.list(NetworkInterface.getNetworkInterfaces());
                    int size = list.size();
                    int i6 = 0;
                    while (true) {
                        if (i6 >= size) {
                            strArr = new String[]{"wlan1", "softap0", "wlan-hotspot-0"};
                            while (true) {
                                if (i5 < 3) {
                                    upperCase = "02:00:00:00:00:00";
                                    break;
                                }
                                file = new File("/sys/class/net/" + strArr[i5] + "/address");
                                if (file.exists()) {
                                    BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                                    line = bufferedReader.readLine();
                                    bufferedReader.close();
                                    if (line != null) {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                i5++;
                            }
                        } else {
                            Object obj = list.get(i6);
                            i6++;
                            NetworkInterface networkInterface = (NetworkInterface) obj;
                            String lowerCase = networkInterface.getName().toLowerCase();
                            if (lowerCase.equals("wlan1") || lowerCase.equals("wlan2") || lowerCase.contains("softap")) {
                                byte[] hardwareAddress = networkInterface.getHardwareAddress();
                                if (hardwareAddress != null) {
                                    StringBuilder sb2 = new StringBuilder();
                                    for (byte b8 : hardwareAddress) {
                                        sb2.append(String.format("%02X:", Byte.valueOf(b8)));
                                    }
                                    if (sb2.length() > 0) {
                                        sb2.deleteCharAt(sb2.length() - 1);
                                    }
                                    upperCase = sb2.toString();
                                }
                                strArr = new String[]{"wlan1", "softap0", "wlan-hotspot-0"};
                                while (true) {
                                    if (i5 < 3) {
                                        upperCase = "02:00:00:00:00:00";
                                        break;
                                    }
                                    try {
                                        file = new File("/sys/class/net/" + strArr[i5] + "/address");
                                        if (file.exists()) {
                                            BufferedReader bufferedReader2 = new BufferedReader(new FileReader(file));
                                            line = bufferedReader2.readLine();
                                            bufferedReader2.close();
                                            if (line != null && !line.isEmpty()) {
                                                upperCase = line.trim().toUpperCase();
                                                break;
                                            }
                                        } else {
                                            continue;
                                        }
                                    } catch (Exception unused) {
                                    }
                                    i5++;
                                }
                            }
                        }
                        sb.append(upperCase);
                        Log.d("HUR-Hotspot", sb.toString());
                        if (bssid != null) {
                            strN2 = j.n(bssid.toString());
                            str3 = ssid;
                        } else {
                            str3 = ssid;
                        }
                        str = str3;
                        str2 = passphrase;
                        strN = strN2;
                    }
                } catch (Exception e10) {
                    Log.e("Hotspot", "Failed to scan hardware network interfaces", e10);
                }
            } else {
                str3 = null;
                passphrase = null;
            }
            strN2 = null;
            str = str3;
            str2 = passphrase;
            strN = strN2;
        } else {
            WifiConfiguration wifiConfiguration = localOnlyHotspotReservation.getWifiConfiguration();
            if (wifiConfiguration != null) {
                String strA = j.a(wifiConfiguration.SSID);
                String strA2 = j.a(wifiConfiguration.preSharedKey);
                Log.d("HUR-Hotspot", "LocalOnlyHotspot WifiConfiguration.BSSID = " + wifiConfiguration.BSSID);
                str2 = strA2;
                str = strA;
                strN = j.n(wifiConfiguration.BSSID);
            } else {
                str = null;
                str2 = null;
                strN = null;
            }
        }
        if (str == null) {
            this.f13285a.L("Could not read hotspot config");
            return;
        }
        if (strN == null) {
            final t tVar = this.f13285a;
            new Thread(new Runnable() { // from class: z7.g
                @Override // java.lang.Runnable
                public final void run() {
                    String strI;
                    String strD;
                    h hVar = this.f13280d;
                    String str4 = str;
                    t tVar2 = tVar;
                    String str5 = str2;
                    WifiManager.LocalOnlyHotspotReservation localOnlyHotspotReservation2 = localOnlyHotspotReservation;
                    i0.p pVar = null;
                    for (int i10 = 0; i10 < 6 && pVar == null; i10++) {
                        try {
                            ArrayList list2 = Collections.list(NetworkInterface.getNetworkInterfaces());
                            int size2 = list2.size();
                            int i11 = 0;
                            while (true) {
                                if (i11 >= size2) {
                                    pVar = null;
                                    break;
                                }
                                Object obj2 = list2.get(i11);
                                i11++;
                                NetworkInterface networkInterface2 = (NetworkInterface) obj2;
                                String name = networkInterface2.getName() == null ? "" : networkInterface2.getName();
                                if (j.e(name) && (strI = j.i(networkInterface2)) != null && (strD = j.d(networkInterface2)) != null) {
                                    Log.d("HUR-Hotspot", "AP interface " + name + " -> bssid=" + strI + " ip=" + strD);
                                    pVar = new i0.p(strI, 27, strD);
                                    break;
                                }
                            }
                        } catch (Exception unused2) {
                        }
                        if (pVar == null) {
                            try {
                                Thread.sleep(600L);
                            } catch (InterruptedException unused3) {
                            }
                        }
                    }
                    if (pVar != null) {
                        StringBuilder sbZ = s0.z("LocalOnlyHotspot started, SSID=", str4, " bssid=");
                        sbZ.append((String) pVar.f5532e);
                        sbZ.append(" ip=");
                        sbZ.append((String) pVar.f5533f);
                        Log.d("HUR-Hotspot", sbZ.toString());
                        tVar2.M(new l5.b(str4, str5, (String) pVar.f5532e, (String) pVar.f5533f, 0));
                        return;
                    }
                    if (hVar.f13286b.f13295f) {
                        Log.e("HUR-Hotspot", "LocalOnlyHotspot yielded no BSSID and P2P already failed - giving up");
                        try {
                            localOnlyHotspotReservation2.close();
                        } catch (Exception unused4) {
                        }
                        hVar.f13286b.f13291b = null;
                        tVar2.L("Could not obtain AP BSSID (enable Location/GPS)");
                        return;
                    }
                    Log.w("HUR-Hotspot", "LocalOnlyHotspot yielded no BSSID - tearing down and using WiFi-P2P instead");
                    try {
                        localOnlyHotspotReservation2.close();
                    } catch (Exception unused5) {
                    }
                    j jVar = hVar.f13286b;
                    jVar.f13291b = null;
                    jVar.m(tVar2);
                }
            }, "AP-MAC-Probe").start();
            return;
        }
        Log.d("HUR-Hotspot", "LocalOnlyHotspot started, SSID=" + str + " bssid=" + strN);
        this.f13285a.M(new l5.b(str, str2, strN, j.c(), 0));
    }
}
