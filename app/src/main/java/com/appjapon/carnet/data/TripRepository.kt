package com.appjapon.carnet.data

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object TripRepository {
    @Volatile
    private var cached: Trip? = null

    fun get(context: Context): Trip = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    private fun load(context: Context): Trip {
        val text = context.assets.open("trip.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val pois = LinkedHashMap<String, Poi>()
        root.getJSONArray("pois").forEachObject { o ->
            val p = Poi(
                id = o.getString("id"),
                name = o.getString("name"),
                nameJa = o.optString("ja"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                category = Category.from(o.getString("cat")),
                importance = o.optInt("imp", 2),
                radius = o.optInt("radius", 120),
                region = o.optString("region"),
                approx = o.optBoolean("approx", false),
                summary = o.optString("summary"),
                history = o.optString("history"),
                tips = o.optString("tips"),
            )
            pois[p.id] = p
        }
        val days = mutableListOf<Day>()
        root.getJSONArray("days").forEachObject { o ->
            val steps = mutableListOf<Step>()
            o.getJSONArray("steps").forEachObject { s ->
                steps += Step(
                    index = steps.size,
                    time = s.optString("time"),
                    type = s.optString("type"),
                    title = s.getString("title"),
                    details = s.optString("details"),
                    poiIds = s.getJSONArray("pois").strings().filter { it in pois },
                    optional = s.optBoolean("optional", false),
                    meta = s.optString("meta"),
                )
            }
            days += Day(
                num = o.getInt("num"),
                date = LocalDate.parse(o.getString("date")),
                part = o.optString("part"),
                color = Color.parseColor(o.optString("color", "#B5332E")),
                title = o.getString("title"),
                subtitle = o.optString("subtitle"),
                night = o.optString("night"),
                transport = o.optString("transport"),
                approxTimes = o.optBoolean("approxTimes", false),
                steps = steps,
                notes = o.optJSONArray("notes")?.strings() ?: emptyList(),
            )
        }
        return Trip(root.optString("title"), LocalDate.parse(root.getString("start")), pois, days)
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
