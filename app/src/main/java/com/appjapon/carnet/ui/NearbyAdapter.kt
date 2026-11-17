package com.appjapon.carnet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.appjapon.carnet.data.Poi
import com.appjapon.carnet.databinding.ItemNearbyBinding
import com.appjapon.carnet.map.Geo
import com.appjapon.carnet.map.MarkerFactory

data class NearbyItem(val poi: Poi, val distance: Double, val bearing: Double, val days: List<Int>)

class NearbyAdapter(
    private val items: List<NearbyItem>,
    private val icons: MarkerFactory,
    private val onClick: (Poi) -> Unit,
) : RecyclerView.Adapter<NearbyAdapter.VH>() {
    class VH(val b: ItemNearbyBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNearbyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = items[position]
        h.b.icon.setImageDrawable(icons.category(it.poi.category))
        h.b.name.text = it.poi.name
        val days = if (it.days.isEmpty()) "" else " · " + it.days.joinToString(", ") { d -> "J$d" }
        h.b.sub.text = it.poi.category.label + days
        h.b.distance.text = Geo.formatDistance(it.distance)
        h.b.direction.text = if (it.distance < 20_000) "${Geo.compass(it.bearing)} · ${Format.minutes(Geo.walkMinutes(it.distance).toLong())} à pied"
                             else Geo.compass(it.bearing)
        h.b.root.setOnClickListener { _ -> onClick(it.poi) }
    }
}
