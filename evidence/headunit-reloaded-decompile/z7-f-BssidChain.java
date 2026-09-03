package z7;

import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.media.session.t;
import android.util.Log;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class f implements WifiP2pManager.GroupInfoListener {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final /* synthetic */ j f13277a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final /* synthetic */ int f13278b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public final /* synthetic */ t f13279c;

    public /* synthetic */ f(int i5, t tVar, j jVar) {
        this.f13277a = jVar;
        this.f13278b = i5;
        this.f13279c = tVar;
    }

    /* JADX WARN: Code duplicated, block: B:142:0x040e A[Catch: Exception -> 0x0419, TRY_LEAVE, TryCatch #4 {Exception -> 0x0419, blocks: (B:140:0x0408, B:142:0x040e), top: B:180:0x0408 }] */
    /* JADX WARN: Code duplicated, block: B:145:0x0419  */
    /* JADX WARN: Code duplicated, block: B:148:0x0430 A[Catch: all -> 0x0438, TRY_LEAVE, TryCatch #6 {all -> 0x0438, blocks: (B:146:0x041d, B:148:0x0430), top: B:184:0x041d }] */
    /* JADX WARN: Code duplicated, block: B:151:0x0439  */
    /* JADX WARN: Code duplicated, block: B:154:0x0453  */
    /* JADX WARN: Code duplicated, block: B:155:0x0456  */
    /* JADX WARN: Code duplicated, block: B:158:0x046b  */
    /* JADX WARN: Code duplicated, block: B:159:0x046d  */
    /* JADX WARN: Code duplicated, block: B:162:0x048a  */
    /* JADX WARN: Code duplicated, block: B:163:0x048d A[DONT_INVERT] */
    /* JADX WARN: Code duplicated, block: B:164:0x048f  */
    /* JADX WARN: Code duplicated, block: B:180:0x0408 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Instruction removed from duplicated block: B:159:0x046d, please report this as an issue */
    @Override // android.net.wifi.p2p.WifiP2pManager.GroupInfoListener
    public final void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
        String name;
        char c10;
        String string;
        String str;
        String string2;
        String str2;
        NetworkInterface byName;
        String strD;
        int iIntValue;
        String str3;
        String str4;
        String str5;
        Object objInvoke;
        j jVar = this.f13277a;
        int i5 = this.f13278b;
        t tVar = this.f13279c;
        if (wifiP2pGroup == null) {
            if (i5 < 10) {
                new Handler(jVar.f13290a.getMainLooper()).postDelayed(new c.k(i5, tVar, jVar), 1000L);
                return;
            } else {
                tVar.L("P2P group info null");
                return;
            }
        }
        String networkName = wifiP2pGroup.getNetworkName();
        String passphrase = wifiP2pGroup.getPassphrase();
        try {
            Object objInvoke2 = wifiP2pGroup.getClass().getMethod("getInterface", null).invoke(wifiP2pGroup, null);
            name = (!(objInvoke2 instanceof String) || ((String) objInvoke2).isEmpty()) ? null : (String) objInvoke2;
        } catch (Throwable unused) {
        }
        if (name == null) {
            try {
                ArrayList list = Collections.list(NetworkInterface.getNetworkInterfaces());
                int size = list.size();
                int i6 = 0;
                while (true) {
                    if (i6 < size) {
                        Object obj = list.get(i6);
                        i6++;
                        NetworkInterface networkInterface = (NetworkInterface) obj;
                        ArrayList list2 = Collections.list(networkInterface.getInetAddresses());
                        c10 = 0;
                        try {
                            int size2 = list2.size();
                            int i10 = 0;
                            while (true) {
                                if (i10 < size2) {
                                    Object obj2 = list2.get(i10);
                                    i10++;
                                    if ("192.168.49.1".equals(((InetAddress) obj2).getHostAddress())) {
                                        name = networkInterface.getName();
                                    }
                                }
                            }
                        } catch (Exception unused2) {
                            name = null;
                        }
                    } else {
                        c10 = 0;
                        name = null;
                    }
                }
            } catch (Exception unused3) {
            }
        } else {
            c10 = 0;
        }
        String str6 = name;
        Log.d("HUR-Hotspot", "== BSSID source dump (iface=" + str6 + ") ==");
        StringBuilder sb = new StringBuilder("  getGroupOwnerBssid() = ");
        try {
            Object objInvoke3 = wifiP2pGroup.getClass().getMethod("getGroupOwnerBssid", null).invoke(wifiP2pGroup, null);
            string = objInvoke3 != null ? objInvoke3.toString() : null;
        } catch (Throwable unused4) {
        }
        sb.append(string);
        Log.d("HUR-Hotspot", sb.toString());
        try {
            str = wifiP2pGroup.getOwner() != null ? wifiP2pGroup.getOwner().deviceAddress : null;
        } catch (Exception e10) {
            str = "err:" + e10;
        }
        Log.d("HUR-Hotspot", "  group.getOwner().deviceAddress = " + str);
        Log.d("HUR-Hotspot", "  requestDeviceInfo (localDeviceAddress) = " + jVar.f13294e);
        Log.d("HUR-Hotspot", "  sysfs(" + str6 + ") = " + j.k(str6));
        StringBuilder sb2 = new StringBuilder("  sysfs(p2p0) = ");
        sb2.append(j.k("p2p0"));
        Log.d("HUR-Hotspot", sb2.toString());
        Log.d("HUR-Hotspot", "  sysfs(p2p-wlan0-0) = " + j.k("p2p-wlan0-0"));
        Log.d("HUR-Hotspot", "  IPv6 link-local(" + str6 + ") = " + j.h(str6));
        StringBuilder sb3 = new StringBuilder("  IPv6 link-local(any) = ");
        sb3.append(j.h(null));
        Log.d("HUR-Hotspot", sb3.toString());
        Log.d("HUR-Hotspot", "  ip link(" + str6 + ") = " + j.g(str6));
        Log.d("HUR-Hotspot", "  NetworkInterface(" + str6 + ") = " + j.j(str6));
        StringBuilder sb4 = new StringBuilder("  sysfs scan = ");
        sb4.append(j.l());
        Log.d("HUR-Hotspot", sb4.toString());
        try {
            Log.d("HUR-Hotspot", "  Settings.Secure wifi_p2p_device_address = " + Settings.Secure.getString(jVar.f13290a.getContentResolver(), "wifi_p2p_device_address"));
        } catch (Exception e11) {
            Log.d("HUR-Hotspot", "  Settings.Secure wifi_p2p_device_address = err:" + e11);
        }
        Log.d("HUR-Hotspot", "== end BSSID source dump ==");
        try {
            Object objInvoke4 = wifiP2pGroup.getClass().getMethod("getGroupOwnerBssid", null).invoke(wifiP2pGroup, null);
            string2 = objInvoke4 != null ? objInvoke4.toString() : null;
        } catch (Throwable unused5) {
        }
        String strN = j.n(string2);
        try {
            if (strN != null) {
                Log.d("HUR-Hotspot", "BSSID via getGroupOwnerBssid()=".concat(strN));
            } else {
                strN = j.h(str6);
                if (strN == null) {
                    strN = j.h(null);
                }
                if (strN != null) {
                    Log.d("HUR-Hotspot", "BSSID via IPv6 link-local=".concat(strN));
                } else {
                    strN = j.n(jVar.f13294e);
                    if (strN == null) {
                        strN = j.n(j.k(str6));
                        if (strN != null) {
                            Log.d("HUR-Hotspot", "BSSID via sysfs(" + str6 + ")=" + strN);
                        } else {
                            String strN2 = j.n(j.k("p2p0"));
                            if (strN2 != null) {
                                Log.d("HUR-Hotspot", "BSSID via sysfs(p2p0)=".concat(strN2));
                            } else {
                                strN2 = j.n(j.k("p2p-wlan0-0"));
                                if (strN2 != null) {
                                    Log.d("HUR-Hotspot", "BSSID via sysfs(p2p-wlan0-0)=".concat(strN2));
                                } else {
                                    strN2 = j.n(j.g(str6));
                                    if (strN2 != null) {
                                        Log.d("HUR-Hotspot", "BSSID via ip link(" + str6 + ")=" + strN2);
                                    } else {
                                        strN2 = j.n(j.j(str6));
                                        if (strN2 != null) {
                                            Log.d("HUR-Hotspot", "BSSID via NetworkInterface(" + str6 + ")=" + strN2);
                                        } else {
                                            try {
                                                if (wifiP2pGroup.getOwner() == null || (strN2 = j.n(wifiP2pGroup.getOwner().deviceAddress)) == null) {
                                                    try {
                                                        strN2 = j.n(Settings.Secure.getString(jVar.f13290a.getContentResolver(), "wifi_p2p_device_address"));
                                                        if (strN2 != null) {
                                                            Log.d("HUR-Hotspot", "BSSID via Settings.Secure=".concat(strN2));
                                                        } else {
                                                            strN2 = j.n(j.l());
                                                            if (strN2 != null) {
                                                                Log.d("HUR-Hotspot", "BSSID via sysfs scan=".concat(strN2));
                                                            } else {
                                                                try {
                                                                    ArrayList list3 = Collections.list(NetworkInterface.getNetworkInterfaces());
                                                                    int size3 = list3.size();
                                                                    int i11 = 0;
                                                                    while (true) {
                                                                        if (i11 >= size3) {
                                                                            strN2 = null;
                                                                            break;
                                                                        }
                                                                        Object obj3 = list3.get(i11);
                                                                        i11++;
                                                                        NetworkInterface networkInterface2 = (NetworkInterface) obj3;
                                                                        String name2 = networkInterface2.getName() == null ? "" : networkInterface2.getName();
                                                                        if (name2.startsWith("ap") || name2.startsWith("swlan") || name2.startsWith("wlan") || name2.contains("p2p")) {
                                                                            byte[] hardwareAddress = networkInterface2.getHardwareAddress();
                                                                            if (hardwareAddress != null) {
                                                                                if (hardwareAddress.length == 6) {
                                                                                    StringBuilder sb5 = new StringBuilder();
                                                                                    int i12 = 0;
                                                                                    for (int i13 = 6; i12 < i13; i13 = 6) {
                                                                                        if (i12 > 0) {
                                                                                            sb5.append(':');
                                                                                        }
                                                                                        Object[] objArr = new Object[1];
                                                                                        objArr[c10] = Byte.valueOf(hardwareAddress[i12]);
                                                                                        sb5.append(String.format("%02X", objArr));
                                                                                        i12++;
                                                                                    }
                                                                                    strN2 = j.n(sb5.toString());
                                                                                    if (strN2 != null) {
                                                                                        Log.d("HUR-Hotspot", "AP BSSID " + strN2 + " from interface " + name2);
                                                                                        break;
                                                                                    }
                                                                                    strN2 = null;
                                                                                    break;
                                                                                }
                                                                                continue;
                                                                            } else {
                                                                                continue;
                                                                            }
                                                                        }
                                                                    }
                                                                } catch (Exception unused6) {
                                                                }
                                                                if (strN2 != null) {
                                                                    Log.d("HUR-Hotspot", "BSSID via apBssid fallback=".concat(strN2));
                                                                } else {
                                                                    str2 = null;
                                                                }
                                                            }
                                                        }
                                                    } catch (Exception unused7) {
                                                    }
                                                } else {
                                                    Log.d("HUR-Hotspot", "BSSID via group owner=".concat(strN2));
                                                }
                                            } catch (Exception unused8) {
                                            }
                                        }
                                    }
                                }
                            }
                            str2 = strN2;
                        }
                        if (str6 != null) {
                            try {
                                byName = NetworkInterface.getByName(str6);
                                if (byName != null) {
                                    strD = j.d(byName);
                                    if (!j.f(strD)) {
                                        strD = j.c();
                                    }
                                } else {
                                    strD = j.c();
                                }
                            } catch (Exception unused9) {
                            }
                        } else {
                            strD = j.c();
                        }
                        objInvoke = wifiP2pGroup.getClass().getMethod("getFrequency", null).invoke(wifiP2pGroup, null);
                        if (objInvoke instanceof Integer) {
                            iIntValue = ((Integer) objInvoke).intValue();
                        } else {
                            iIntValue = 0;
                        }
                        StringBuilder sb6 = new StringBuilder("P2P group ready: SSID=");
                        sb6.append(networkName);
                        sb6.append(" iface=");
                        sb6.append(str6);
                        sb6.append(" bssid=");
                        if (str2 == null) {
                            str3 = "<MISSING>";
                        } else {
                            str3 = str2;
                        }
                        sb6.append(str3);
                        sb6.append(" ip=");
                        sb6.append(strD);
                        sb6.append(" freq=");
                        str4 = "unknown";
                        if (iIntValue == 0) {
                            str5 = "unknown";
                        } else {
                            str5 = iIntValue + "MHz";
                        }
                        sb6.append(str5);
                        sb6.append(" band=");
                        if (iIntValue >= 5000) {
                            str4 = "5GHz";
                        } else if (iIntValue > 0) {
                            str4 = "2.4GHz";
                        }
                        sb6.append(str4);
                        Log.d("HUR-Hotspot", sb6.toString());
                        tVar.M(new l5.b(networkName, passphrase, str2, strD, iIntValue));
                    }
                    Log.d("HUR-Hotspot", "BSSID via requestDeviceInfo=".concat(strN));
                }
            }
            objInvoke = wifiP2pGroup.getClass().getMethod("getFrequency", null).invoke(wifiP2pGroup, null);
            if (objInvoke instanceof Integer) {
                iIntValue = ((Integer) objInvoke).intValue();
            } else {
                iIntValue = 0;
            }
        } catch (Throwable unused10) {
        }
        str2 = strN;
        if (str6 != null) {
            byName = NetworkInterface.getByName(str6);
            if (byName != null) {
                strD = j.d(byName);
                if (!j.f(strD)) {
                    strD = j.c();
                }
            } else {
                strD = j.c();
            }
        } else {
            strD = j.c();
        }
        StringBuilder sb7 = new StringBuilder("P2P group ready: SSID=");
        sb7.append(networkName);
        sb7.append(" iface=");
        sb7.append(str6);
        sb7.append(" bssid=");
        if (str2 == null) {
            str3 = "<MISSING>";
        } else {
            str3 = str2;
        }
        sb7.append(str3);
        sb7.append(" ip=");
        sb7.append(strD);
        sb7.append(" freq=");
        str4 = "unknown";
        if (iIntValue == 0) {
            str5 = "unknown";
        } else {
            str5 = iIntValue + "MHz";
        }
        sb7.append(str5);
        sb7.append(" band=");
        if (iIntValue >= 5000) {
            str4 = "5GHz";
        } else if (iIntValue > 0) {
            str4 = "2.4GHz";
        }
        sb7.append(str4);
        Log.d("HUR-Hotspot", sb7.toString());
        tVar.M(new l5.b(networkName, passphrase, str2, strD, iIntValue));
    }
}
