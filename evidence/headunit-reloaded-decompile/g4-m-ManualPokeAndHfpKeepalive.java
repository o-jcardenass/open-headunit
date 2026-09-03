package g4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.OutputStream;
import java.util.concurrent.ThreadPoolExecutor;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class m implements Runnable {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final /* synthetic */ int f4984d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final /* synthetic */ Object f4985e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final /* synthetic */ Object f4986f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public final /* synthetic */ Object f4987g;

    public /* synthetic */ m(Object obj, Object obj2, Object obj3, int i5) {
        this.f4984d = i5;
        this.f4985e = obj;
        this.f4986f = obj2;
        this.f4987g = obj3;
    }

    /* JADX WARN: Code duplicated, block: B:93:0x0151 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    @Override // java.lang.Runnable
    public final void run() throws Throwable {
        BluetoothSocket bluetoothSocketCreateRfcommSocketToServiceRecord;
        boolean z6;
        switch (this.f4984d) {
            case 0:
                b6.c cVar = (b6.c) this.f4985e;
                e5.a aVar = (e5.a) this.f4986f;
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) this.f4987g;
                try {
                    v vVarN = c9.a.n(cVar.f1358a);
                    if (vVarN == null) {
                        throw new RuntimeException("EmojiCompat font provider not available on this device.");
                    }
                    u uVar = (u) ((j) vVarN.f4971b);
                    synchronized (uVar.f5005d) {
                        uVar.f5007f = threadPoolExecutor;
                        break;
                    }
                    ((j) vVarN.f4971b).a(new n(aVar, threadPoolExecutor));
                    return;
                } catch (Throwable th) {
                    aVar.X(th);
                    threadPoolExecutor.shutdown();
                    return;
                }
            case 1:
                BluetoothAdapter bluetoothAdapter = (BluetoothAdapter) this.f4985e;
                String str = (String) this.f4986f;
                String str2 = (String) this.f4987g;
                BluetoothSocket bluetoothSocket = null;
                try {
                    try {
                        try {
                            try {
                                bluetoothSocketCreateRfcommSocketToServiceRecord = bluetoothAdapter.getRemoteDevice(str).createRfcommSocketToServiceRecord(z7.n.f13305b);
                                try {
                                    Log.i("HUR-A2dpNudge", "Poking " + str + " (RFCOMM connect to A2DP source)...");
                                    bluetoothSocketCreateRfcommSocketToServiceRecord.connect();
                                    Log.i("HUR-A2dpNudge", "RFCOMM up to " + str + " - waiting up to 30s for Android Auto");
                                    z7.n.f13304a.e(new z7.m(2, str2, null));
                                    long jCurrentTimeMillis = System.currentTimeMillis() + 30000;
                                    while (true) {
                                        if (System.currentTimeMillis() >= jCurrentTimeMillis || Thread.currentThread().isInterrupted()) {
                                            z6 = false;
                                        } else if (z7.p.f13312n) {
                                            z6 = true;
                                        } else {
                                            Thread.sleep(500L);
                                        }
                                    }
                                    z7.n.f13304a.e(new z7.m(z6 ? 3 : 5, str2, null));
                                } catch (InterruptedException unused) {
                                    z7.n.f13304a.e(new z7.m(z7.p.f13312n ? 3 : 5, str2, null));
                                    if (bluetoothSocketCreateRfcommSocketToServiceRecord == null) {
                                        return;
                                    }
                                } catch (Exception e10) {
                                    e = e10;
                                    bluetoothSocket = bluetoothSocketCreateRfcommSocketToServiceRecord;
                                    Log.d("HUR-A2dpNudge", "Poke RFCOMM to " + str + " failed: " + e.getMessage());
                                    z7.n.f13304a.e(new z7.m(4, str2, e.getMessage()));
                                    if (bluetoothSocket != null) {
                                        bluetoothSocket.close();
                                        return;
                                    }
                                    return;
                                }
                            } catch (Throwable th2) {
                                th = th2;
                                if (bluetoothSocket != null) {
                                    try {
                                        bluetoothSocket.close();
                                        break;
                                    } catch (Exception unused2) {
                                    }
                                }
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            bluetoothSocket = bluetoothAdapter;
                            if (bluetoothSocket != null) {
                                bluetoothSocket.close();
                                break;
                            }
                            throw th;
                        }
                        break;
                    } catch (InterruptedException unused3) {
                        bluetoothSocketCreateRfcommSocketToServiceRecord = null;
                    } catch (Exception e11) {
                        e = e11;
                    }
                    bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                    return;
                } catch (Exception unused4) {
                    return;
                }
            default:
                z7.p pVar = (z7.p) this.f4985e;
                BluetoothSocket bluetoothSocket2 = (BluetoothSocket) this.f4986f;
                OutputStream outputStream = (OutputStream) this.f4987g;
                Log.d("HUR-WirelessBT", "HFP keepalive started");
                while (pVar.f13315b && bluetoothSocket2.isConnected() && !Thread.currentThread().isInterrupted()) {
                    try {
                        z7.p.d(outputStream, "AT+CIND?");
                        Log.d("HUR-WirelessBT", "HFP keepalive poll (socket connected=" + bluetoothSocket2.isConnected() + ")");
                        try {
                            Thread.sleep(2000L);
                        } catch (InterruptedException unused5) {
                        }
                    } catch (Exception e12) {
                        Log.d("HUR-WirelessBT", "HFP keepalive ended (channel gone): " + e12);
                    }
                }
                Log.d("HUR-WirelessBT", "HFP keepalive stopped");
                return;
        }
    }
}
