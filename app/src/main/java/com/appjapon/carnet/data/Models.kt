package com.appjapon.carnet.data

import android.graphics.Color
import java.time.LocalDate

enum class Category(val key: String, val label: String, val glyph: String, val color: Int) {
    TEMPLE("temple", "Temple bouddhique", "寺", Color.parseColor("#6A4C93")),
    SHRINE("shrine", "Sanctuaire shintō", "社", Color.parseColor("#C0392B")),
    CASTLE("castle", "Château", "城", Color.parseColor("#37474F")),
    QUARTIER("quartier", "Quartier historique", "町", Color.parseColor("#D17A22")),
    VILLAGE("village", "Village traditionnel", "村", Color.parseColor("#A0522D")),
    NATURE("nature", "Nature & paysage", "景", Color.parseColor("#2E7D32")),
    VIEWPOINT("viewpoint", "Belvédère", "望", Color.parseColor("#00838F")),
    HISTORIC("historic", "Site historique", "史", Color.parseColor("#795548")),
    FOOD("food", "Gastronomie", "食", Color.parseColor("#AD1457")),
    ONSEN("onsen", "Bains & onsen", "湯", Color.parseColor("#1565C0")),
    TRANSPORT("transport", "Transport", "駅", Color.parseColor("#546E7A")),
    HOTEL("hotel", "Hébergement", "宿", Color.parseColor("#4E342E"));

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: HISTORIC
    }
}

data class Poi(
    val id: String,
    val name: String,
    val nameJa: String,
    val lat: Double,
    val lon: Double,
    val category: Category,
    /** 1 = visible dès le zoom ville, 2 = zoom quartier, 3 = zoom rue */
    val importance: Int,
    /** Rayon (m) dans lequel on considère être « arrivé ». */
    val radius: Int,
    val region: String,
    val approx: Boolean,
    val summary: String,
    val history: String,
    val tips: String,
)

data class Step(
    val index: Int,
    val time: String,
    val type: String,
    val title: String,
    val details: String,
    val poiIds: List<String>,
    val optional: Boolean,
    val meta: String,
) {
    val trackable get() = poiIds.isNotEmpty()
}

data class Day(
    val num: Int,
    val date: LocalDate,
    val part: String,
    val color: Int,
    val title: String,
    val subtitle: String,
    val night: String,
    val transport: String,
    val approxTimes: Boolean,
    val steps: List<Step>,
    val notes: List<String>,
) {
    val trackableSteps get() = steps.filter { it.trackable }
}

data class Trip(
    val title: String,
    val start: LocalDate,
    val pois: Map<String, Poi>,
    val days: List<Day>,
) {
    fun day(num: Int) = days.firstOrNull { it.num == num }
    fun todayDay(today: LocalDate = LocalDate.now()) = days.firstOrNull { it.date == today }

    /** Étapes (jour, étape) qui passent par ce lieu. */
    fun stepsFor(poiId: String): List<Pair<Day, Step>> =
        days.flatMap { d -> d.steps.filter { poiId in it.poiIds }.map { d to it } }
}
