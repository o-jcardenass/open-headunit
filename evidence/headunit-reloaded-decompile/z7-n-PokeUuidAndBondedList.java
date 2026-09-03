package z7;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.lifecycle.a0;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/* JADX INFO: compiled from: r8-map-id-2a18494dca7a9e53669f4e8709dea820aded73b8c7552488804dd55e404ce534 */
/* JADX INFO: loaded from: classes.dex */
public abstract class n {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public static final a0 f13304a = new a0(null);

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final UUID f13305b = UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public static volatile Thread f13306c;

    public static synchronized void a() {
        Thread thread = f13306c;
        f13306c = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static ArrayList b(Context context) {
        ArrayList arrayList = new ArrayList();
        if (!d(context)) {
            Log.w("HUR-A2dpNudge", "No BLUETOOTH_CONNECT permission - cannot list bonded devices");
            return arrayList;
        }
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.isEnabled()) {
            try {
                Set<BluetoothDevice> bondedDevices = defaultAdapter.getBondedDevices();
                if (bondedDevices != null) {
                    for (BluetoothDevice bluetoothDevice : bondedDevices) {
                        String name = bluetoothDevice.getName();
                        String address = bluetoothDevice.getAddress();
                        if (address != null) {
                            if (name == null || name.isEmpty()) {
                                name = address;
                            }
                            arrayList.add(new l(name, address));
                        }
                    }
                }
            } catch (Throwable th) {
                Log.w("HUR-A2dpNudge", "getBondedDevices failed: " + th);
            }
        }
        return arrayList;
    }

    public static LinkedHashSet c(Context context) {
        return new LinkedHashSet(n.j.b(context).getStringSet("wireless_a2dp_selected", new HashSet()));
    }

    public static boolean d(Context context) {
        return Build.VERSION.SDK_INT < 31 || h5.a.o(context, "android.permission.BLUETOOTH_CONNECT") == 0;
    }
}
