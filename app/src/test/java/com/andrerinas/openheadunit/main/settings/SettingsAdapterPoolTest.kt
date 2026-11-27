package com.andrerinas.openheadunit.main.settings

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.R
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The spare rows that keep the first scroll smooth, and the background a rebind does not rebuild. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsAdapterPoolTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)

    private fun listWith(adapter: SettingsAdapter) = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
    }

    private fun toggle(id: String) = SettingItem.ToggleSettingEntry(
        stableId = id,
        nameResId = R.string.kill_on_disconnect,
        descriptionResId = null,
        isChecked = false,
        onCheckedChanged = {}
    )

    @Test
    fun `warming builds every spare row of the plan`() {
        val adapter = SettingsAdapter()
        val list = listWith(adapter)
        adapter.raisePoolLimits(list)
        val pending = adapter.warmQueue()

        var steps = 1
        while (adapter.warmStep(list, pending)) steps++

        assertEquals(SettingsAdapter.WARM_PLAN.sumOf { it.second }, steps)
        SettingsAdapter.WARM_PLAN.forEach { (type, count) ->
            assertEquals(count, list.recycledViewPool.getRecycledViewCount(type))
        }
    }

    @Test
    fun `warming stops when the list has another adapter`() {
        val adapter = SettingsAdapter()
        val list = listWith(adapter)
        val pending = adapter.warmQueue()
        val planned = pending.size
        list.adapter = SettingsAdapter()

        assertFalse(adapter.warmStep(list, pending))
        assertEquals(planned, pending.size)
    }

    @Test
    fun `a rebind to the same group shape keeps the background`() {
        val adapter = SettingsAdapter()
        val list = listWith(adapter)
        adapter.submitList(listOf(toggle("a"), toggle("b"), toggle("c"), toggle("d")))
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(1))

        adapter.bindViewHolder(holder, 1)
        val middle = holder.itemView.background
        adapter.bindViewHolder(holder, 2)
        assertSame(middle, holder.itemView.background)

        adapter.bindViewHolder(holder, 0)
        assertNotSame(middle, holder.itemView.background)
    }

    private fun segmented(id: String, vararg options: String) = SettingItem.SegmentedButtonSettingEntry(
        stableId = id,
        nameResId = R.string.wireless_mode,
        options = options.toList(),
        selectedIndex = 0,
        onOptionSelected = {}
    )

    @Test
    fun `a rebind with the same option count keeps the segment corners`() {
        val adapter = SettingsAdapter()
        val list = listWith(adapter)
        adapter.submitList(listOf(segmented("a", "1", "2", "3"), segmented("b", "x", "y", "z"), segmented("c", "p", "q")))
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(0))
        val first = holder.itemView.findViewById<MaterialButton>(R.id.btnOption1)
        val second = holder.itemView.findViewById<MaterialButton>(R.id.btnOption2)

        adapter.bindViewHolder(holder, 0)
        val corners = second.shapeAppearanceModel
        adapter.bindViewHolder(holder, 1)
        assertSame(corners, second.shapeAppearanceModel)
        assertEquals("x", first.text.toString())

        adapter.bindViewHolder(holder, 2)
        assertNotSame(corners, second.shapeAppearanceModel)
    }
}
