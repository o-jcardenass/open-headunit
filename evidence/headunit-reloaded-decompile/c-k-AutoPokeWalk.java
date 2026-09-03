package c;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;
import java.io.Serializable;
import java.util.ArrayList;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final /* synthetic */ class k implements Runnable {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public final /* synthetic */ int f1429d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public final /* synthetic */ Object f1430e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public final /* synthetic */ int f1431f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    public final /* synthetic */ Object f1432g;

    public /* synthetic */ k(int i5, int i6, Object obj, Object obj2) {
        this.f1429d = i6;
        this.f1430e = obj;
        this.f1431f = i5;
        this.f1432g = obj2;
    }

    /* JADX WARN: Code duplicated, block: B:30:0x00b7 A[Catch: all -> 0x00b0, Exception -> 0x00b3, InterruptedException -> 0x0114, TRY_LEAVE, TryCatch #2 {InterruptedException -> 0x0114, blocks: (B:16:0x003c, B:17:0x0092, B:19:0x009a, B:21:0x00a4, B:24:0x00aa, B:30:0x00b7, B:34:0x00cb), top: B:77:0x003c, outer: #0 }] */
    /* JADX WARN: Code duplicated, block: B:34:0x00cb A[Catch: all -> 0x00b0, Exception -> 0x00b3, InterruptedException -> 0x0114, TRY_ENTER, TRY_LEAVE, TryCatch #2 {InterruptedException -> 0x0114, blocks: (B:16:0x003c, B:17:0x0092, B:19:0x009a, B:21:0x00a4, B:24:0x00aa, B:30:0x00b7, B:34:0x00cb), top: B:77:0x003c, outer: #0 }] */
    /* JADX WARN: Instruction removed from duplicated block: B:34:0x00cb, please report this as an issue */
    @Override // java.lang.Runnable
    public final void run() {
        switch (this.f1429d) {
            case 0:
                l lVar = (l) this.f1430e;
                int i5 = this.f1431f;
                Serializable serializable = (Serializable) ((a4.i) this.f1432g).f226e;
                String str = (String) lVar.f1433a.get(Integer.valueOf(i5));
                if (str == null) {
                    return;
                }
                f.d dVar = (f.d) lVar.f1437e.get(str);
                if ((dVar != null ? dVar.f4289a : null) == null) {
                    lVar.f1439g.remove(str);
                    lVar.f1438f.put(str, serializable);
                    return;
                } else {
                    f.b bVar = dVar.f4289a;
                    if (lVar.f1436d.remove(str)) {
                        bVar.a(serializable);
                        return;
                    }
                    return;
                }
            case 1:
                l lVar2 = (l) this.f1430e;
                int i6 = this.f1431f;
                IntentSender.SendIntentException sendIntentException = (IntentSender.SendIntentException) this.f1432g;
                a9.j.e(lVar2, "this$0");
                a9.j.e(sendIntentException, "$e");
                lVar2.a(i6, 0, new Intent().setAction("androidx.activity.result.contract.action.INTENT_SENDER_REQUEST").putExtra("androidx.activity.result.contract.extra.SEND_INTENT_EXCEPTION", sendIntentException));
                return;
            case 2:
                ((w4.d) ((n8.a) this.f1430e).f8347c).g(this.f1431f, this.f1432g);
                return;
            case 3:
                z7.j jVar = (z7.j) this.f1430e;
                jVar.f13292c.requestGroupInfo(jVar.f13293d, new z7.f(this.f1431f + 1, (android.support.v4.media.session.t) this.f1432g, jVar));
                return;
            default:
                int i10 = this.f1431f;
                ArrayList arrayList = (ArrayList) this.f1430e;
                BluetoothAdapter bluetoothAdapter = (BluetoothAdapter) this.f1432g;
                try {
                    Thread.sleep(2000L);
                    int i11 = 0;
                    boolean z6 = z7.p.f13312n;
                    while (true) {
                        if (i11 < i10 && !z6 && !Thread.currentThread().isInterrupted()) {
                            z7.l lVar3 = (z7.l) arrayList.get(i11);
                            int i12 = i11 + 1;
                            if (z7.p.f13312n) {
                                z6 = true;
                            } else {
                                BluetoothSocket bluetoothSocketCreateRfcommSocketToServiceRecord = null;
                                try {
                                    try {
                                        try {
                                            androidx.lifecycle.a0 a0Var = z7.n.f13304a;
                                            a0Var.e(new z7.m(1, lVar3.f13297a, null, i12, i10));
                                            bluetoothSocketCreateRfcommSocketToServiceRecord = bluetoothAdapter.getRemoteDevice(lVar3.f13298b).createRfcommSocketToServiceRecord(z7.n.f13305b);
                                            Log.i("HUR-A2dpNudge", "Auto-poke " + i12 + "/" + i10 + " -> " + lVar3.f13298b);
                                            bluetoothSocketCreateRfcommSocketToServiceRecord.connect();
                                            a0Var.e(new z7.m(2, lVar3.f13297a, null, i12, i10));
                                            long jCurrentTimeMillis = System.currentTimeMillis() + 30000;
                                            while (System.currentTimeMillis() < jCurrentTimeMillis && !Thread.currentThread().isInterrupted()) {
                                                if (z7.p.f13312n) {
                                                    z6 = true;
                                                    if (z6) {
                                                        z7.n.f13304a.e(new z7.m(3, lVar3.f13297a, null, i12, i10));
                                                        try {
                                                            bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                                                            break;
                                                        } catch (Exception unused) {
                                                        }
                                                    } else {
                                                        Log.i("HUR-A2dpNudge", "No AA from " + lVar3.f13298b + " within timeout - next device");
                                                        try {
                                                            bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                                                        } catch (Exception unused2) {
                                                        }
                                                        i11 = i12;
                                                    }
                                                } else {
                                                    Thread.sleep(500L);
                                                }
                                            }
                                            if (z6) {
                                                z7.n.f13304a.e(new z7.m(3, lVar3.f13297a, null, i12, i10));
                                                bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                                            } else {
                                                Log.i("HUR-A2dpNudge", "No AA from " + lVar3.f13298b + " within timeout - next device");
                                                bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                                                i11 = i12;
                                            }
                                        } catch (Throwable th) {
                                            if (bluetoothSocketCreateRfcommSocketToServiceRecord != null) {
                                                try {
                                                    bluetoothSocketCreateRfcommSocketToServiceRecord.close();
                                                    break;
                                                } catch (Exception unused3) {
                                                }
                                            }
                                            throw th;
                                        }
                                        break;
                                    } catch (InterruptedException unused4) {
                                        if (z7.p.f13312n) {
                                            z7.n.f13304a.e(new z7.m(3, lVar3.f13297a, null, i12, i10));
                                            z6 = true;
                                        }
                                        if (bluetoothSocketCreateRfcommSocketToServiceRecord != null) {
                                        }
                                        if (z6) {
                                            return;
                                        } else {
                                            return;
                                        }
                                    }
                                } catch (Exception e10) {
                                    Log.d("HUR-A2dpNudge", "Auto-poke RFCOMM to " + lVar3.f13298b + " failed: " + e10.getMessage());
                                    if (bluetoothSocketCreateRfcommSocketToServiceRecord != null) {
                                    }
                                    i11 = i12;
                                }
                            }
                        }
                        if (z6 || Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        Log.i("HUR-A2dpNudge", "Auto-connect exhausted all " + i10 + " device(s) with no session");
                        z7.n.f13304a.e(new z7.m(6, "", null, i10, i10));
                        return;
                    }
                } catch (InterruptedException unused5) {
                    return;
                }
        }
    }

    public /* synthetic */ k(int i5, android.support.v4.media.session.t tVar, z7.j jVar) {
        this.f1429d = 3;
        this.f1430e = jVar;
        this.f1432g = tVar;
        this.f1431f = i5;
    }

    public /* synthetic */ k(int i5, ArrayList arrayList, BluetoothAdapter bluetoothAdapter) {
        this.f1429d = 4;
        this.f1431f = i5;
        this.f1430e = arrayList;
        this.f1432g = bluetoothAdapter;
    }
}
