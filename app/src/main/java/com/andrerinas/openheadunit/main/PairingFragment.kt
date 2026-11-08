package com.andrerinas.openheadunit.main

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.ExternalBtTransportPolicy
import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.NativeAaHandshakeManager
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.andrerinas.openheadunit.utils.bluetooth.AndroidPairingAdapter
import com.andrerinas.openheadunit.utils.bluetooth.PairingController
import com.andrerinas.openheadunit.utils.bluetooth.PairingPolicy
import com.andrerinas.openheadunit.utils.bluetooth.PairingPolicy.Prerequisite
import com.andrerinas.openheadunit.utils.bluetooth.PairingRow
import com.andrerinas.openheadunit.utils.bluetooth.RowState
import com.google.android.material.appbar.MaterialToolbar

/** Pairs a new phone: makes the unit visible, searches, and bonds the phone the driver taps. */
class PairingFragment : Fragment(), PairingController.Listener {

    private var controller: PairingController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var holdRelease: (() -> Unit)? = null
    private var promptAvailable = true
    private var promptDeclined = false
    private var searching = false
    private var searchFailed = false
    private var searchedOnce = false
    private var foundCount = 0
    private var rows: List<PairingRow> = emptyList()
    private var anyDone = false
    private var hintShown = false
    private var wirelessRearmed = false

    private lateinit var prerequisiteContainer: LinearLayout
    private lateinit var visibleStatus: TextView
    private lateinit var routePhone: TextView
    private lateinit var foundCountView: TextView
    private lateinit var searchAgain: Button
    private lateinit var emptyList: TextView
    private lateinit var deviceContainer: LinearLayout
    private lateinit var askingHint: TextView
    private lateinit var openBluetoothSettings: Button
    private lateinit var doneText: TextView

    private val tickRunnable = object : Runnable {
        override fun run() {
            controller?.tick()
            keepHold()
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val enableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        releaseHold()
        render()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        releaseHold()
        render()
    }

    private val promptLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        releaseHold()
        promptDeclined = result.resultCode == android.app.Activity.RESULT_CANCELED
        controller?.onPromptResult(result.resultCode)
        render()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_pairing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        prerequisiteContainer = view.findViewById(R.id.prerequisiteContainer)
        visibleStatus = view.findViewById(R.id.visibleStatus)
        routePhone = view.findViewById(R.id.routePhone)
        foundCountView = view.findViewById(R.id.foundCount)
        searchAgain = view.findViewById(R.id.searchAgain)
        emptyList = view.findViewById(R.id.emptyList)
        deviceContainer = view.findViewById(R.id.deviceContainer)
        askingHint = view.findViewById(R.id.askingHint)
        openBluetoothSettings = view.findViewById(R.id.openBluetoothSettings)
        doneText = view.findViewById(R.id.doneText)

        val ctx = requireContext()
        controller = PairingController(AndroidPairingAdapter(ctx), Build.VERSION.SDK_INT, this).also { it.start() }
        searchAgain.setOnClickListener { startSearch() }
        val settingsIntent = Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)
        val settingsResolves = settingsIntent.resolveActivity(ctx.packageManager) != null
        // Held like every other system window here, so no poke runs while a bond is in flight.
        openBluetoothSettings.setOnClickListener { launchHeld { startActivity(settingsIntent) } }
        openBluetoothSettings.tag = settingsResolves
        render()
        handler.postDelayed(tickRunnable, TICK_MS)
    }

    override fun onStart() {
        super.onStart()
        controller?.onStart()
    }

    override fun onResume() {
        super.onResume()
        // SettingsActivity.onResume has cleared the hold, so a handle from before the pause is stale.
        releaseHold()
        keepHold()
        maybeSearch()
        render()
    }

    override fun onStop() {
        controller?.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(tickRunnable)
        controller?.stop()
        controller = null
        releaseHold()
        super.onDestroyView()
    }

    override fun onRowsChanged(rows: List<PairingRow>) {
        this.rows = rows
        foundCount = rows.size
        if (isAdded) render()
    }

    override fun onSearchChanged(searching: Boolean, found: Int) {
        this.searching = searching
        foundCount = found
        if (searching) searchFailed = false
        if (isAdded) render()
    }

    override fun onSearchFailed() {
        searchFailed = true
    }

    override fun onPrerequisitesChanged() {
        if (isAdded) render()
    }

    override fun onPromptNeeded() {
        launchPrompt()
    }

    override fun onHoldChanged(held: Boolean) {
        if (held) takeHold() else releaseHold()
    }

    override fun onAskingHint(address: String) {
        hintShown = true
        if (isAdded) render()
    }

    override fun onBondDone(address: String) {
        anyDone = true
        if (!wirelessRearmed) {
            // The stack is down while this screen is open; ask for one re-arm when it closes.
            wirelessRearmed = true
            AapService.applyWirelessSettings(requireContext().applicationContext, startWireless = true)
        }
    }

    private fun startSearch() {
        searchedOnce = true
        controller?.search()
    }

    private fun maybeSearch() {
        if (searchedOnce || searching) return
        val open = missing().filter { it != Prerequisite.VISIBLE }
        if (open.isEmpty()) startSearch()
    }

    private fun takeHold() {
        val activity = activity as? SettingsActivity ?: return
        activity.openSetup {
            holdRelease = activity.setupRequestCompletion()
            true
        }
    }

    private fun releaseHold() {
        val release = holdRelease ?: return
        holdRelease = null
        release()
    }

    /** Holds the settings screen while the unit is visible or a bond is in flight. */
    private fun keepHold() {
        val c = controller ?: return
        if (!isResumed) return
        val holds = PairingPolicy.holdsSettings(c.visibleMsLeft(), c.bondInFlight)
        if (holds && holdRelease == null) takeHold() else if (!holds && holdRelease != null) releaseHold()
    }

    /** Launches a system window with the settings hold taken, and gives the hold back on failure. */
    private fun launchHeld(block: () -> Unit): PairingPolicy.PromptLaunch {
        val activity = activity as? SettingsActivity
        val previous = holdRelease
        return try {
            if (activity == null) block() else activity.openSetup {
                holdRelease = activity.setupRequestCompletion()
                block()
                true
            }
            PairingPolicy.PromptLaunch.OK
        } catch (e: ActivityNotFoundException) {
            holdRelease = previous
            AppLog.i("PairingFragment: no activity for the request: ${e.message}")
            PairingPolicy.PromptLaunch.NO_ACTIVITY
        } catch (e: SecurityException) {
            holdRelease = previous
            AppLog.i("PairingFragment: request refused: ${e.message}")
            PairingPolicy.PromptLaunch.REFUSED
        }
    }

    private fun launchPrompt() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, PairingPolicy.VISIBLE_SECONDS)
        val result = launchHeld { promptLauncher.launch(intent) }
        if (PairingPolicy.latchesPromptUnavailable(result)) {
            promptAvailable = false
            controller?.onPromptUnavailable()
        }
        render()
    }

    private fun nearbyGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || nearbyPermissions().all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun fineGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationOn(ctx: Context): Boolean {
        val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try { LocationManagerCompat.isLocationEnabled(manager) } catch (e: RuntimeException) { false }
    }

    private fun radioOn(ctx: Context): Boolean = try {
        BluetoothHelper.getBluetoothAdapter(ctx)?.isEnabled == true
    } catch (e: SecurityException) { false }

    private fun missing(): List<Prerequisite> {
        val ctx = context ?: return emptyList()
        return PairingPolicy.prerequisites(
            sdk = Build.VERSION.SDK_INT,
            btOn = radioOn(ctx),
            hasNearbyPermission = nearbyGranted(ctx),
            hasFineLocation = fineGranted(ctx),
            locationOn = locationOn(ctx),
            visible = controller?.isVisible() == true
        )
    }

    private fun fix(item: Prerequisite) {
        val ctx = requireContext()
        when (item) {
            Prerequisite.BLUETOOTH_ON -> {
                if (!nearbyGranted(ctx)) {
                    launchHeld { permissionLauncher.launch(nearbyPermissions()) }
                } else {
                    launchHeld { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }
            }
            Prerequisite.NEARBY_DEVICES -> launchHeld { permissionLauncher.launch(nearbyPermissions()) }
            Prerequisite.FINE_LOCATION -> launchHeld {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            Prerequisite.LOCATION_ON -> launchHeld {
                startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            Prerequisite.VISIBLE -> {
                // The visible prompt needs the advertise permission from API 31.
                if (!nearbyGranted(ctx)) {
                    launchHeld { permissionLauncher.launch(nearbyPermissions()) }
                } else {
                    promptDeclined = false
                    launchPrompt()
                }
            }
        }
    }

    private fun label(item: Prerequisite): Int = when (item) {
        Prerequisite.BLUETOOTH_ON -> R.string.pairing_need_bluetooth
        Prerequisite.NEARBY_DEVICES -> R.string.pairing_need_nearby
        Prerequisite.FINE_LOCATION -> R.string.pairing_need_fine_location
        Prerequisite.LOCATION_ON -> R.string.pairing_need_location_on
        Prerequisite.VISIBLE -> R.string.pairing_need_visible
    }

    private fun buttonLabel(item: Prerequisite): Int = when (item) {
        Prerequisite.BLUETOOTH_ON -> R.string.pairing_fix_bluetooth
        Prerequisite.NEARBY_DEVICES, Prerequisite.FINE_LOCATION -> R.string.pairing_fix_permission
        Prerequisite.LOCATION_ON -> R.string.pairing_fix_location_on
        Prerequisite.VISIBLE -> R.string.pairing_fix_visible
    }

    private fun render() {
        if (!isAdded || view == null || controller == null) return
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)

        val unmet = missing()
        val route = PairingPolicy.visibleRoute(promptAvailable)
        prerequisiteContainer.removeAllViews()
        for (item in unmet) {
            if (item == Prerequisite.VISIBLE && route == PairingPolicy.VisibleRoute.NONE) continue
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(ctx).apply {
                setText(label(item))
                setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_light))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(ctx).apply {
                setText(buttonLabel(item))
                setOnClickListener { fix(item) }
            })
            prerequisiteContainer.addView(row)
        }

        val leftMs = controller?.visibleMsLeft() ?: 0L
        visibleStatus.text = when {
            route == PairingPolicy.VisibleRoute.NONE -> getString(R.string.pairing_visible_unavailable)
            promptDeclined && Prerequisite.VISIBLE in unmet -> getString(R.string.pairing_visible_declined)
            leftMs > 0 -> getString(R.string.pairing_visible_left, ((leftMs + 999) / 1000).toInt())
            else -> getString(R.string.pairing_not_visible)
        }
        routePhone.text = getString(R.string.pairing_route_phone, unitName(ctx))

        foundCountView.text = when {
            searchFailed -> getString(R.string.pairing_search_failed)
            searching -> getString(R.string.pairing_searching, foundCount)
            else -> getString(R.string.pairing_found_count, foundCount)
        }
        searchAgain.isEnabled = !searching
        emptyList.visibility = if (searchedOnce && !searching && rows.isEmpty()) View.VISIBLE else View.GONE

        deviceContainer.removeAllViews()
        for (row in rows) {
            val item = inflater.inflate(R.layout.list_item_pairing_device, deviceContainer, false)
            item.findViewById<TextView>(R.id.deviceName).text = row.name ?: row.address
            item.findViewById<ImageView>(R.id.deviceIcon).setImageResource(
                if (row.kind == PairingPolicy.Kind.PHONE) R.drawable.ic_phone else R.drawable.ic_headphones
            )
            val kind = getString(
                if (row.kind == PairingPolicy.Kind.PHONE) R.string.pairing_kind_phone else R.string.pairing_kind_other
            )
            val state = getString(
                when (row.state) {
                    RowState.IDLE -> R.string.pairing_state_tap
                    RowState.ASKING -> R.string.pairing_state_asking
                    RowState.FAILED -> R.string.pairing_state_failed
                    RowState.DONE -> R.string.pairing_state_done
                }
            )
            item.findViewById<TextView>(R.id.deviceStatus).text = "$kind, $state"
            if (row.state == RowState.IDLE || row.state == RowState.FAILED) {
                item.setOnClickListener { controller?.tap(row.address) }
            } else {
                item.isClickable = false
            }
            deviceContainer.addView(item)
        }

        val asking = rows.any { it.state == RowState.ASKING }
        val showHint = hintShown && asking
        askingHint.visibility = if (showHint) View.VISIBLE else View.GONE
        openBluetoothSettings.visibility =
            if (showHint && openBluetoothSettings.tag == true) View.VISIBLE else View.GONE
        doneText.visibility = if (anyDone) View.VISIBLE else View.GONE
    }

    private fun unitName(ctx: Context): String = try {
        BluetoothHelper.getBluetoothAdapter(ctx)?.name ?: Build.MODEL
    } catch (e: SecurityException) {
        Build.MODEL
    }

    companion object {
        private const val TICK_MS = 1_000L
        private fun nearbyPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                emptyArray()
            }

        /** Whether the pairing entry shows: not on an external-module route or during a session. */
        fun entryAvailable(context: Context): Boolean {
            val route = NativeAaHandshakeManager.transportRoute(context)
            val app = App.provide(context)
            return PairingPolicy.entryAvailable(
                sdk = Build.VERSION.SDK_INT,
                externalModule = ExternalBtTransportPolicy.usesExternalModule(route),
                routeBlocked = route == ExternalBtTransportPolicy.Route.BLOCKED,
                radioServiceName = app.settings.bluetoothManagerServiceName,
                adapterPresent = BluetoothHelper.getBluetoothAdapter(context) != null,
                sessionLive = app.commManager.isConnected
            )
        }

        /** Opens the pairing screen from outside the settings activity, as onboarding does. */
        fun open(context: Context) {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_DESTINATION, R.id.pairingFragment)
            )
        }
    }
}
