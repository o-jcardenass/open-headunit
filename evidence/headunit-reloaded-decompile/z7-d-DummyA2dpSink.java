package z7;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public final class d extends Thread {

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    public static final UUID f13273f = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB");

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public BluetoothServerSocket f13274d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    public volatile boolean f13275e;

    @Override // java.lang.Thread, java.lang.Runnable
    public final void run() {
        while (this.f13275e && this.f13274d != null) {
            try {
                Log.d("HUR-DummyA2dp", "Waiting for an A2DP sender to connect...");
                BluetoothSocket bluetoothSocketAccept = this.f13274d.accept();
                if (bluetoothSocketAccept != null) {
                    Log.d("HUR-DummyA2dp", "A2DP sender connected - absorbing stream");
                    try {
                        InputStream inputStream = bluetoothSocketAccept.getInputStream();
                        byte[] bArr = new byte[1024];
                        while (this.f13275e) {
                            if (inputStream.read(bArr) == -1) {
                                Log.d("HUR-DummyA2dp", "A2DP sender disconnected");
                                break;
                            }
                        }
                    } catch (IOException e10) {
                        Log.d("HUR-DummyA2dp", "A2DP stream dropped: " + e10);
                    }
                    try {
                        bluetoothSocketAccept.close();
                    } catch (IOException unused) {
                    }
                }
            } catch (IOException e11) {
                if (this.f13275e) {
                    Log.d("HUR-DummyA2dp", "A2DP accept ended: " + e11);
                    return;
                }
                return;
            }
        }
    }
}
