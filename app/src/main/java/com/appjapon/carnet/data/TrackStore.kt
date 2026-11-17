package com.appjapon.carnet.data

import android.content.Context
import android.net.Uri
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Tracés importés par l'utilisateur (GPX, KML ou KMZ — par ex. export Google My Maps),
 * un fichier par jour. Un tracé = une liste de segments (polylignes).
 */
class TrackStore(private val context: Context) {

    private val dir get() = File(context.filesDir, "tracks").apply { mkdirs() }
    private fun file(day: Int) = File(dir, "day_$day.txt")

    fun has(day: Int) = file(day).exists()

    fun load(day: Int): List<List<GeoPoint>>? {
        val f = file(day)
        if (!f.exists()) return null
        val segments = mutableListOf<MutableList<GeoPoint>>()
        var current = mutableListOf<GeoPoint>()
        f.forEachLine { line ->
            if (line.isBlank()) {
                if (current.isNotEmpty()) segments += current
                current = mutableListOf()
            } else {
                val (la, lo) = line.split(',')
                current += GeoPoint(la.toDouble(), lo.toDouble())
            }
        }
        if (current.isNotEmpty()) segments += current
        return segments.filter { it.size >= 2 }.ifEmpty { null }
    }

    fun delete(day: Int) = file(day).delete()

    /** Importe le fichier et renvoie le nombre de points, ou lève une exception explicite. */
    fun import(day: Int, uri: Uri): Int {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Fichier illisible")
        val xml = if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) unzipKml(bytes) else bytes
        val segments = parse(xml)
        val points = segments.sumOf { it.size }
        if (points < 2) error("Aucun tracé trouvé dans ce fichier (GPX, KML ou KMZ attendu)")
        file(day).bufferedWriter().use { w ->
            segments.forEach { seg ->
                seg.forEach { w.write("${it.latitude},${it.longitude}\n") }
                w.write("\n")
            }
        }
        return points
    }

    private fun unzipKml(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name.endsWith(".kml", ignoreCase = true)) return zip.readBytes()
            }
        }
        error("Archive KMZ sans fichier KML")
    }

    companion object {
        /** Lit GPX (trk/rte, sinon waypoints) et KML (LineString, gx:Track, sinon Points). */
        fun parse(xml: ByteArray): List<List<GeoPoint>> {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(ByteArrayInputStream(xml), null)

            val lines = mutableListOf<MutableList<GeoPoint>>()   // trk/rte/LineString
            val waypoints = mutableListOf<GeoPoint>()             // wpt / Point
            var current: MutableList<GeoPoint>? = null
            var inLineString = false
            var inPoint = false
            var inGxTrack = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.substringAfter(':')?.lowercase()
                when (event) {
                    XmlPullParser.START_TAG -> when (name) {
                        "trkseg", "rte" -> current = mutableListOf<GeoPoint>().also { lines += it }
                        "trkpt", "rtept" -> {
                            val p = latLon(parser)
                            if (p != null) (current ?: mutableListOf<GeoPoint>().also { current = it; lines += it }) += p
                        }
                        "wpt" -> latLon(parser)?.let { waypoints += it }
                        "linestring" -> inLineString = true
                        "point" -> inPoint = true
                        "track" -> if (parser.name.startsWith("gx:")) {
                            inGxTrack = true; current = mutableListOf<GeoPoint>().also { lines += it }
                        }
                        "coordinates" -> {
                            val pts = parseKmlCoordinates(parser.nextText())
                            when {
                                inLineString -> lines += pts.toMutableList()
                                inPoint -> waypoints += pts
                            }
                        }
                        "coord" -> if (inGxTrack) {
                            val parts = parser.nextText().trim().split(Regex("\\s+"))
                            if (parts.size >= 2) current?.add(GeoPoint(parts[1].toDouble(), parts[0].toDouble()))
                        }
                    }
                    XmlPullParser.END_TAG -> when (name) {
                        "trkseg", "rte" -> current = null
                        "linestring" -> inLineString = false
                        "point" -> inPoint = false
                        "track" -> if (inGxTrack) { inGxTrack = false; current = null }
                    }
                }
                event = parser.next()
            }
            val result = lines.filter { it.size >= 2 }
            return result.ifEmpty { if (waypoints.size >= 2) listOf(waypoints) else emptyList() }
        }

        private fun latLon(p: XmlPullParser): GeoPoint? {
            val la = p.getAttributeValue(null, "lat")?.toDoubleOrNull()
            val lo = p.getAttributeValue(null, "lon")?.toDoubleOrNull()
            return if (la != null && lo != null) GeoPoint(la, lo) else null
        }

        private fun parseKmlCoordinates(text: String): List<GeoPoint> =
            text.trim().split(Regex("\\s+")).mapNotNull { tuple ->
                val c = tuple.split(',')
                if (c.size >= 2) {
                    val lon = c[0].toDoubleOrNull(); val lat = c[1].toDoubleOrNull()
                    if (lat != null && lon != null) GeoPoint(lat, lon) else null
                } else null
            }
    }
}
