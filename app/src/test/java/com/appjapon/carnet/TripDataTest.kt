package com.appjapon.carnet

import androidx.test.core.app.ApplicationProvider
import com.appjapon.carnet.data.TrackStore
import com.appjapon.carnet.data.TripRepository
import com.appjapon.carnet.map.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripDataTest {

    @Test
    fun tripLoadsAndIsConsistent() {
        val trip = TripRepository.get(ApplicationProvider.getApplicationContext())
        assertEquals(9, trip.days.size)
        assertTrue(trip.pois.size > 60)
        trip.days.forEach { d ->
            assertTrue("jour ${d.num} sans étapes localisées", d.trackableSteps.size >= 3)
            d.steps.flatMap { it.poiIds }.forEach { assertTrue(it in trip.pois) }
        }
        // Tous les lieux sont au Japon
        trip.pois.values.forEach { assertTrue(it.name, it.lat in 30.0..40.0 && it.lon in 130.0..141.0) }
        // Mise à jour de la synthèse : départ 15h00 vers Nakatsugawa
        assertTrue(trip.day(4)!!.steps.any { it.time == "15h00" && "Nakatsugawa" in it.title })
    }

    @Test
    fun parsesGpxTrack() {
        val gpx = """<?xml version="1.0"?><gpx version="1.1"><trk><trkseg>
            <trkpt lat="34.9943" lon="135.7844"/><trkpt lat="34.9975" lon="135.7810"/><trkpt lat="35.0036" lon="135.7782"/>
            </trkseg></trk></gpx>"""
        val t = TrackStore.parse(gpx.toByteArray())
        assertEquals(1, t.size); assertEquals(3, t[0].size)
        assertEquals(34.9975, t[0][1].latitude, 1e-6)
    }

    @Test
    fun parsesKmlFromGoogleMyMaps() {
        val kml = """<?xml version="1.0" encoding="UTF-8"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>
            <Placemark><name>Kiyomizu</name><Point><coordinates>135.7844,34.9943,0</coordinates></Point></Placemark>
            <Placemark><name>Itinéraire</name><LineString><tessellate>1</tessellate><coordinates>
              135.7844,34.9943,0 135.7810,34.9975,0 135.7782,35.0036,0
            </coordinates></LineString></Placemark></Document></kml>"""
        val t = TrackStore.parse(kml.toByteArray())
        assertEquals(1, t.size); assertEquals(3, t[0].size)
        assertEquals(135.7810, t[0][1].longitude, 1e-6)
    }

    @Test
    fun projectsPositionOnTrack() {
        val track = listOf(listOf(GeoPoint(35.0, 135.0), GeoPoint(35.0, 135.01), GeoPoint(35.01, 135.01)))
        val p = Geo.project(track, GeoPoint(35.0002, 135.005), null)!!
        assertTrue(p.distanceToTrack in 15.0..30.0)
        assertTrue(p.along in 400.0..500.0)
        val (done, todo) = Geo.split(track, p)
        assertEquals(2, done[0].size); assertEquals(3, todo[0].size)
    }
}
