package com.andrerinas.openheadunit.utils.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.BluetoothHelper

/** The real radio. Each call checks its permission first and logs the cause of a failure. */
class AndroidPairingAdapter(context: Context) : PairingAdapter {

    private val appContext = context.applicationContext
    private val radio: BluetoothAdapter? = BluetoothHelper.getBluetoothAdapter(appContext)
    private var receiver: BroadcastReceiver? = null

    /** A null permission means the level has no runtime Bluetooth permission (below API 31). */
    private fun allowed(permission: String?): Boolean =
        permission == null ||
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private inline fun <T> guarded(call: String, permission: String?, fallback: T, block: (BluetoothAdapter) -> T): T {
        val adapter = radio ?: return fallback
        if (!allowed(permission)) {
            AppLog.i("AndroidPairingAdapter: $call skipped, $permission not granted")
            return fallback
        }
        return try {
            block(adapter)
        } catch (e: SecurityException) {
            AppLog.i("AndroidPairingAdapter: $call refused: ${e.message}")
            fallback
        } catch (e: RuntimeException) {
            AppLog.i("AndroidPairingAdapter: $call failed: ${e.message}")
            fallback
        }
    }

    override fun registerEvents(sink: PairingAdapter.Events) {
        unregisterEvents()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        val next = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val address = deviceOf(intent)?.address
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        if (address == null) return
                        val major = if (intent.hasExtra(BluetoothDevice.EXTRA_CLASS)) {
                            (intent.getParcelableExtraCompat(BluetoothDevice.EXTRA_CLASS) as? android.bluetooth.BluetoothClass)
                                ?.majorDeviceClass
                        } else null
                        sink.onFound(address, intent.getStringExtra(BluetoothDevice.EXTRA_NAME), major)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> sink.onDiscoveryFinished()
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        if (address == null) return
                        sink.onBondChanged(
                            address,
                            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, PairingPolicy.BOND_NONE),
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, PairingPolicy.BOND_NONE)
                        )
                    }
                }
            }
        }
        receiver = next
        // The Bluetooth app sends these, so EXPORTED: NOT_EXPORTED drops them below API 33.
        ContextCompat.registerReceiver(appContext, next, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun unregisterEvents() {
        val current = receiver ?: return
        receiver = null
        try {
            appContext.unregisterReceiver(current)
        } catch (e: IllegalArgumentException) {
            AppLog.i("AndroidPairingAdapter: unregister failed: ${e.message}")
        }
    }

    override fun startDiscovery(): Boolean = guarded("startDiscovery", scanPermission(), false) { it.startDiscovery() }

    override fun cancelDiscovery() {
        guarded("cancelDiscovery", scanPermission(), false) { it.cancelDiscovery() }
    }

    override fun isDiscovering(): Boolean = guarded("isDiscovering", scanPermission(), false) { it.isDiscovering }

    override fun isVisible(): Boolean = guarded("getScanMode", scanPermission(), false) {
        it.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
    }

    override fun createBond(address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return false
        return guarded("createBond", connectPermission(), false) {
            it.getRemoteDevice(address).createBond()
        }
    }

    override fun bondState(address: String): Int =
        guarded("getBondState", connectPermission(), PairingPolicy.BOND_NONE) {
            it.getRemoteDevice(address).bondState
        }

    private fun scanPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else null

    private fun connectPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    @Suppress("DEPRECATION")
    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(key: String): android.os.Parcelable? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, android.bluetooth.BluetoothClass::class.java)
        } else {
            getParcelableExtra(key)
        }
}
