package com.appjapon.carnet.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.appjapon.carnet.R
import com.appjapon.carnet.data.Day
import com.appjapon.carnet.data.Step
import com.appjapon.carnet.databinding.ItemStepBinding

class StepAdapter(
    private val onOpen: (Step) -> Unit,
    private val onToggle: (Step, Boolean) -> Unit,
) : RecyclerView.Adapter<StepAdapter.VH>() {

    class VH(val b: ItemStepBinding) : RecyclerView.ViewHolder(b.root)

    private var day: Day? = null
    private var done: Set<Int> = emptySet()
    private var current: Int? = null
    private var numbers: Map<Int, Int> = emptyMap()

    fun submit(day: Day, done: Set<Int>, current: Int?) {
        this.day = day; this.done = done; this.current = current
        numbers = day.trackableSteps.mapIndexed { i, s -> s.index to i + 1 }.toMap()
        notifyDataSetChanged()
    }

    override fun getItemCount() = day?.steps?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStepBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = day ?: return
        val s = d.steps[position]
        val ctx = h.itemView.context
        val isDone = s.index in done
        val isCurrent = s.index == current
        val b = h.b

        b.time.text = if (d.approxTimes && s.time.isNotEmpty()) "≈ ${s.time}" else s.time
        b.type.text = s.type
        b.title.text = s.title
        b.details.text = s.details
        b.details.visibility = if (s.details.isBlank()) View.GONE else View.VISIBLE
        b.meta.text = s.meta
        b.meta.visibility = if (s.meta.isBlank()) View.GONE else View.VISIBLE
        b.optional.visibility = if (s.optional) View.VISIBLE else View.GONE

        if (s.trackable) {
            b.badge.text = if (isDone) "✓" else numbers[s.index]?.toString() ?: ""
            Format.circle(b.badge, if (isDone) ContextCompat.getColor(ctx, R.color.done) else d.color)
            b.check.visibility = View.VISIBLE
        } else {
            b.badge.text = "→"
            Format.circle(b.badge, Color.parseColor("#B0B6BC"))
            b.check.visibility = View.INVISIBLE
        }
        b.title.alpha = if (isDone) 0.55f else 1f
        b.details.alpha = if (isDone) 0.55f else 1f

        b.root.setBackgroundColor(if (isCurrent) (d.color and 0x00FFFFFF) or 0x1A000000 else Color.TRANSPARENT)

        b.check.setOnCheckedChangeListener(null)
        b.check.isChecked = isDone
        b.check.setOnCheckedChangeListener { _, checked -> onToggle(s, checked) }
        b.root.setOnClickListener { onOpen(s) }
    }
}
