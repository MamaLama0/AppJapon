package com.appjapon.carnet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.appjapon.carnet.data.Day
import com.appjapon.carnet.databinding.ItemDayBinding

class DayAdapter(private val onSelect: (Day) -> Unit) : RecyclerView.Adapter<DayAdapter.VH>() {
    class VH(val b: ItemDayBinding) : RecyclerView.ViewHolder(b.root)

    private var days: List<Day> = emptyList()
    private var progress: Map<Int, Pair<Int, Int>> = emptyMap()

    fun submit(days: List<Day>, progress: Map<Int, Pair<Int, Int>>) {
        this.days = days; this.progress = progress
        notifyDataSetChanged()
    }

    override fun getItemCount() = days.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = days[position]
        h.b.num.text = "J${d.num}"
        Format.circle(h.b.num, d.color)
        h.b.date.text = Format.longDate(d.date)
        h.b.date.setTextColor(d.color)
        h.b.title.text = d.title
        h.b.subtitle.text = d.subtitle
        val (done, total) = progress[d.num] ?: (0 to 0)
        h.b.progress.text = if (done == 0) "$total étapes · nuit : ${d.night}" else "$done / $total étapes faites"
        h.b.root.setOnClickListener { onSelect(d) }
    }
}
