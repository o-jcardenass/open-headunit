package c;

import android.bluetooth.BluetoothSocket;
import android.graphics.Typeface;
import android.util.Log;
import android.util.LongSparseArray;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class n implements Runnable {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final /* synthetic */ int f1443d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final /* synthetic */ Object f1444e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final /* synthetic */ Object f1445f;

    public /* synthetic */ n(Object obj, int i5, Object obj2) {
        this.f1443d = i5;
        this.f1444e = obj;
        this.f1445f = obj2;
    }

    @Override // java.lang.Runnable
    public final void run() {
        int i5;
        int i6;
        Thread thread;
        int i10 = 0;
        switch (this.f1443d) {
            case 0:
                o oVar = (o) this.f1444e;
                f0 f0Var = (f0) this.f1445f;
                int i11 = o.f1446v;
                oVar.f7667d.a(new g(f0Var, oVar));
                return;
            case 1:
                h1.a.a((h1.c) this.f1444e, (LongSparseArray) this.f1445f);
                return;
            case 2:
                i.p pVar = (i.p) this.f1444e;
                Runnable runnable = (Runnable) this.f1445f;
                pVar.getClass();
                try {
                    runnable.run();
                    return;
                } finally {
                    pVar.a();
                }
            case 3:
                ((o3.b) this.f1444e).j((Typeface) this.f1445f);
                return;
            case 4:
                z7.i iVar = (z7.i) this.f1444e;
                android.support.v4.media.session.t tVar = (android.support.v4.media.session.t) this.f1445f;
                z7.j jVar = iVar.f13289c;
                jVar.f13292c.requestGroupInfo(jVar.f13293d, new z7.f(i10, tVar, jVar));
                return;
            default:
                z7.p pVar2 = (z7.p) this.f1444e;
                BluetoothSocket bluetoothSocket = (BluetoothSocket) this.f1445f;
                Thread thread2 = null;
                try {
                    try {
                        InputStream inputStream = bluetoothSocket.getInputStream();
                        OutputStream outputStream = bluetoothSocket.getOutputStream();
                        z7.p.d(outputStream, "AT+BRSF=0");
                        byte[] bArr = new byte[1024];
                        StringBuilder sb = new StringBuilder();
                        char c10 = 0;
                        while (pVar2.f13315b && bluetoothSocket.isConnected() && (i5 = inputStream.read(bArr)) >= 0) {
                            sb.append(new String(bArr, 0, i5, StandardCharsets.US_ASCII));
                            while (true) {
                                while (true) {
                                    if (i6 < sb.length()) {
                                        char cCharAt = sb.charAt(i6);
                                        i6 = (cCharAt == '\r' || cCharAt == '\n') ? 0 : i6 + 1;
                                    } else {
                                        i6 = -1;
                                    }
                                }
                                if (i6 >= 0) {
                                    String strTrim = sb.substring(0, i6).trim();
                                    sb.delete(0, i6 + 1);
                                    if (!strTrim.isEmpty()) {
                                        Log.d("HUR-WirelessBT", "HFP RX: " + strTrim);
                                        int i12 = 2;
                                        if (strTrim.startsWith("AT")) {
                                            z7.p.b(outputStream, strTrim);
                                            if (c10 < 4 && strTrim.startsWith("AT+CMER")) {
                                                thread = new Thread(new g4.m(pVar2, bluetoothSocket, outputStream, i12), "AA-BT-HFP-KeepAlive");
                                                thread.start();
                                                thread2 = thread;
                                                c10 = 4;
                                            }
                                        } else if (strTrim.equalsIgnoreCase("OK") || strTrim.startsWith("ERROR")) {
                                            if (c10 == 0) {
                                                z7.p.d(outputStream, "AT+CIND=?");
                                                c10 = 1;
                                            } else if (c10 == 1) {
                                                z7.p.d(outputStream, "AT+CIND?");
                                                c10 = 2;
                                            } else if (c10 == 2) {
                                                z7.p.d(outputStream, "AT+CMER=3,0,0,1");
                                                c10 = 3;
                                            } else if (c10 == 3) {
                                                Log.d("HUR-WirelessBT", "HFP SLC established (HF initiator)");
                                                thread = new Thread(new g4.m(pVar2, bluetoothSocket, outputStream, i12), "AA-BT-HFP-KeepAlive");
                                                thread.start();
                                                thread2 = thread;
                                                c10 = 4;
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        }
                        if (thread2 != null) {
                            thread2.interrupt();
                        }
                    } catch (Exception e10) {
                        Log.d("HUR-WirelessBT", "HFP handler ended: " + e10);
                        if (thread2 != null) {
                        }
                    }
                    try {
                        bluetoothSocket.close();
                        return;
                    } catch (Exception unused) {
                        return;
                    }
                } catch (Throwable th) {
                    if (thread2 != null) {
                        thread2.interrupt();
                    }
                    try {
                        bluetoothSocket.close();
                        break;
                    } catch (Exception unused2) {
                    }
                    throw th;
                }
        }
    }
}
