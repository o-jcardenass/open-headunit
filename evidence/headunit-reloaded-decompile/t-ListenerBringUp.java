package android.support.v4.media.session;

import android.animation.Animator;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureSession;
import androidx.media.session.MediaButtonReceiver;
import b2.g0;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.protobuf.b8;
import d2.f0;
import d2.f1;
import d2.n0;
import d2.s1;
import e2.h1;
import g4.a0;
import g4.z;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import k4.c0;
import k4.m0;
import org.xmlpull.v1.XmlPullParserException;
import s0.a1;
import s0.o0;
import x3.g1;
import x3.j1;
import y4.d1;
import y4.e0;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class t implements c1.m, h1, g4.q, t3.d, o2.e, x3.o {

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public static int f359g;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final /* synthetic */ int f360d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public Object f361e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public Object f362f;

    public /* synthetic */ t(int i5, Object obj, Object obj2, boolean z6) {
        this.f360d = i5;
        this.f362f = obj;
        this.f361e = obj2;
    }

    public static void C(Bundle bundle) {
        if (bundle != null) {
            bundle.setClassLoader(t.class.getClassLoader());
        }
    }

    public static Calendar E(BigDecimal bigDecimal, Calendar calendar) {
        if (bigDecimal == null) {
            return null;
        }
        Calendar calendar2 = (Calendar) calendar.clone();
        BigDecimal bigDecimal2 = BigDecimal.ZERO;
        if (bigDecimal.compareTo(bigDecimal2) == -1) {
            bigDecimal = bigDecimal.add(BigDecimal.valueOf(24.0d));
            calendar2.add(11, -24);
        }
        String[] strArrSplit = bigDecimal.toPlainString().split("\\.");
        int i5 = Integer.parseInt(strArrSplit[0]);
        BigDecimal scale = new BigDecimal("0." + strArrSplit[1]).multiply(BigDecimal.valueOf(60L)).setScale(0, RoundingMode.HALF_EVEN);
        if (scale.intValue() == 60) {
            i5++;
        } else {
            bigDecimal2 = scale;
        }
        if (i5 == 24) {
            i5 = 0;
        }
        calendar2.set(11, i5);
        calendar2.set(12, bigDecimal2.intValue());
        calendar2.set(13, 0);
        calendar2.set(14, 0);
        calendar2.setTimeZone(calendar.getTimeZone());
        return calendar2;
    }

    public static int H(int i5, int i6) {
        int i10 = 0;
        int i11 = 0;
        for (int i12 = 0; i12 < i5; i12++) {
            i10++;
            if (i10 == i6) {
                i11++;
                i10 = 0;
            } else if (i10 > i6) {
                i11++;
                i10 = 1;
            }
        }
        return i10 + 1 > i6 ? i11 + 1 : i11;
    }

    public static BigDecimal U(BigDecimal bigDecimal) {
        return bigDecimal.setScale(8, RoundingMode.HALF_EVEN);
    }

    public static Bundle W(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        C(bundle);
        try {
            bundle.isEmpty();
            return bundle;
        } catch (BadParcelableException unused) {
            Log.e("MediaSessionCompat", "Could not unparcel the data.");
            return null;
        }
    }

    public static BigDecimal l(BigDecimal bigDecimal) {
        return U(bigDecimal.multiply(BigDecimal.valueOf(0.017453292519943295d)));
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r5v0 */
    /* JADX WARN: Type inference failed for: r5v1, types: [f1.n] */
    /* JADX WARN: Type inference failed for: r5v10 */
    /* JADX WARN: Type inference failed for: r5v11 */
    /* JADX WARN: Type inference failed for: r5v3 */
    /* JADX WARN: Type inference failed for: r5v4, types: [f1.n] */
    /* JADX WARN: Type inference failed for: r5v5, types: [java.lang.Object] */
    /* JADX WARN: Type inference failed for: r5v6 */
    /* JADX WARN: Type inference failed for: r5v7 */
    /* JADX WARN: Type inference failed for: r5v8 */
    /* JADX WARN: Type inference failed for: r5v9 */
    /* JADX WARN: Type inference failed for: r6v0 */
    /* JADX WARN: Type inference failed for: r6v1 */
    /* JADX WARN: Type inference failed for: r6v10 */
    /* JADX WARN: Type inference failed for: r6v11 */
    /* JADX WARN: Type inference failed for: r6v2 */
    /* JADX WARN: Type inference failed for: r6v3, types: [u0.d] */
    /* JADX WARN: Type inference failed for: r6v4 */
    /* JADX WARN: Type inference failed for: r6v5 */
    /* JADX WARN: Type inference failed for: r6v6, types: [u0.d] */
    /* JADX WARN: Type inference failed for: r6v8 */
    /* JADX WARN: Type inference failed for: r6v9 */
    /* JADX WARN: Type inference failed for: r7v5 */
    public static void m(f0 f0Var) {
        n0 n0Var = f0Var.f3355z;
        int i5 = 0;
        if (n0Var.f3441c == 5 && !n0Var.f3443e && !n0Var.f3442d && !f0Var.G && f0Var.F()) {
            f1.n nVar = (f1.n) f0Var.f3354y.f3151f;
            if ((nVar.f4341g & 256) != 0) {
                while (nVar != null) {
                    if ((nVar.f4340f & 256) != 0) {
                        ?? F = nVar;
                        ?? dVar = 0;
                        while (F != 0) {
                            if (F instanceof d2.p) {
                                d2.p pVar = (d2.p) F;
                                pVar.b0(d2.f.r(pVar, 256));
                            } else if ((F.f4340f & 256) != 0 && (F instanceof d2.m)) {
                                f1.n nVar2 = ((d2.m) F).f3432r;
                                int i6 = 0;
                                while (nVar2 != null) {
                                    if ((nVar2.f4340f & 256) != 0) {
                                        i6++;
                                        if (i6 == 1) {
                                            F = F;
                                            dVar = dVar;
                                            dVar = dVar;
                                            F = nVar2;
                                        } else {
                                            if (dVar == 0) {
                                                dVar = new u0.d(new f1.n[16]);
                                            }
                                            if (F != 0) {
                                                dVar.c(F);
                                                F = 0;
                                            }
                                            dVar.c(nVar2);
                                        }
                                    } else {
                                        F = F;
                                        dVar = dVar;
                                    }
                                    nVar2 = nVar2.f4343i;
                                    F = F;
                                    dVar = dVar;
                                }
                                if (i6 == 1) {
                                    F = F;
                                    dVar = dVar;
                                } else {
                                    F = F;
                                    dVar = dVar;
                                }
                            }
                            F = d2.f.f(dVar);
                        }
                    }
                    if ((nVar.f4341g & 256) == 0) {
                        break;
                    } else {
                        nVar = nVar.f4343i;
                    }
                }
            }
        }
        f0Var.F = false;
        u0.d dVarW = f0Var.w();
        int i10 = dVarW.f11399f;
        if (i10 > 0) {
            Object[] objArr = dVarW.f11397d;
            do {
                m((f0) objArr[i5]);
                i5++;
            } while (i5 < i10);
        }
    }

    public void A(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.A(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public synchronized void B(s9.i iVar) {
        try {
            s9.i iVar2 = (s9.i) this.f362f;
            if (iVar2 != null) {
                iVar2.f11140c = iVar;
                this.f362f = iVar;
            } else {
                if (((s9.i) this.f361e) != null) {
                    throw new IllegalStateException("Head present, but no tail");
                }
                this.f362f = iVar;
                this.f361e = iVar;
            }
            notifyAll();
        } catch (Throwable th) {
            throw th;
        }
    }

    public View D(int i5, int i6, int i10, int i11) {
        View viewU;
        d1 d1Var = (d1) this.f362f;
        e0 e0Var = (e0) this.f361e;
        int iD = e0Var.d();
        int iC = e0Var.c();
        int i12 = i6 > i5 ? 1 : -1;
        View view = null;
        while (i5 != i6) {
            switch (e0Var.f12880a) {
                case 0:
                    viewU = e0Var.f12881b.u(i5);
                    break;
                default:
                    viewU = e0Var.f12881b.u(i5);
                    break;
            }
            int iB = e0Var.b(viewU);
            int iA = e0Var.a(viewU);
            d1Var.f12871b = iD;
            d1Var.f12872c = iC;
            d1Var.f12873d = iB;
            d1Var.f12874e = iA;
            if (i10 != 0) {
                d1Var.f12870a = i10;
                if (d1Var.a()) {
                    return viewU;
                }
            }
            if (i11 != 0) {
                d1Var.f12870a = i11;
                if (d1Var.a()) {
                    view = viewU;
                }
            }
            i5 += i12;
        }
        return view;
    }

    public g0 F() {
        return (g0) ((a1) this.f362f).getValue();
    }

    public f9.d G() {
        Matcher matcher = (Matcher) this.f361e;
        return a.a.M(matcher.start(), matcher.end());
    }

    public void I() {
        ((SparseIntArray) this.f361e).clear();
    }

    public boolean J(View view) {
        d1 d1Var = (d1) this.f362f;
        e0 e0Var = (e0) this.f361e;
        int iD = e0Var.d();
        int iC = e0Var.c();
        int iB = e0Var.b(view);
        int iA = e0Var.a(view);
        d1Var.f12871b = iD;
        d1Var.f12872c = iC;
        d1Var.f12873d = iB;
        d1Var.f12874e = iA;
        d1Var.f12870a = 24579;
        return d1Var.a();
    }

    public AutofillId K(long j) {
        if (Build.VERSION.SDK_INT < 29) {
            return null;
        }
        ContentCaptureSession contentCaptureSessionG = q.g(this.f361e);
        s9.h hVarS = b6.b.s((View) this.f362f);
        Objects.requireNonNull(hVarS);
        return h2.b.a(contentCaptureSessionG, b6.a.g(hVarS.f11136a), j);
    }

    public void L(String str) {
        Log.e("HUR-WirelessBT", "Could not create WiFi AP, wireless AA unsupported: " + str);
        z7.p.f13311m.e(Boolean.FALSE);
        ((z7.p) this.f362f).i();
    }

    public void M(l5.b bVar) {
        String str;
        ((z7.p) this.f362f).f13322i = bVar;
        z7.p.f13313o = bVar.f7184a;
        StringBuilder sb = new StringBuilder("AP ready: SSID=");
        sb.append((String) bVar.f7185b);
        sb.append(" ip=");
        sb.append((String) bVar.f7188e);
        sb.append(" freq=");
        if (bVar.f7184a == 0) {
            str = "unknown";
        } else {
            str = bVar.f7184a + "MHz";
        }
        sb.append(str);
        sb.append(" -> ");
        sb.append(bVar.f7184a >= 5000 ? "5GHz (1080p ok)" : "not 5GHz (video will cap to 720p)");
        Log.d("HUR-WirelessBT", sb.toString());
        final z7.p pVar = (z7.p) this.f362f;
        BluetoothAdapter bluetoothAdapter = (BluetoothAdapter) this.f361e;
        synchronized (pVar) {
            if (pVar.f13315b) {
                return;
            }
            try {
                pVar.f13316c = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("AndroidAuto", z7.p.f13309k);
                if (z7.p.a()) {
                    Log.d("HUR-WirelessBT", "Native HFP present on this device - skipping dummy HFP SDP record");
                } else {
                    try {
                        pVar.f13317d = bluetoothAdapter.listenUsingRfcommWithServiceRecord("HFP", z7.p.f13310l);
                        Log.d("HUR-WirelessBT", "No native HFP - dummy HFP SDP record registered");
                    } catch (Throwable th) {
                        Log.w("HUR-WirelessBT", "Could not register dummy HFP SDP record", th);
                    }
                }
                try {
                    z7.d dVar = new z7.d("AA-BT-A2dpDummy");
                    dVar.f13275e = true;
                    try {
                        dVar.f13274d = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("HUR Audio Speaker", z7.d.f13273f);
                        Log.d("HUR-DummyA2dp", "Dummy A2DP sink SDP record registered");
                    } catch (IOException e10) {
                        Log.e("HUR-DummyA2dp", "Could not register dummy A2DP sink", e10);
                        dVar.f13274d = null;
                    }
                    pVar.j = dVar;
                    dVar.start();
                } catch (Throwable th2) {
                    Log.w("HUR-WirelessBT", "Could not start dummy A2DP sink", th2);
                }
                pVar.f13315b = true;
                z7.p.f13311m.e(Boolean.TRUE);
                final int i5 = 0;
                Thread thread = new Thread(new Runnable() { // from class: z7.o
                    @Override // java.lang.Runnable
                    public final void run() {
                        switch (i5) {
                            case 0:
                                p pVar2 = pVar;
                                while (pVar2.f13315b) {
                                    try {
                                        BluetoothSocket bluetoothSocketAccept = pVar2.f13316c.accept();
                                        Log.d("HUR-WirelessBT", "Phone connected over RFCOMM, starting handshake");
                                        p.f13312n = true;
                                        n.a();
                                        pVar2.f(false);
                                        l5.b bVar2 = pVar2.f13322i;
                                        if (bVar2 != null) {
                                            try {
                                                pVar2.e(bluetoothSocketAccept, bVar2);
                                            } catch (Throwable th3) {
                                                try {
                                                    Log.e("HUR-WirelessBT", "Handshake error", th3);
                                                } catch (Throwable th4) {
                                                    try {
                                                        bluetoothSocketAccept.close();
                                                        break;
                                                    } catch (Exception unused) {
                                                    }
                                                    throw th4;
                                                }
                                            }
                                            bluetoothSocketAccept.close();
                                            break;
                                        } else {
                                            Log.e("HUR-WirelessBT", "No AP info available, closing RFCOMM connection");
                                            try {
                                                bluetoothSocketAccept.close();
                                            } catch (Exception unused2) {
                                            }
                                        }
                                    } catch (Exception e11) {
                                        if (pVar2.f13315b) {
                                            Log.e("HUR-WirelessBT", "RFCOMM accept failed", e11);
                                            return;
                                        }
                                        return;
                                    }
                                }
                                return;
                            default:
                                p pVar3 = pVar;
                                while (pVar3.f13315b) {
                                    try {
                                        BluetoothSocket bluetoothSocketAccept2 = pVar3.f13317d.accept();
                                        Log.d("HUR-WirelessBT", "Incoming HFP RFCOMM connection - stopping advertising");
                                        pVar3.f(false);
                                        BluetoothSocket bluetoothSocket = pVar3.f13320g;
                                        pVar3.f13320g = bluetoothSocketAccept2;
                                        if (bluetoothSocket != null) {
                                            try {
                                                bluetoothSocket.close();
                                            } catch (Exception unused3) {
                                            }
                                        }
                                        new Thread(new c.n(pVar3, 5, bluetoothSocketAccept2), "AA-BT-HFP-Responder").start();
                                    } catch (Exception e12) {
                                        if (pVar3.f13315b) {
                                            Log.d("HUR-WirelessBT", "HFP accept ended: " + e12);
                                            return;
                                        }
                                        return;
                                    }
                                }
                                return;
                        }
                    }
                }, "AA-BT-Accept");
                pVar.f13318e = thread;
                thread.start();
                if (pVar.f13317d != null) {
                    final int i6 = 1;
                    Thread thread2 = new Thread(new Runnable() { // from class: z7.o
                        @Override // java.lang.Runnable
                        public final void run() {
                            switch (i6) {
                                case 0:
                                    p pVar2 = pVar;
                                    while (pVar2.f13315b) {
                                        try {
                                            BluetoothSocket bluetoothSocketAccept = pVar2.f13316c.accept();
                                            Log.d("HUR-WirelessBT", "Phone connected over RFCOMM, starting handshake");
                                            p.f13312n = true;
                                            n.a();
                                            pVar2.f(false);
                                            l5.b bVar2 = pVar2.f13322i;
                                            if (bVar2 != null) {
                                                try {
                                                    pVar2.e(bluetoothSocketAccept, bVar2);
                                                } catch (Throwable th3) {
                                                    try {
                                                        Log.e("HUR-WirelessBT", "Handshake error", th3);
                                                    } catch (Throwable th4) {
                                                        try {
                                                            bluetoothSocketAccept.close();
                                                            break;
                                                        } catch (Exception unused) {
                                                        }
                                                        throw th4;
                                                    }
                                                }
                                                bluetoothSocketAccept.close();
                                                break;
                                            } else {
                                                Log.e("HUR-WirelessBT", "No AP info available, closing RFCOMM connection");
                                                try {
                                                    bluetoothSocketAccept.close();
                                                } catch (Exception unused2) {
                                                }
                                            }
                                        } catch (Exception e11) {
                                            if (pVar2.f13315b) {
                                                Log.e("HUR-WirelessBT", "RFCOMM accept failed", e11);
                                                return;
                                            }
                                            return;
                                        }
                                    }
                                    return;
                                default:
                                    p pVar3 = pVar;
                                    while (pVar3.f13315b) {
                                        try {
                                            BluetoothSocket bluetoothSocketAccept2 = pVar3.f13317d.accept();
                                            Log.d("HUR-WirelessBT", "Incoming HFP RFCOMM connection - stopping advertising");
                                            pVar3.f(false);
                                            BluetoothSocket bluetoothSocket = pVar3.f13320g;
                                            pVar3.f13320g = bluetoothSocketAccept2;
                                            if (bluetoothSocket != null) {
                                                try {
                                                    bluetoothSocket.close();
                                                } catch (Exception unused3) {
                                                }
                                            }
                                            new Thread(new c.n(pVar3, 5, bluetoothSocketAccept2), "AA-BT-HFP-Responder").start();
                                        } catch (Exception e12) {
                                            if (pVar3.f13315b) {
                                                Log.d("HUR-WirelessBT", "HFP accept ended: " + e12);
                                                return;
                                            }
                                            return;
                                        }
                                    }
                                    return;
                            }
                        }
                    }, "AA-BT-HFP-Accept");
                    pVar.f13319f = thread2;
                    thread2.start();
                }
                pVar.f(true);
                Log.d("HUR-WirelessBT", "Wireless AA RFCOMM listener started (AP already up)");
            } catch (Throwable th3) {
                Log.e("HUR-WirelessBT", "Could not open RFCOMM server socket", th3);
                z7.p.f13311m.e(Boolean.FALSE);
                pVar.i();
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:113:0x0110 A[SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:63:0x0109 A[Catch: IOException -> 0x0091, XmlPullParserException -> 0x0094, TryCatch #2 {IOException -> 0x0091, XmlPullParserException -> 0x0094, blocks: (B:20:0x0062, B:97:0x0209, B:28:0x0074, B:29:0x0082, B:31:0x0087, B:38:0x0097, B:46:0x00b1, B:41:0x00a0, B:44:0x00a9, B:47:0x00bf, B:51:0x00ce, B:53:0x00d6, B:54:0x00e0, B:63:0x0109, B:64:0x0110, B:65:0x0128, B:57:0x00e9, B:59:0x00f1, B:60:0x00ff, B:66:0x0129, B:68:0x0131, B:69:0x013f, B:72:0x0149, B:73:0x0154, B:74:0x016c, B:75:0x016d, B:78:0x0177, B:79:0x0182, B:80:0x019a, B:81:0x019b, B:83:0x01a3, B:84:0x01ac, B:87:0x01b6, B:88:0x01c0, B:89:0x01d8, B:90:0x01d9, B:93:0x01e3, B:94:0x01ed, B:95:0x0205, B:96:0x0206), top: B:105:0x0062 }] */
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    public void N(Context context, XmlResourceParser xmlResourceParser) {
        i3.o oVar = new i3.o();
        int attributeCount = xmlResourceParser.getAttributeCount();
        for (int i5 = 0; i5 < attributeCount; i5++) {
            String attributeName = xmlResourceParser.getAttributeName(i5);
            String attributeValue = xmlResourceParser.getAttributeValue(i5);
            if (attributeName != null && attributeValue != null && "id".equals(attributeName)) {
                int identifier = attributeValue.contains("/") ? context.getResources().getIdentifier(attributeValue.substring(attributeValue.indexOf(47) + 1), "id", context.getPackageName()) : -1;
                if (identifier == -1) {
                    if (attributeValue.length() > 1) {
                        identifier = Integer.parseInt(attributeValue.substring(1));
                    } else {
                        Log.e("ConstraintLayoutStates", "error in parsing id");
                    }
                }
                try {
                    int eventType = xmlResourceParser.getEventType();
                    i3.j jVarD = null;
                    while (eventType != 1) {
                        if (eventType == 0) {
                            xmlResourceParser.getName();
                        } else if (eventType == 2) {
                            String name = xmlResourceParser.getName();
                            switch (name.hashCode()) {
                                case -2025855158:
                                    if (name.equals("Layout")) {
                                        if (jVarD == null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        jVarD.f5671d.a(context, Xml.asAttributeSet(xmlResourceParser));
                                    } else {
                                        continue;
                                    }
                                    break;
                                case -1984451626:
                                    if (name.equals("Motion")) {
                                        if (jVarD == null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        jVarD.f5670c.a(context, Xml.asAttributeSet(xmlResourceParser));
                                    } else {
                                        continue;
                                    }
                                    break;
                                case -1962203927:
                                    if (name.equals("ConstraintOverride")) {
                                        jVarD = i3.o.d(context, Xml.asAttributeSet(xmlResourceParser), true);
                                    }
                                    break;
                                case -1269513683:
                                    if (name.equals("PropertySet")) {
                                        if (jVarD == null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        jVarD.f5669b.a(context, Xml.asAttributeSet(xmlResourceParser));
                                    } else {
                                        continue;
                                    }
                                    break;
                                case -1238332596:
                                    if (name.equals("Transform")) {
                                        if (jVarD == null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        jVarD.f5672e.a(context, Xml.asAttributeSet(xmlResourceParser));
                                    } else {
                                        continue;
                                    }
                                    break;
                                case -71750448:
                                    if (name.equals("Guideline")) {
                                        jVarD = i3.o.d(context, Xml.asAttributeSet(xmlResourceParser), false);
                                        jVarD.f5671d.f5675a = true;
                                    }
                                    break;
                                case 366511058:
                                    if (name.equals("CustomMethod")) {
                                        if (jVarD != null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        i3.b.a(context, xmlResourceParser, jVarD.f5673f);
                                    } else {
                                        continue;
                                    }
                                    break;
                                case 1331510167:
                                    if (name.equals("Barrier")) {
                                        jVarD = i3.o.d(context, Xml.asAttributeSet(xmlResourceParser), false);
                                        jVarD.f5671d.f5690h0 = 1;
                                    }
                                    break;
                                case 1791837707:
                                    if (name.equals("CustomAttribute")) {
                                        if (jVarD != null) {
                                            throw new RuntimeException("XML parser error must be within a Constraint " + xmlResourceParser.getLineNumber());
                                        }
                                        i3.b.a(context, xmlResourceParser, jVarD.f5673f);
                                    } else {
                                        continue;
                                    }
                                    break;
                                case 1803088381:
                                    if (name.equals("Constraint")) {
                                        jVarD = i3.o.d(context, Xml.asAttributeSet(xmlResourceParser), false);
                                    }
                                    break;
                            }
                        } else if (eventType == 3) {
                            String lowerCase = xmlResourceParser.getName().toLowerCase(Locale.ROOT);
                            switch (lowerCase.hashCode()) {
                                case -2075718416:
                                    if (lowerCase.equals("guideline")) {
                                        oVar.f5743c.put(Integer.valueOf(jVarD.f5668a), jVarD);
                                        jVarD = null;
                                    }
                                    break;
                                case -190376483:
                                    if (lowerCase.equals("constraint")) {
                                        oVar.f5743c.put(Integer.valueOf(jVarD.f5668a), jVarD);
                                        jVarD = null;
                                    }
                                    break;
                                case 426575017:
                                    if (lowerCase.equals("constraintoverride")) {
                                        oVar.f5743c.put(Integer.valueOf(jVarD.f5668a), jVarD);
                                        jVarD = null;
                                    }
                                    break;
                                case 2146106725:
                                    if (lowerCase.equals("constraintset")) {
                                        ((SparseArray) this.f362f).put(identifier, oVar);
                                        return;
                                    }
                                    break;
                                    break;
                                default:
                                    break;
                            }
                        }
                        eventType = xmlResourceParser.next();
                    }
                } catch (IOException e10) {
                    Log.e("ConstraintSet", "Error parsing XML resource", e10);
                } catch (XmlPullParserException e11) {
                    Log.e("ConstraintSet", "Error parsing XML resource", e11);
                }
                ((SparseArray) this.f362f).put(identifier, oVar);
                return;
            }
        }
    }

    public synchronized s9.i O() {
        s9.i iVar;
        iVar = (s9.i) this.f361e;
        if (iVar != null) {
            s9.i iVar2 = iVar.f11140c;
            this.f361e = iVar2;
            if (iVar2 == null) {
                this.f362f = null;
            }
        }
        return iVar;
    }

    public synchronized s9.i P() {
        try {
            if (((s9.i) this.f361e) == null) {
                wait(1000);
            }
        } catch (Throwable th) {
            throw th;
        }
        return O();
    }

    public void Q() {
        m mVar = (m) this.f361e;
        MediaSession mediaSession = mVar.f350a;
        mVar.f354e.kill();
        if (Build.VERSION.SDK_INT == 27) {
            try {
                Field declaredField = mediaSession.getClass().getDeclaredField("mCallback");
                declaredField.setAccessible(true);
                Handler handler = (Handler) declaredField.get(mediaSession);
                if (handler != null) {
                    handler.removeCallbacksAndMessages(null);
                }
            } catch (Exception e10) {
                Log.w("MediaSessionCompat", "Exception happened while accessing MediaSession.mCallback.", e10);
            }
        }
        mediaSession.setCallback(null);
        mVar.f351b.f349b.set(null);
        mediaSession.release();
    }

    public boolean R(f0 f0Var) {
        if (f0Var.E()) {
            return ((s1) this.f362f).remove(f0Var);
        }
        a.a.J("DepthSortedSet.remove called on an unattached node");
        throw null;
    }

    public void S(int i5, Bundle bundle, Messenger messenger) throws RemoteException {
        Message messageObtain = Message.obtain();
        messageObtain.what = i5;
        messageObtain.arg1 = 1;
        messageObtain.setData(bundle);
        messageObtain.replyTo = messenger;
        ((Messenger) this.f361e).send(messageObtain);
    }

    public void T(PlaybackStateCompat playbackStateCompat) {
        m mVar = (m) this.f361e;
        mVar.f355f = playbackStateCompat;
        synchronized (mVar.f353d) {
            for (int iBeginBroadcast = mVar.f354e.beginBroadcast() - 1; iBeginBroadcast >= 0; iBeginBroadcast--) {
                try {
                    ((b) mVar.f354e.getBroadcastItem(iBeginBroadcast)).W(playbackStateCompat);
                } catch (RemoteException unused) {
                }
            }
            mVar.f354e.finishBroadcast();
        }
        MediaSession mediaSession = mVar.f350a;
        if (playbackStateCompat.f326o == null) {
            PlaybackState.Builder builderD = u.d();
            u.x(builderD, playbackStateCompat.f316d, playbackStateCompat.f317e, playbackStateCompat.f319g, playbackStateCompat.f322k);
            u.u(builderD, playbackStateCompat.f318f);
            u.s(builderD, playbackStateCompat.f320h);
            u.v(builderD, playbackStateCompat.j);
            ArrayList arrayList = playbackStateCompat.f323l;
            int size = arrayList.size();
            int i5 = 0;
            while (i5 < size) {
                Object obj = arrayList.get(i5);
                i5++;
                PlaybackStateCompat.CustomAction customAction = (PlaybackStateCompat.CustomAction) obj;
                PlaybackState.CustomAction.Builder builderE = u.e(customAction.f327d, customAction.f328e, customAction.f329f);
                u.w(builderE, customAction.f330g);
                u.a(builderD, u.b(builderE));
            }
            u.t(builderD, playbackStateCompat.f324m);
            if (Build.VERSION.SDK_INT >= 22) {
                w.b(builderD, playbackStateCompat.f325n);
            }
            playbackStateCompat.f326o = u.c(builderD);
        }
        mediaSession.setPlaybackState(playbackStateCompat.f326o);
    }

    public void V(View view, float[] fArr) {
        float[] fArr2 = (float[]) this.f361e;
        Object parent = view.getParent();
        if (parent instanceof View) {
            V((View) parent, fArr);
            float f2 = -view.getScrollX();
            float f6 = -view.getScrollY();
            m1.g0.d(fArr2);
            m1.g0.h(fArr2, f2, f6);
            e2.n0.x(fArr, fArr2);
            float left = view.getLeft();
            float top = view.getTop();
            m1.g0.d(fArr2);
            m1.g0.h(fArr2, left, top);
            e2.n0.x(fArr, fArr2);
        } else {
            int[] iArr = (int[]) this.f362f;
            view.getLocationInWindow(iArr);
            float f8 = -view.getScrollX();
            float f10 = -view.getScrollY();
            m1.g0.d(fArr2);
            m1.g0.h(fArr2, f8, f10);
            e2.n0.x(fArr, fArr2);
            float f11 = iArr[0];
            float f12 = iArr[1];
            m1.g0.d(fArr2);
            m1.g0.h(fArr2, f11, f12);
            e2.n0.x(fArr, fArr2);
        }
        Matrix matrix = view.getMatrix();
        if (matrix.isIdentity()) {
            return;
        }
        m1.n0.r(matrix, fArr2);
        e2.n0.x(fArr, fArr2);
    }

    @Override // c1.m
    public Object a(Object obj) {
        return ((z8.c) this.f362f).d(obj);
    }

    @Override // c1.m
    public Object b(c1.b bVar, Object obj) {
        return ((z8.e) this.f361e).i(bVar, obj);
    }

    @Override // o2.e
    public int c(int i5) {
        CharSequence charSequence = (CharSequence) this.f361e;
        do {
            h7.n nVar = (h7.n) this.f362f;
            nVar.a(i5);
            i5 = ((BreakIterator) nVar.f5236e).following(i5);
            if (i5 == -1 || i5 == charSequence.length()) {
                return -1;
            }
        } while (Character.isWhitespace(charSequence.charAt(i5)));
        return i5;
    }

    /* JADX WARN: Code duplicated, block: B:37:0x0096  */
    @Override // x3.o
    public j1 d(View view, j1 j1Var) {
        boolean z6;
        boolean z9;
        b8 b8Var = (b8) this.f361e;
        y6.m mVar = (y6.m) this.f362f;
        int i5 = mVar.f13185a;
        int i6 = mVar.f13186b;
        int i10 = mVar.f13187c;
        g1 g1Var = j1Var.f12461a;
        p3.c cVarF = g1Var.f(7);
        p3.c cVarF2 = g1Var.f(32);
        BottomSheetBehavior bottomSheetBehavior = (BottomSheetBehavior) b8Var.f2205b;
        int i11 = cVarF.f9892b;
        int i12 = cVarF.f9893c;
        int i13 = cVarF.f9891a;
        bottomSheetBehavior.f1905w = i11;
        boolean z10 = view.getLayoutDirection() == 1;
        int paddingBottom = view.getPaddingBottom();
        int paddingLeft = view.getPaddingLeft();
        int paddingRight = view.getPaddingRight();
        boolean z11 = bottomSheetBehavior.f1897o;
        if (z11) {
            int iA = j1Var.a();
            bottomSheetBehavior.f1904v = iA;
            paddingBottom = iA + i10;
        }
        if (bottomSheetBehavior.f1898p) {
            paddingLeft = (z10 ? i6 : i5) + i13;
        }
        int i14 = paddingLeft;
        if (bottomSheetBehavior.f1899q) {
            if (!z10) {
                i5 = i6;
            }
            paddingRight = i5 + i12;
        }
        int i15 = paddingRight;
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (!bottomSheetBehavior.f1901s || marginLayoutParams.leftMargin == i13) {
            z6 = false;
        } else {
            marginLayoutParams.leftMargin = i13;
            z6 = true;
        }
        if (bottomSheetBehavior.f1902t && marginLayoutParams.rightMargin != i12) {
            marginLayoutParams.rightMargin = i12;
            z6 = true;
        }
        if (bottomSheetBehavior.f1903u) {
            int i16 = marginLayoutParams.topMargin;
            int i17 = cVarF.f9892b;
            if (i16 != i17) {
                marginLayoutParams.topMargin = i17;
                z9 = true;
            } else {
                z9 = z6;
            }
        } else {
            z9 = z6;
        }
        if (z9) {
            view.setLayoutParams(marginLayoutParams);
        }
        view.setPadding(i14, view.getPaddingTop(), i15, paddingBottom);
        boolean z12 = b8Var.f2204a;
        if (z12) {
            bottomSheetBehavior.f1895m = cVarF2.f9894d;
        }
        if (!z11 && !z12) {
            return j1Var;
        }
        bottomSheetBehavior.J();
        return j1Var;
    }

    @Override // o2.e
    public int e(int i5) {
        do {
            h7.n nVar = (h7.n) this.f362f;
            nVar.a(i5);
            i5 = ((BreakIterator) nVar.f5236e).preceding(i5);
            if (i5 == -1 || i5 == 0) {
                return -1;
            }
        } while (Character.isWhitespace(((CharSequence) this.f361e).charAt(i5 - 1)));
        return i5;
    }

    @Override // e2.h1
    public void f(View view, float[] fArr) {
        m1.g0.d(fArr);
        V(view, fArr);
    }

    @Override // o2.e
    public int g(int i5) {
        do {
            h7.n nVar = (h7.n) this.f362f;
            nVar.a(i5);
            i5 = ((BreakIterator) nVar.f5236e).preceding(i5);
            if (i5 == -1) {
                return -1;
            }
        } while (Character.isWhitespace(((CharSequence) this.f361e).charAt(i5)));
        return i5;
    }

    @Override // g4.q
    public Object getResult() {
        return (g4.c0) this.f361e;
    }

    @Override // o2.e
    public int h(int i5) {
        do {
            h7.n nVar = (h7.n) this.f362f;
            nVar.a(i5);
            i5 = ((BreakIterator) nVar.f5236e).following(i5);
            if (i5 == -1) {
                return -1;
            }
        } while (Character.isWhitespace(((CharSequence) this.f361e).charAt(i5 - 1)));
        return i5;
    }

    @Override // g4.q
    public boolean i(CharSequence charSequence, int i5, int i6, z zVar) {
        if ((zVar.f5020c & 4) > 0) {
            return true;
        }
        if (((g4.c0) this.f361e) == null) {
            this.f361e = new g4.c0(charSequence instanceof Spannable ? (Spannable) charSequence : new SpannableString(charSequence));
        }
        ((t5.g) this.f362f).getClass();
        ((g4.c0) this.f361e).setSpan(new a0(zVar), i5, i6, 33);
        return true;
    }

    public void j(f0 f0Var) {
        if (f0Var.E()) {
            ((s1) this.f362f).add(f0Var);
        } else {
            a.a.J("DepthSortedSet.add called on an unattached node");
            throw null;
        }
    }

    public BigDecimal k(e8.a aVar, Calendar calendar, boolean z6) {
        i0.p pVar = (i0.p) this.f361e;
        TimeZone timeZone = (TimeZone) this.f362f;
        calendar.setTimeZone(timeZone);
        BigDecimal bigDecimalValueOf = BigDecimal.valueOf(z6 ? 6 : 18);
        BigDecimal bigDecimal = (BigDecimal) pVar.f5533f;
        BigDecimal bigDecimalValueOf2 = BigDecimal.valueOf(15L);
        RoundingMode roundingMode = RoundingMode.HALF_EVEN;
        BigDecimal bigDecimalU = U(new BigDecimal(calendar.get(6)).add(bigDecimalValueOf.subtract(bigDecimal.divide(bigDecimalValueOf2, 8, roundingMode)).divide(BigDecimal.valueOf(24L), 8, roundingMode)));
        BigDecimal bigDecimalU2 = U(U(new BigDecimal("0.9856").multiply(bigDecimalU)).subtract(new BigDecimal("3.289")));
        BigDecimal bigDecimalAdd = bigDecimalU2.add(U(new BigDecimal(Math.sin(l(bigDecimalU2).doubleValue())).multiply(new BigDecimal("1.916")))).add(U(new BigDecimal(Math.sin(U(l(bigDecimalU2).multiply(BigDecimal.valueOf(2L))).doubleValue())).multiply(new BigDecimal("0.020"))).add(new BigDecimal("282.634")));
        if (bigDecimalAdd.doubleValue() > 360.0d) {
            bigDecimalAdd = bigDecimalAdd.subtract(BigDecimal.valueOf(360L));
        }
        BigDecimal bigDecimalU3 = U(bigDecimalAdd);
        BigDecimal bigDecimalU4 = U(BigDecimal.valueOf(Math.sin(l(bigDecimalU3).doubleValue())).multiply(new BigDecimal("0.39782")));
        BigDecimal bigDecimalU5 = U(BigDecimal.valueOf(Math.cos(BigDecimal.valueOf(Math.asin(bigDecimalU4.doubleValue())).doubleValue())));
        BigDecimal bigDecimalValueOf3 = BigDecimal.valueOf(Math.cos(l(aVar.f4282a).doubleValue()));
        BigDecimal bigDecimal2 = (BigDecimal) pVar.f5532e;
        BigDecimal bigDecimalU6 = U(bigDecimalValueOf3.subtract(bigDecimalU4.multiply(BigDecimal.valueOf(Math.sin(l(bigDecimal2).doubleValue())))).divide(bigDecimalU5.multiply(BigDecimal.valueOf(Math.cos(l(bigDecimal2).doubleValue()))), 8, roundingMode));
        if (bigDecimalU6.doubleValue() < -1.0d || bigDecimalU6.doubleValue() > 1.0d) {
            return null;
        }
        BigDecimal bigDecimalU7 = U(U(BigDecimal.valueOf(Math.acos(bigDecimalU6.doubleValue()))).multiply(new BigDecimal(57.29577951308232d)));
        if (z6) {
            bigDecimalU7 = BigDecimal.valueOf(360L).subtract(bigDecimalU7);
        }
        BigDecimal bigDecimalDivide = bigDecimalU7.divide(BigDecimal.valueOf(15L), 8, roundingMode);
        BigDecimal bigDecimalU8 = U(U(new BigDecimal(Math.atan(l(U(U(new BigDecimal(Math.tan(l(bigDecimalU3).doubleValue())).multiply(new BigDecimal(57.29577951308232d))).multiply(new BigDecimal("0.91764")))).doubleValue())).multiply(new BigDecimal(57.29577951308232d))));
        if (bigDecimalU8.doubleValue() < 0.0d) {
            bigDecimalU8 = bigDecimalU8.add(BigDecimal.valueOf(360L));
        } else if (bigDecimalU8.doubleValue() > 360.0d) {
            bigDecimalU8 = bigDecimalU8.subtract(BigDecimal.valueOf(360L));
        }
        BigDecimal bigDecimalValueOf4 = BigDecimal.valueOf(90L);
        RoundingMode roundingMode2 = RoundingMode.FLOOR;
        BigDecimal bigDecimalSubtract = bigDecimalDivide.add(bigDecimalU8.add(bigDecimalU3.divide(bigDecimalValueOf4, 0, roundingMode2).multiply(bigDecimalValueOf4).subtract(bigDecimalU8.divide(bigDecimalValueOf4, 0, roundingMode2).multiply(bigDecimalValueOf4))).divide(BigDecimal.valueOf(15L), 8, roundingMode)).subtract(bigDecimalU.multiply(new BigDecimal("0.06571"))).subtract(new BigDecimal("6.622"));
        if (bigDecimalSubtract.doubleValue() < 0.0d) {
            bigDecimalSubtract = bigDecimalSubtract.add(BigDecimal.valueOf(24L));
        } else if (bigDecimalSubtract.doubleValue() > 24.0d) {
            bigDecimalSubtract = bigDecimalSubtract.subtract(BigDecimal.valueOf(24L));
        }
        BigDecimal bigDecimalAdd2 = U(bigDecimalSubtract).subtract(((BigDecimal) pVar.f5533f).divide(BigDecimal.valueOf(15L), 8, roundingMode)).add(new BigDecimal(calendar.get(15)).divide(new BigDecimal(3600000), new MathContext(2)));
        if (timeZone.inDaylightTime(calendar.getTime())) {
            bigDecimalAdd2 = bigDecimalAdd2.add(BigDecimal.valueOf(((double) timeZone.getDSTSavings()) * 2.7777777777777776E-7d));
        }
        return bigDecimalAdd2.doubleValue() > 24.0d ? bigDecimalAdd2.subtract(BigDecimal.valueOf(24L)) : bigDecimalAdd2;
    }

    public void n(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.n(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void o(boolean z6) {
        c0 c0Var = (c0) this.f362f;
        i.k kVar = c0Var.f6786t.j;
        k4.p pVar = c0Var.f6788v;
        if (pVar != null) {
            pVar.j().f6778l.o(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    @Override // t3.d
    public void onCancel() {
        ((Animator) this.f361e).end();
        if (c0.F(2)) {
            Log.v("FragmentManager", "Animator from operation " + ((m0) this.f362f) + " has been canceled.");
        }
    }

    public void p(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.p(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void q(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.q(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void r(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.r(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void s(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.s(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void t(boolean z6) {
        c0 c0Var = (c0) this.f362f;
        i.k kVar = c0Var.f6786t.j;
        k4.p pVar = c0Var.f6788v;
        if (pVar != null) {
            pVar.j().f6778l.t(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public String toString() {
        switch (this.f360d) {
            case 3:
                return ((s1) this.f362f).toString();
            default:
                return super.toString();
        }
    }

    public void u(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.u(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void v(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.v(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void w(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.w(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void x(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.x(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void y(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.y(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public void z(boolean z6) {
        k4.p pVar = ((c0) this.f362f).f6788v;
        if (pVar != null) {
            pVar.j().f6778l.z(true);
        }
        Iterator it = ((CopyOnWriteArrayList) this.f361e).iterator();
        if (it.hasNext()) {
            if (it.next() != null) {
                throw new ClassCastException();
            }
            if (!z6) {
                throw null;
            }
            throw null;
        }
    }

    public /* synthetic */ t(int i5, boolean z6) {
        this.f360d = i5;
    }

    public /* synthetic */ t(Object obj, int i5, Object obj2) {
        this.f360d = i5;
        this.f361e = obj;
        this.f362f = obj2;
    }

    public t(y5.b bVar, w5.f fVar) {
        this.f360d = 20;
        this.f362f = "ClientTelemetry.API";
        this.f361e = bVar;
    }

    public t(f0 f0Var, g0 g0Var) {
        this.f360d = 4;
        this.f361e = f0Var;
        this.f362f = s0.d.I(g0Var, o0.f10741i);
    }

    public t(int i5) {
        this.f360d = i5;
        switch (i5) {
            case 5:
                this.f361e = new u0.d(new f0[16]);
                break;
            case 15:
                this.f361e = new Rect();
                this.f362f = new Rect();
                break;
            case 18:
                this.f361e = new t5.g();
                this.f362f = new g4.t();
                break;
            case 23:
                t5.d dVar = t5.d.f11250c;
                this.f361e = new SparseIntArray();
                this.f362f = dVar;
                break;
            case 25:
                this.f361e = new SparseIntArray();
                this.f362f = new SparseIntArray();
                break;
            default:
                this.f361e = e5.a.O(d2.n.f3436f);
                this.f362f = new s1(new f1(1));
                break;
        }
    }

    public t(i0.p pVar, String str) {
        this.f360d = 8;
        this.f361e = pVar;
        this.f362f = TimeZone.getTimeZone(str);
    }

    public t(c0 c0Var) {
        this.f360d = 14;
        this.f361e = new CopyOnWriteArrayList();
        this.f362f = c0Var;
    }

    public t(e0 e0Var) {
        this.f360d = 26;
        this.f361e = e0Var;
        d1 d1Var = new d1();
        d1Var.f12870a = 0;
        this.f362f = d1Var;
    }

    public t(f4.c cVar) {
        this.f360d = 7;
        this.f362f = cVar;
    }

    public t(Matcher matcher, CharSequence charSequence) {
        this.f360d = 12;
        a9.j.e(charSequence, "input");
        this.f361e = matcher;
        this.f362f = charSequence;
    }

    public t(Context context, String str) {
        ComponentName componentName;
        this.f360d = 0;
        this.f362f = new ArrayList();
        if (context != null) {
            if (!TextUtils.isEmpty(str)) {
                int i5 = MediaButtonReceiver.f897a;
                Intent intent = new Intent("android.intent.action.MEDIA_BUTTON");
                intent.setPackage(context.getPackageName());
                List<ResolveInfo> listQueryBroadcastReceivers = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                PendingIntent broadcast = null;
                if (listQueryBroadcastReceivers.size() == 1) {
                    ActivityInfo activityInfo = listQueryBroadcastReceivers.get(0).activityInfo;
                    componentName = new ComponentName(activityInfo.packageName, activityInfo.name);
                } else {
                    if (listQueryBroadcastReceivers.size() > 1) {
                        Log.w("MediaButtonReceiver", "More than one BroadcastReceiver that handles android.intent.action.MEDIA_BUTTON was found, returning null.");
                    }
                    componentName = null;
                }
                if (componentName == null) {
                    Log.w("MediaSessionCompat", "Couldn't find a unique registered media button receiver in the given context.");
                }
                if (componentName != null) {
                    Intent intent2 = new Intent("android.intent.action.MEDIA_BUTTON");
                    intent2.setComponent(componentName);
                    broadcast = PendingIntent.getBroadcast(context, 0, intent2, Build.VERSION.SDK_INT >= 31 ? 33554432 : 0);
                }
                int i6 = Build.VERSION.SDK_INT;
                if (i6 >= 29) {
                    this.f361e = new r(context, str);
                } else if (i6 >= 28) {
                    this.f361e = new p(context, str);
                } else if (i6 >= 22) {
                    this.f361e = new n(context, str);
                } else {
                    this.f361e = new m(context, str);
                }
                ((m) this.f361e).e(new h(), new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper()));
                ((m) this.f361e).f350a.setMediaButtonReceiver(broadcast);
                MediaSessionCompat$Token mediaSessionCompat$Token = ((m) this.f361e).f352c;
                if (mediaSessionCompat$Token != null) {
                    Collections.synchronizedSet(new HashSet());
                    if (i6 >= 29) {
                        new g(context, mediaSessionCompat$Token);
                    } else {
                        new f(context, mediaSessionCompat$Token);
                    }
                    if (f359g == 0) {
                        f359g = (int) (TypedValue.applyDimension(1, 320.0f, context.getResources().getDisplayMetrics()) + 0.5f);
                        return;
                    }
                    return;
                }
                throw new IllegalArgumentException("sessionToken must not be null");
            }
            throw new IllegalArgumentException("tag must not be null or empty");
        }
        throw new IllegalArgumentException("context must not be null");
    }

    public t(float[] fArr) {
        this.f360d = 6;
        this.f361e = fArr;
        this.f362f = new int[2];
    }
}
