package com.appjapon.carnet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.appjapon.carnet.R
import com.appjapon.carnet.data.ProgressStore
import com.appjapon.carnet.data.TripRepository
import com.appjapon.carnet.databinding.ActivityPoiBinding
import com.appjapon.carnet.map.Geo

/** Fiche détaillée d'un lieu : histoire, programme, conseils, navigation. */
class PoiActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POI = "poi"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val RESULT_SHOW_POI = "show_poi"
    }

    private lateinit var b: ActivityPoiBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityPoiBinding.inflate(layoutInflater)
        setContentView(b.root)
        WindowCompat.getInsetsController(window, b.root).isAppearanceLightStatusBars = false

        val trip = TripRepository.get(this)
        val poi = trip.pois[intent.getStringExtra(EXTRA_POI)] ?: run { finish(); return }
        val progress = ProgressStore(this)

        ViewCompat.setOnApplyWindowInsetsListener(b.poiRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.header.updatePadding(top = bars.top)
            b.scroll.updatePadding(bottom = bars.bottom)
            insets
        }

        b.header.setBackgroundColor(poi.category.color)
        b.glyph.text = poi.category.glyph
        b.category.text = "${poi.category.label} · ${poi.region}"
        b.name.text = poi.name
        b.nameJa.text = poi.nameJa
        b.summary.text = poi.summary
        b.history.text = poi.history
        b.tips.text = poi.tips
        b.tips.visibility = if (poi.tips.isBlank()) View.GONE else View.VISIBLE
        b.tipsLabel.visibility = b.tips.visibility
        b.approxWarning.visibility = if (poi.approx) View.VISIBLE else View.GONE
        b.coords.text = String.format("%.5f, %.5f", poi.lat, poi.lon)

        if (intent.hasExtra(EXTRA_LAT)) {
            val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
            val lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
            val d = Geo.distance(lat, lon, poi.lat, poi.lon)
            val dir = Geo.compass(Geo.bearing(lat, lon, poi.lat, poi.lon))
            b.distance.text = if (d < 20_000) "📍 à ${Geo.formatDistance(d)} ($dir) · ≈ ${Format.minutes(Geo.walkMinutes(d).toLong())} à pied"
                              else "📍 à ${Geo.formatDistance(d)} ($dir)"
        } else b.distance.visibility = View.GONE

        b.back.setOnClickListener { finish() }
        b.btnGo.setOnClickListener {
            val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${poi.lat},${poi.lon}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        b.btnMap.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(RESULT_SHOW_POI, poi.id))
            finish()
        }

        // Programme : les étapes du carnet qui passent par ce lieu
        val steps = trip.stepsFor(poi.id)
        b.programLabel.visibility = if (steps.isEmpty()) View.GONE else View.VISIBLE
        val pad = (8 * resources.displayMetrics.density).toInt()
        steps.forEach { (day, step) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, pad, 0, pad)
            }
            val text = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val time = if (day.approxTimes) "≈ ${step.time}" else step.time
                text = buildString {
                    append("Jour ${day.num} · ${Format.longDate(day.date)} · $time\n")
                    append(step.title)
                    if (step.optional) append("  (facultatif)")
                    if (step.meta.isNotBlank()) append("\n${step.meta}")
                    if (step.details.isNotBlank()) append("\n${step.details}")
                }
                setTextAppearance(R.style.Text_Body)
                setPadding(0, 0, 0, pad / 2)
            }
            val check = CheckBox(this).apply {
                isChecked = progress.isDone(day.num, step.index)
                contentDescription = "Visité"
                setOnCheckedChangeListener { _, v -> progress.setDone(day.num, step.index, v) }
            }
            row.addView(text); row.addView(check)
            b.program.addView(row)
            b.program.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ContextCompat.getColor(this@PoiActivity, R.color.washi_line))
            })
        }
    }
}
