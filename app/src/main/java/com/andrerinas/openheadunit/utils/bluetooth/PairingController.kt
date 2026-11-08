package com.andrerinas.openheadunit.utils.bluetooth

import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog

enum class RowState { IDLE, ASKING, FAILED, DONE }

data class FoundDevice(val address: String, val name: String?, val majorClass: Int?, val bonded: Boolean)

data class PairingRow(
    val address: String,
    val name: String?,
    val kind: PairingPolicy.Kind,
    val state: RowState
)

/** The radio as the controller sees it; the real one checks permissions and catches errors. */
interface PairingAdapter {
    fun registerEvents(sink: Events)
    fun unregisterEvents()
    fun startDiscovery(): Boolean
    fun cancelDiscovery()
    fun isDiscovering(): Boolean
    fun isVisible(): Boolean
    fun createBond(address: String): Boolean
    fun bondState(address: String): Int

    interface Events {
        fun onFound(address: String, name: String?, majorClass: Int?)
        fun onDiscoveryFinished()
        fun onBondChanged(address: String, previous: Int, current: Int)
    }
}

/**
 * Orders search, cancel and bond for the pairing screen. A search must end before a bond starts,
 * and the system pairing dialog opens only inside the windows that PairingPolicy checks.
 */
class PairingController(
    private val adapter: PairingAdapter,
    private val sdk: Int,
    private val listener: Listener,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val log: (String) -> Unit = AppLog::i
) : PairingAdapter.Events {

    interface Listener {
        fun onRowsChanged(rows: List<PairingRow>) {}
        fun onSearchChanged(searching: Boolean, found: Int) {}
        fun onPromptNeeded() {}
        fun onPrerequisitesChanged() {}
        fun onSearchFailed() {}
        fun onHoldChanged(held: Boolean) {}
        fun onAskingHint(address: String) {}
        fun onBondDone(address: String) {}
    }

    private var started = false
    private var searching = false
    private var resumeSearch = false
    private var rows: List<PairingRow> = emptyList()
    private val foundThisSearch = LinkedHashSet<String>()
    private var lastSearchEndMs: Long? = null
    private var lastGrantMs: Long? = null
    private var grantedSeconds = PairingPolicy.VISIBLE_SECONDS
    private var freshSearchAddress: String? = null
    private var freshSearchEndMs = 0L
    private var promptBondAddress: String? = null
    private var promptAvailable = true
    private var pendingAddress: String? = null
    private val askingSinceMs = HashMap<String, Long>()
    private val hintShown = HashSet<String>()
    private var holdTaken = false

    val bondInFlight: Boolean get() = pendingAddress != null || rows.any { it.state == RowState.ASKING }

    fun visibleMsLeft(): Long {
        val grant = lastGrantMs ?: return 0L
        return PairingPolicy.remainingMs(grant, grantedSeconds, clock())
    }

    fun isVisible(): Boolean = visibleMsLeft() > 0 || adapter.isVisible()

    fun start() {
        if (started) return
        started = true
        adapter.registerEvents(this)
    }

    /** Clears every field so a later start() begins clean. */
    fun stop() {
        if (!started) return
        if (searching || freshSearchAddress != null) adapter.cancelDiscovery()
        adapter.unregisterEvents()
        releaseHold()
        log("PairingController: stopped")
        started = false
        searching = false
        resumeSearch = false
        rows = emptyList()
        foundThisSearch.clear()
        lastSearchEndMs = null
        lastGrantMs = null
        grantedSeconds = PairingPolicy.VISIBLE_SECONDS
        freshSearchAddress = null
        promptBondAddress = null
        promptAvailable = true
        pendingAddress = null
        askingSinceMs.clear()
        hintShown.clear()
    }

    fun onStart() {
        for (row in rows.filter { it.state == RowState.ASKING } + pendingRows()) {
            applyBondState(row.address, adapter.bondState(row.address))
        }
        if (resumeSearch) {
            resumeSearch = false
            beginSearch()
        }
    }

    fun onStop() {
        if (searching) {
            adapter.cancelDiscovery()
            searching = false
            freshSearchAddress = null
            resumeSearch = true
        }
    }

    fun search() {
        if (searching) return
        rows = rows.filter { it.state != RowState.IDLE }
        foundThisSearch.clear()
        listener.onRowsChanged(rows)
        beginSearch()
    }

    fun tap(address: String) {
        if (freshSearchAddress != null) return
        val now = clock()
        if (searching) {
            adapter.cancelDiscovery()
            searching = false
            lastSearchEndMs = now
            listener.onSearchChanged(false, foundThisSearch.size)
        }
        if (PairingPolicy.needsFreshSearch(sdk, lastSearchEndMs?.let { now - it })) {
            log("PairingController: fresh search before bond")
            freshSearchAddress = address
            freshSearchEndMs = now + FRESH_SEARCH_MS
            if (!adapter.startDiscovery()) {
                log("PairingController: discovery failed")
                freshSearchAddress = null
                requestBond(address)
                return
            }
            // Counted as a search so onStop cancels it; the screen shows no search for it.
            searching = true
            return
        }
        requestBond(address)
    }

    fun onPromptResult(resultCode: Int) {
        val waiting = promptBondAddress
        promptBondAddress = null
        if (resultCode == RESULT_CANCELED) {
            log("PairingController: visible request declined")
            return
        }
        grantedSeconds = resultCode
        lastGrantMs = clock()
        log("PairingController: visible via system prompt")
        listener.onPrerequisitesChanged()
        if (waiting != null) bond(waiting)
    }

    /** No prompt route: bond the waiting phone anyway, and ask for no prompt again. */
    fun onPromptUnavailable() {
        val waiting = promptBondAddress
        promptBondAddress = null
        promptAvailable = false
        log("PairingController: visible unavailable")
        if (waiting != null) bond(waiting)
    }

    fun tick(now: Long = clock()) {
        val fresh = freshSearchAddress
        if (fresh != null && now >= freshSearchEndMs) {
            freshSearchAddress = null
            searching = false
            adapter.cancelDiscovery()
            lastSearchEndMs = now
            log("PairingController: discovery cancelled before bond")
            bond(fresh)
        }
        for (row in rows) {
            val since = askingSinceMs[row.address] ?: continue
            if (row.state == RowState.ASKING && PairingPolicy.askingHint(now - since) && hintShown.add(row.address)) {
                log("PairingController: asking hint shown")
                listener.onAskingHint(row.address)
            }
        }
    }

    override fun onFound(address: String, name: String?, majorClass: Int?) {
        foundThisSearch.add(address)
        val bonded = adapter.bondState(address) == PairingPolicy.BOND_BONDED
        val next = PairingPolicy.mergeFound(rows, FoundDevice(address, name, majorClass, bonded))
        if (next !== rows) {
            rows = next
            listener.onRowsChanged(rows)
        }
    }

    override fun onDiscoveryFinished() {
        if (!searching || freshSearchAddress != null) return
        searching = false
        lastSearchEndMs = clock()
        log("PairingController: found ${foundThisSearch.size}")
        log("PairingController: discovery finished ${foundThisSearch.size}")
        listener.onSearchChanged(false, foundThisSearch.size)
    }

    override fun onBondChanged(address: String, previous: Int, current: Int) {
        val event = PairingPolicy.bondTransition(previous, current)
        if (event == PairingPolicy.BondEvent.IGNORE) return
        applyEvent(address, event)
    }

    private fun beginSearch() {
        if (adapter.startDiscovery()) {
            searching = true
            log("PairingController: discovery started")
            listener.onSearchChanged(true, foundThisSearch.size)
        } else {
            log("PairingController: discovery failed")
            listener.onSearchFailed()
            listener.onPrerequisitesChanged()
        }
    }

    private fun requestBond(address: String) {
        if (promptAvailable && PairingPolicy.needsFreshPrompt(sdk, lastGrantMs?.let { clock() - it }, grantedSeconds)) {
            log("PairingController: fresh prompt before bond")
            promptBondAddress = address
            listener.onPromptNeeded()
            return
        }
        bond(address)
    }

    private fun bond(address: String) {
        holdTaken = true
        listener.onHoldChanged(true)
        log("PairingController: bond requested $address")
        if (adapter.createBond(address)) {
            pendingAddress = address
            return
        }
        log("PairingController: bond request refused $address")
        setState(address, RowState.FAILED)
        releaseHold()
    }

    private fun applyBondState(address: String, state: Int) {
        val wasAsking = rows.any { it.address == address && it.state == RowState.ASKING }
        when (state) {
            PairingPolicy.BOND_BONDED -> applyEvent(address, PairingPolicy.BondEvent.DONE)
            PairingPolicy.BOND_BONDING -> applyEvent(address, PairingPolicy.BondEvent.ASKING)
            else -> if (wasAsking) applyEvent(address, PairingPolicy.BondEvent.FAILED)
        }
    }

    private fun applyEvent(address: String, event: PairingPolicy.BondEvent) {
        val target = when (event) {
            PairingPolicy.BondEvent.ASKING -> RowState.ASKING
            PairingPolicy.BondEvent.FAILED -> RowState.FAILED
            PairingPolicy.BondEvent.DONE -> RowState.DONE
            PairingPolicy.BondEvent.IGNORE -> return
        }
        if (rows.none { it.address == address }) {
            rows = rows + PairingRow(address, null, PairingPolicy.Kind.OTHER, RowState.IDLE)
        }
        if (rows.first { it.address == address }.state == target) return
        log("PairingController: bond state ${target.name} $address")
        if (target == RowState.ASKING) askingSinceMs[address] = clock()
        setState(address, target)
        if (target != RowState.ASKING) {
            if (pendingAddress == address) pendingAddress = null
            askingSinceMs.remove(address)
            releaseHold()
            if (target == RowState.DONE) listener.onBondDone(address)
        }
    }

    private fun setState(address: String, state: RowState) {
        rows = rows.map { if (it.address == address) it.copy(state = state) else it }
        listener.onRowsChanged(rows)
    }

    private fun pendingRows(): List<PairingRow> =
        pendingAddress?.let { p -> rows.filter { it.address == p && it.state != RowState.ASKING } }.orEmpty()

    private fun releaseHold() {
        if (!holdTaken) return
        holdTaken = false
        listener.onHoldChanged(false)
    }

    private companion object {
        const val FRESH_SEARCH_MS = 2_000L
        const val RESULT_CANCELED = 0
    }
}
