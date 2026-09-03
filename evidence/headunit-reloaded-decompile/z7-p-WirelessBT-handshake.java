package z7;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.lifecycle.a0;
import com.google.protobuf.t7;
import com.google.protobuf.t8;
import gb.xxy.hr.TransporterService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import m7.c0;
import m7.d0;
import m7.e0;
import m7.l0;
import m7.q;
import m7.r;
import m7.u;
import m7.v;
import m7.x;
import m7.z;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class p {

    /* JADX INFO: renamed from: k, reason: collision with root package name */
    public static final UUID f13309k = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");

    /* JADX INFO: renamed from: l, reason: collision with root package name */
    public static final UUID f13310l = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb");

    /* JADX INFO: renamed from: m, reason: collision with root package name */
    public static final a0 f13311m = new a0(null);

    /* JADX INFO: renamed from: n, reason: collision with root package name */
    public static volatile boolean f13312n = false;

    /* JADX INFO: renamed from: o, reason: collision with root package name */
    public static volatile int f13313o = 0;

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final Context f13314a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public volatile boolean f13315b = false;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public BluetoothServerSocket f13316c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public BluetoothServerSocket f13317d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public Thread f13318e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public Thread f13319f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public volatile BluetoothSocket f13320g;

    /* JADX INFO: renamed from: h, reason: collision with root package name */
    public j f13321h;

    /* JADX INFO: renamed from: i, reason: collision with root package name */
    public volatile l5.b f13322i;
    public d j;

    public p(TransporterService transporterService) {
        this.f13314a = transporterService.getApplicationContext();
    }

    public static boolean a() {
        try {
            BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
            if (defaultAdapter != null) {
                ParcelUuid[] parcelUuidArr = null;
                Object objInvoke = defaultAdapter.getClass().getMethod("getUuids", null).invoke(defaultAdapter, null);
                if (objInvoke instanceof ParcelUuid[]) {
                    parcelUuidArr = (ParcelUuid[]) objInvoke;
                } else if (objInvoke instanceof List) {
                    parcelUuidArr = (ParcelUuid[]) ((List) objInvoke).toArray(new ParcelUuid[0]);
                }
                if (parcelUuidArr != null) {
                    for (ParcelUuid parcelUuid : parcelUuidArr) {
                        if (parcelUuid != null) {
                            UUID uuid = parcelUuid.getUuid();
                            if (f13310l.equals(uuid)) {
                                Log.d("HUR-WirelessBT", "Local adapter already advertises HFP Hands-Free (" + uuid + ")");
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Throwable th) {
            Log.d("HUR-WirelessBT", "Could not read local HFP UUIDs (assuming none): " + th);
            return false;
        }
    }

    public static void b(OutputStream outputStream, String str) {
        Log.d("HUR-WirelessBT", "HFP RX: ".concat(str));
        if (str.startsWith("AT")) {
            if (str.startsWith("AT+BRSF")) {
                c(outputStream, "+BRSF: 20");
                c(outputStream, "OK");
                return;
            }
            if (str.startsWith("AT+CIND=?")) {
                c(outputStream, "+CIND: (\"service\",(0,1)),(\"call\",(0,1)),(\"callsetup\",(0,3)),(\"callheld\",(0,2)),(\"signal\",(0,5)),(\"roam\",(0,1)),(\"battchg\",(0,5))");
                c(outputStream, "OK");
                return;
            }
            if (str.startsWith("AT+CIND?")) {
                c(outputStream, "+CIND: 1,0,0,0,5,0,5");
                c(outputStream, "OK");
                return;
            }
            if (str.startsWith("AT+CHLD=?")) {
                c(outputStream, "+CHLD: (0,1,2,3)");
                c(outputStream, "OK");
                return;
            }
            if (str.startsWith("AT+BIND=?")) {
                c(outputStream, "+BIND: (1,2)");
                c(outputStream, "OK");
                return;
            }
            if (str.startsWith("AT+BIND?")) {
                c(outputStream, "+BIND: 1,1");
                c(outputStream, "+BIND: 2,1");
                c(outputStream, "OK");
            } else if (str.startsWith("AT+CGMI")) {
                c(outputStream, "+CGMI: \"HUR\"");
                c(outputStream, "OK");
            } else if (str.startsWith("AT+CGMM")) {
                c(outputStream, "+CGMM: \"Headunit Reloaded\"");
                c(outputStream, "OK");
            } else if (!str.startsWith("AT+CGMR")) {
                c(outputStream, "OK");
            } else {
                c(outputStream, "+CGMR: \"1.0\"");
                c(outputStream, "OK");
            }
        }
    }

    public static void c(OutputStream outputStream, String str) {
        synchronized (outputStream) {
            outputStream.write(("\r\n" + str + "\r\n").getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
        }
    }

    public static void d(OutputStream outputStream, String str) {
        synchronized (outputStream) {
            outputStream.write(str.concat("\r").getBytes(StandardCharsets.US_ASCII));
            outputStream.flush();
        }
    }

    public static void j(OutputStream outputStream, int i5, byte[] bArr) {
        int length = bArr.length;
        byte[] bArr2 = new byte[length + 4];
        bArr2[0] = (byte) ((length >> 8) & 255);
        bArr2[1] = (byte) (length & 255);
        bArr2[2] = (byte) 0;
        bArr2[3] = (byte) (i5 & 255);
        System.arraycopy(bArr, 0, bArr2, 4, length);
        synchronized (outputStream) {
            outputStream.write(bArr2);
            outputStream.flush();
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:123:0x0409  */
    /* JADX WARN: Code duplicated, block: B:124:0x0411  */
    public final void e(BluetoothSocket bluetoothSocket, l5.b bVar) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream;
        ByteArrayOutputStream byteArrayOutputStream2;
        InputStream inputStream = bluetoothSocket.getInputStream();
        OutputStream outputStream = bluetoothSocket.getOutputStream();
        z zVarF = m7.a0.f7749n.toBuilder();
        zVarF.f7898d |= 1;
        zVarF.f7899e = "HUR";
        zVarF.onChanged();
        String str = Build.MODEL;
        String str2 = str == null ? "HUR" : str;
        zVarF.f7898d |= 2;
        zVarF.f7900f = str2;
        zVarF.onChanged();
        int i5 = 4;
        zVarF.f7898d |= 4;
        zVarF.f7901g = "2024";
        zVarF.onChanged();
        zVarF.f7898d |= 16;
        zVarF.f7903i = "Headunit Reloaded";
        zVarF.onChanged();
        String str3 = str != null ? str : "HUR";
        zVarF.f7898d |= 32;
        zVarF.j = str3;
        zVarF.onChanged();
        String str4 = Build.ID;
        if (str4 == null) {
            str4 = "1";
        }
        zVarF.f7898d |= 64;
        zVarF.f7904k = str4;
        zVarF.onChanged();
        zVarF.f7898d |= 128;
        zVarF.f7905l = "1.0";
        zVarF.onChanged();
        m7.a0 a0VarC = zVarF.buildPartial();
        if (!a0VarC.isInitialized()) {
            throw com.google.protobuf.a.newUninitializedMessageException((t7) a0VarC);
        }
        x xVarK = e0.f7778k.toBuilder();
        xVarK.f7891d |= 1;
        xVarK.f7892e = 1;
        xVarK.onChanged();
        xVarK.f7891d |= 2;
        int i6 = 0;
        xVarK.f7893f = 0;
        xVarK.onChanged();
        xVarK.c(2412);
        xVarK.c(2437);
        xVarK.c(2462);
        xVarK.c(5180);
        xVarK.c(5220);
        xVarK.c(5745);
        t8 t8Var = xVarK.f7896i;
        if (t8Var == null) {
            xVarK.f7895h = a0VarC;
            xVarK.onChanged();
        } else {
            t8Var.i(a0VarC);
        }
        int i10 = 8;
        xVarK.f7891d |= 8;
        c0 c0VarF = d0.f7772h.toBuilder();
        String str5 = (String) bVar.f7188e;
        str5.getClass();
        c0VarF.f7763d |= 1;
        c0VarF.f7764e = str5;
        c0VarF.onChanged();
        c0VarF.f7763d |= 2;
        c0VarF.f7765f = 5288;
        c0VarF.onChanged();
        d0 d0VarC = c0VarF.buildPartial();
        if (!d0VarC.isInitialized()) {
            throw com.google.protobuf.a.newUninitializedMessageException((t7) d0VarC);
        }
        t8 t8Var2 = xVarK.f7897k;
        if (t8Var2 == null) {
            xVarK.j = d0VarC;
            xVarK.onChanged();
        } else {
            t8Var2.i(d0VarC);
        }
        xVarK.f7891d |= 16;
        e0 e0VarD = xVarK.buildPartial();
        if (!e0VarD.isInitialized()) {
            throw com.google.protobuf.a.newUninitializedMessageException((t7) e0VarD);
        }
        j(outputStream, 4, e0VarD.toByteArray());
        byte[] bArr = new byte[8192];
        ByteArrayOutputStream byteArrayOutputStream3 = new ByteArrayOutputStream();
        while (this.f13315b) {
            try {
                int i11 = inputStream.read(bArr);
                if (i11 < 0) {
                    Log.d("HUR-WirelessBT", "RFCOMM stream closed by phone");
                    return;
                }
                byteArrayOutputStream3.write(bArr, i6, i11);
                byte[] byteArray = byteArrayOutputStream3.toByteArray();
                int i12 = 0;
                while (byteArray.length - i12 >= i5) {
                    int i13 = (byteArray[i12 + 1] & 255) | ((byteArray[i12] & 255) << i10);
                    int i14 = (byteArray[i12 + 3] & 255) | ((byteArray[i12 + 2] & 255) << i10);
                    int i15 = i13 + 4;
                    if (byteArray.length - i12 < i15) {
                        InputStream inputStream2 = inputStream;
                        byte[] bArr2 = bArr;
                        byteArrayOutputStream = byteArrayOutputStream3;
                        byteArrayOutputStream.reset();
                        if (i12 < byteArray.length) {
                            byteArrayOutputStream2 = byteArrayOutputStream;
                            byteArrayOutputStream2.write(byteArray, i12, byteArray.length - i12);
                        } else {
                            byteArrayOutputStream2 = byteArrayOutputStream;
                        }
                        byteArrayOutputStream3 = byteArrayOutputStream2;
                        inputStream = inputStream2;
                        bArr = bArr2;
                        i6 = 0;
                        i10 = 8;
                        i5 = 4;
                    } else {
                        byte[] bArr3 = new byte[i13];
                        System.arraycopy(byteArray, i12 + 4, bArr3, i6, i13);
                        int i16 = i15 + i12;
                        InputStream inputStream3 = inputStream;
                        byte[] bArr4 = bArr;
                        ByteArrayOutputStream byteArrayOutputStream4 = byteArrayOutputStream3;
                        if (i14 != 2) {
                            switch (i14) {
                                case 5:
                                    try {
                                        l0 l0Var = (l0) l0.f7844m.parseFrom(bArr3);
                                        StringBuilder sb = new StringBuilder("WifiVersionResponse: status=");
                                        v vVarC = v.c(l0Var.f7849h);
                                        if (vVarC == null) {
                                            vVarC = v.STATUS_UNSOLICITED_MESSAGE;
                                        }
                                        sb.append(vVarC);
                                        sb.append(" serial=");
                                        sb.append(l0Var.g());
                                        Log.d("HUR-WirelessBT", sb.toString());
                                        q qVarF = r.f7862h.toBuilder();
                                        String str6 = (String) bVar.f7188e;
                                        str6.getClass();
                                        qVarF.f7859d |= 1;
                                        qVarF.f7860e = str6;
                                        qVarF.onChanged();
                                        qVarF.f7859d |= 2;
                                        try {
                                            qVarF.f7861f = 5288;
                                            qVarF.onChanged();
                                            r rVarC = qVarF.buildPartial();
                                            if (!rVarC.isInitialized()) {
                                                throw com.google.protobuf.a.newUninitializedMessageException((t7) rVarC);
                                            }
                                            j(outputStream, 1, rVarC.toByteArray());
                                        } catch (Exception e10) {
                                            e = e10;
                                            Log.e("HUR-WirelessBT", "dispatch id=" + i14 + " failed", e);
                                        }
                                    } catch (Exception e11) {
                                        e = e11;
                                    }
                                    break;
                                case 6:
                                    m7.d dVar = (m7.d) m7.d.f7767i.parseFrom(bArr3);
                                    StringBuilder sb2 = new StringBuilder("WifiConnectStatus: ");
                                    v vVarC2 = v.c(dVar.f7769e);
                                    if (vVarC2 == null) {
                                        vVarC2 = v.STATUS_UNSOLICITED_MESSAGE;
                                    }
                                    sb2.append(vVarC2);
                                    sb2.append(" hint=");
                                    sb2.append(dVar.f());
                                    Log.d("HUR-WirelessBT", sb2.toString());
                                    break;
                                case 7:
                                    u uVar = (u) u.j.parseFrom(bArr3);
                                    StringBuilder sb3 = new StringBuilder("WifiStartResponse: status=");
                                    v vVarC3 = v.c(uVar.f7876g);
                                    if (vVarC3 == null) {
                                        vVarC3 = v.STATUS_UNSOLICITED_MESSAGE;
                                    }
                                    sb3.append(vVarC3);
                                    sb3.append(" - phone should now open the projection socket");
                                    Log.d("HUR-WirelessBT", sb3.toString());
                                    break;
                                case 8:
                                    m7.l lVar = (m7.l) m7.l.f7839h.parseFrom(bArr3);
                                    m7.n nVarF = m7.o.f7854g.toBuilder();
                                    long j = lVar.f7841e;
                                    nVarF.f7852d |= 1;
                                    nVarF.f7853e = j;
                                    nVarF.onChanged();
                                    m7.o oVarC = nVarF.buildPartial();
                                    if (!oVarC.isInitialized()) {
                                        throw com.google.protobuf.a.newUninitializedMessageException((t7) oVarC);
                                    }
                                    j(outputStream, 9, oVarC.toByteArray());
                                    break;
                                    break;
                                case 9:
                                    break;
                                default:
                                    try {
                                        Log.d("HUR-WirelessBT", "Unhandled BT frame id=" + i14 + " len=" + i13);
                                    } catch (Exception e12) {
                                        e = e12;
                                        Log.e("HUR-WirelessBT", "dispatch id=" + i14 + " failed", e);
                                    }
                                    break;
                            }
                        } else {
                            StringBuilder sb4 = new StringBuilder("WifiInfoRequest -> sending creds: ssid=");
                            String str7 = (String) bVar.f7185b;
                            String str8 = (String) bVar.f7187d;
                            String str9 = (String) bVar.f7186c;
                            sb4.append(str7);
                            sb4.append(" key=");
                            sb4.append(str9 == null ? "<null!>" : "(" + str9.length() + " chars)");
                            sb4.append(" bssid=");
                            sb4.append(str8 == null ? "<MISSING>" : str8);
                            sb4.append(" security=WPA2_PERSONAL ip=");
                            sb4.append((String) bVar.f7188e);
                            sb4.append(":5288");
                            Log.d("HUR-WirelessBT", sb4.toString());
                            if (str8 == null) {
                                Log.e("HUR-WirelessBT", "No BSSID available - the phone will likely FAIL to join the AP. Ensure Location (GPS) is enabled on this head-unit device.");
                            }
                            try {
                                Thread.sleep(1000L);
                            } catch (InterruptedException unused) {
                            }
                            m7.g gVarF = m7.i.f7819k.toBuilder();
                            String str10 = (String) bVar.f7185b;
                            str10.getClass();
                            try {
                                gVarF.f7790d |= 1;
                                gVarF.f7791e = str10;
                                gVarF.onChanged();
                                m7.h hVar = m7.h.UNKNOWN_SECURITY_MODE;
                                gVarF.f7790d |= 8;
                                try {
                                    gVarF.f7794h = 8;
                                    gVarF.onChanged();
                                    m7.f fVar = m7.f.STATIC;
                                    gVarF.f7790d |= 16;
                                    try {
                                        gVarF.f7795i = 0;
                                        gVarF.onChanged();
                                        String str11 = (String) bVar.f7186c;
                                        if (str11 != null) {
                                            try {
                                                gVarF.f7790d |= 2;
                                                gVarF.f7792f = str11;
                                                gVarF.onChanged();
                                            } catch (Exception e13) {
                                                e = e13;
                                                Log.e("HUR-WirelessBT", "dispatch id=" + i14 + " failed", e);
                                            }
                                        }
                                        String str12 = (String) bVar.f7187d;
                                        if (str12 != null) {
                                            gVarF.f7790d |= 4;
                                            gVarF.f7793g = str12;
                                            gVarF.onChanged();
                                        }
                                        m7.i iVarC = gVarF.buildPartial();
                                        if (!iVarC.isInitialized()) {
                                            throw com.google.protobuf.a.newUninitializedMessageException((t7) iVarC);
                                        }
                                        j(outputStream, 3, iVarC.toByteArray());
                                        Log.d("HUR-WirelessBT", "Sent WifiInfoResponse for SSID=" + ((String) bVar.f7185b));
                                    } catch (Exception e14) {
                                        e = e14;
                                        Log.e("HUR-WirelessBT", "dispatch id=" + i14 + " failed", e);
                                        inputStream = inputStream3;
                                        bArr = bArr4;
                                        i12 = i16;
                                        byteArrayOutputStream3 = byteArrayOutputStream4;
                                        i6 = 0;
                                        i10 = 8;
                                        i5 = 4;
                                    }
                                } catch (Exception e15) {
                                    e = e15;
                                    Log.e("HUR-WirelessBT", "dispatch id=" + i14 + " failed", e);
                                    inputStream = inputStream3;
                                    bArr = bArr4;
                                    i12 = i16;
                                    byteArrayOutputStream3 = byteArrayOutputStream4;
                                    i6 = 0;
                                    i10 = 8;
                                    i5 = 4;
                                }
                            } catch (Exception e16) {
                                e = e16;
                            }
                        }
                        inputStream = inputStream3;
                        bArr = bArr4;
                        i12 = i16;
                        byteArrayOutputStream3 = byteArrayOutputStream4;
                        i6 = 0;
                        i10 = 8;
                        i5 = 4;
                    }
                }
                InputStream inputStream4 = inputStream;
                byte[] bArr5 = bArr;
                byteArrayOutputStream = byteArrayOutputStream3;
                byteArrayOutputStream.reset();
                if (i12 < byteArray.length) {
                    byteArrayOutputStream2 = byteArrayOutputStream;
                    byteArrayOutputStream2.write(byteArray, i12, byteArray.length - i12);
                } else {
                    byteArrayOutputStream2 = byteArrayOutputStream;
                }
                byteArrayOutputStream3 = byteArrayOutputStream2;
                inputStream = inputStream4;
                bArr = bArr5;
                i6 = 0;
                i10 = 8;
                i5 = 4;
            } catch (IOException e17) {
                Log.d("HUR-WirelessBT", "Bootstrap RFCOMM closed by phone (normal after projection starts): " + e17.getMessage());
                return;
            }
        }
    }

    public final void f(boolean z6) {
        Class<?> cls = Integer.TYPE;
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null) {
            if (Build.VERSION.SDK_INT >= 31 && h5.a.o(this.f13314a, "android.permission.BLUETOOTH_CONNECT") != 0) {
                return;
            }
            int i5 = z6 ? 23 : 21;
            try {
                if (defaultAdapter.getScanMode() == i5) {
                    return;
                }
            } catch (Throwable unused) {
            }
            try {
                try {
                    defaultAdapter.getClass().getMethod("setScanMode", cls, cls).invoke(defaultAdapter, Integer.valueOf(i5), 0);
                } catch (NoSuchMethodException unused2) {
                    defaultAdapter.getClass().getMethod("setScanMode", cls).invoke(defaultAdapter, Integer.valueOf(i5));
                }
                Log.d("HUR-WirelessBT", "setDiscoverable(" + z6 + ")");
            } catch (Throwable th) {
                Log.w("HUR-WirelessBT", "setScanMode not permitted on this device: " + th);
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:24:0x003c A[Catch: all -> 0x0013, TRY_LEAVE, TryCatch #1 {, blocks: (B:4:0x0002, B:5:0x000b, B:7:0x000f, B:12:0x0016, B:14:0x001f, B:15:0x0021, B:17:0x0025, B:21:0x0031, B:20:0x002a, B:22:0x0033, B:24:0x003c), top: B:32:0x0002, inners: #0 }] */
    public final synchronized void g() {
        Thread thread;
        this.f13315b = false;
        f13312n = false;
        f13313o = 0;
        f(false);
        try {
            BluetoothServerSocket bluetoothServerSocket = this.f13316c;
            if (bluetoothServerSocket != null) {
                bluetoothServerSocket.close();
            }
        } catch (Exception unused) {
        }
        this.f13316c = null;
        h();
        d dVar = this.j;
        if (dVar != null) {
            dVar.f13275e = false;
            try {
                BluetoothServerSocket bluetoothServerSocket2 = dVar.f13274d;
                if (bluetoothServerSocket2 != null) {
                    bluetoothServerSocket2.close();
                }
            } catch (IOException e10) {
                Log.e("HUR-DummyA2dp", "Could not close A2DP server socket", e10);
            }
            this.j = null;
            i();
            this.f13322i = null;
            thread = this.f13318e;
            if (thread != null) {
                thread.interrupt();
                this.f13318e = null;
            }
        } else {
            i();
            this.f13322i = null;
            thread = this.f13318e;
            if (thread != null) {
                thread.interrupt();
                this.f13318e = null;
            }
        }
        throw th;
    }

    public final synchronized void h() {
        try {
            BluetoothServerSocket bluetoothServerSocket = this.f13317d;
            if (bluetoothServerSocket != null) {
                bluetoothServerSocket.close();
            }
        } catch (Exception unused) {
        }
        this.f13317d = null;
        try {
            if (this.f13320g != null) {
                this.f13320g.close();
            }
        } catch (Exception unused2) {
        }
        this.f13320g = null;
        Thread thread = this.f13319f;
        if (thread != null) {
            thread.interrupt();
            this.f13319f = null;
        }
    }

    public final void i() {
        WifiP2pManager.Channel channel;
        j jVar = this.f13321h;
        if (jVar != null) {
            try {
                WifiManager.LocalOnlyHotspotReservation localOnlyHotspotReservation = jVar.f13291b;
                if (localOnlyHotspotReservation != null) {
                    localOnlyHotspotReservation.close();
                    jVar.f13291b = null;
                }
            } catch (Exception unused) {
            }
            try {
                WifiP2pManager wifiP2pManager = jVar.f13292c;
                if (wifiP2pManager != null && (channel = jVar.f13293d) != null) {
                    wifiP2pManager.removeGroup(channel, null);
                }
            } catch (Exception unused2) {
            }
            this.f13321h = null;
        }
    }
}
