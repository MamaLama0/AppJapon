package com.appjapon.carnet.map

import org.osmdroid.util.GeoPoint
import kotlin.math.*

object Geo {
    private const val R = 6_371_000.0

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(a))
    }

    fun distance(a: GeoPoint, b: GeoPoint) = distance(a.latitude, a.longitude, b.latitude, b.longitude)

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun compass(bearing: Double): String =
        arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[(((bearing + 22.5) % 360) / 45).toInt()]

    fun formatDistance(m: Double): String = when {
        m < 1000 -> "${(m / 10).roundToInt() * 10} m"
        m < 10_000 -> String.format("%.1f km", m / 1000).replace('.', ',')
        else -> "${(m / 1000).roundToInt()} km"
    }

    /** Durée de marche (≈ 4,5 km/h) */
    fun walkMinutes(m: Double) = max(1, (m / 75.0).roundToInt())

    data class Projection(
        val distanceToTrack: Double,
        /** Distance parcourue le long du tracé jusqu'au point projeté. */
        val along: Double,
        val segment: Int,
        val index: Int,
        val point: GeoPoint,
    )

    /** Longueur cumulée d'un tracé multi-segments. */
    fun length(track: List<List<GeoPoint>>) = track.sumOf { seg -> seg.zipWithNext().sumOf { (a, b) -> distance(a, b) } }

    /**
     * Projette la position sur le tracé. Si plusieurs portions sont proches (tracé en boucle,
     * aller-retour), on privilégie celle dont l'avancement est le plus proche de [previousAlong]
     * pour éviter les sauts.
     */
    fun project(track: List<List<GeoPoint>>, p: GeoPoint, previousAlong: Double?): Projection? {
        val candidates = mutableListOf<Projection>()
        var cumulative = 0.0
        track.forEachIndexed { si, seg ->
            for (i in 0 until seg.size - 1) {
                val a = seg[i]; val b = seg[i + 1]
                val segLen = distance(a, b)
                val (t, q) = projectOnSegment(a, b, p)
                candidates += Projection(distance(p, q), cumulative + t * segLen, si, i, q)
                cumulative += segLen
            }
        }
        if (candidates.isEmpty()) return null
        val best = candidates.minBy { it.distanceToTrack }
        if (previousAlong == null) return best
        val tolerance = best.distanceToTrack + 40.0
        return candidates.filter { it.distanceToTrack <= tolerance }
            .minBy { abs(it.along - previousAlong) }
    }

    /** Projection plane locale (équirectangulaire), suffisante à l'échelle d'un segment. */
    private fun projectOnSegment(a: GeoPoint, b: GeoPoint, p: GeoPoint): Pair<Double, GeoPoint> {
        val k = cos(Math.toRadians(a.latitude))
        val ax = a.longitude * k; val ay = a.latitude
        val bx = b.longitude * k; val by = b.latitude
        val px = p.longitude * k; val py = p.latitude
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return t to GeoPoint(a.latitude + t * (b.latitude - a.latitude), a.longitude + t * (b.longitude - a.longitude))
    }

    /** Découpe le tracé en partie parcourue / restante au point projeté. */
    fun split(track: List<List<GeoPoint>>, proj: Projection): Pair<List<List<GeoPoint>>, List<List<GeoPoint>>> {
        val done = mutableListOf<List<GeoPoint>>()
        val todo = mutableListOf<List<GeoPoint>>()
        track.forEachIndexed { si, seg ->
            when {
                si < proj.segment -> done += seg
                si > proj.segment -> todo += seg
                else -> {
                    done += seg.subList(0, proj.index + 1) + proj.point
                    todo += listOf(proj.point) + seg.subList(proj.index + 1, seg.size)
                }
            }
        }
        return done to todo
    }
}
