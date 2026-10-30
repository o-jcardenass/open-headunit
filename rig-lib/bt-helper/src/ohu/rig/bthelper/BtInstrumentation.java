package ohu.rig.bthelper;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Pairs and unpairs Bluetooth devices through the API, under the shell's permissions.
 *
 *   am instrument -w --no-hidden-api-checks -e cmd list|unpair|pair|accept [-e addr AA:BB:..] [-e timeout S]
 *       ohu.rig.bthelper/.BtInstrumentation
 *
 * The answer is one INSTRUMENTATION_RESULT line, "result=<WORD> <detail>", WORD one of LIST, UNPAIR_OK, PAIR_OK,
 * ACCEPT_OK or FAIL. removeBond() and createBond(transport) are hidden, hence --no-hidden-api-checks.
 */
public class BtInstrumentation extends Instrumentation {
    private Bundle args;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments;
        start();
    }

    @Override
    public void onStart() {
        String line;
        UiAutomation ua = getUiAutomation();
        ua.adoptShellPermissionIdentity();
        try {
            line = run();
        } catch (Throwable t) {
            line = "FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            ua.dropShellPermissionIdentity();
        }
        Bundle out = new Bundle();
        out.putString("result", line.replace('\n', ' '));
        finish(line.startsWith("FAIL") ? 1 : -1, out);
    }

    private String run() throws Exception {
        Context ctx = getContext();
        BluetoothAdapter adapter = ((BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (adapter == null || !adapter.isEnabled()) return "FAIL Bluetooth is off";
        String cmd = args.getString("cmd", "");
        long timeoutMs = Long.parseLong(args.getString("timeout", "30")) * 1000L;
        if (cmd.equals("list")) return list(adapter);
        String addr = args.getString("addr", "").toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) return "FAIL no valid -e addr";
        BluetoothDevice dev = adapter.getRemoteDevice(addr);
        switch (cmd) {
            case "unpair": return unpair(dev, timeoutMs);
            case "pair": return pair(ctx, adapter, dev, timeoutMs);
            case "accept": return accept(ctx, dev, timeoutMs);
            default: return "FAIL unknown -e cmd " + cmd;
        }
    }

    private static String list(BluetoothAdapter adapter) {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        StringBuilder sb = new StringBuilder("LIST ").append(adapter.getAddress()).append(' ').append(bonded.size());
        for (BluetoothDevice d : bonded) sb.append(" | ").append(d.getAddress()).append(' ').append(d.getName());
        return sb.toString();
    }

    private static String unpair(BluetoothDevice dev, long timeoutMs) throws Exception {
        if (dev.getBondState() == BluetoothDevice.BOND_NONE) return "UNPAIR_OK not bonded";
        Method remove = BluetoothDevice.class.getMethod("removeBond");
        if (!(Boolean) remove.invoke(dev)) return "FAIL removeBond returned false";
        if (!waitBond(dev, BluetoothDevice.BOND_NONE, timeoutMs, false)) return "FAIL still bonded after " + timeoutMs + " ms";
        return "UNPAIR_OK removed";
    }

    private String pair(Context ctx, BluetoothAdapter adapter, BluetoothDevice dev, long timeoutMs) throws Exception {
        if (dev.getBondState() == BluetoothDevice.BOND_BONDED) return "PAIR_OK already bonded";
        BroadcastReceiver confirm = confirmer(dev);
        register(ctx, confirm);
        try {
            adapter.cancelDiscovery();
            boolean asked;
            try {
                Method m = BluetoothDevice.class.getMethod("createBond", int.class);
                asked = (Boolean) m.invoke(dev, BluetoothDevice.TRANSPORT_BREDR);
            } catch (NoSuchMethodException e) {
                asked = dev.createBond();
            }
            if (!asked) return "FAIL createBond returned false";
            if (!waitBond(dev, BluetoothDevice.BOND_BONDED, timeoutMs, true)) return "FAIL not bonded after " + timeoutMs + " ms";
            return "PAIR_OK bonded";
        } finally {
            ctx.unregisterReceiver(confirm);
        }
    }

    /** The far side of a pair: confirm the request from ADDR, then wait for the bond. */
    private String accept(Context ctx, BluetoothDevice dev, long timeoutMs) {
        if (dev.getBondState() == BluetoothDevice.BOND_BONDED) return "ACCEPT_OK already bonded";
        BroadcastReceiver confirm = confirmer(dev);
        register(ctx, confirm);
        try {
            if (!waitBond(dev, BluetoothDevice.BOND_BONDED, timeoutMs, true)) return "FAIL not bonded after " + timeoutMs + " ms";
            return "ACCEPT_OK bonded";
        } finally {
            ctx.unregisterReceiver(confirm);
        }
    }

    private static void register(Context ctx, BroadcastReceiver r) {
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else ctx.registerReceiver(r, f);
    }

    /** Confirms a pairing request for DEV only, so a stray request from another device is left alone. */
    private static BroadcastReceiver confirmer(final BluetoothDevice dev) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                BluetoothDevice from = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (from != null && from.getAddress().equalsIgnoreCase(dev.getAddress())) {
                    from.setPairingConfirmation(true);
                }
            }
        };
    }

    /** CONFIRM also accepts a pending request while bonding, in case the request broadcast never reaches us. */
    private static boolean waitBond(BluetoothDevice dev, int want, long timeoutMs, boolean confirm) {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            int state = dev.getBondState();
            if (state == want) return true;
            if (confirm && state == BluetoothDevice.BOND_BONDING) {
                try { dev.setPairingConfirmation(true); } catch (RuntimeException ignored) { }
            }
            SystemClock.sleep(250);
        }
        return dev.getBondState() == want;
    }
}
