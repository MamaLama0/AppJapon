package com.appjapon.carnet.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.appjapon.carnet.data.Trip
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.cos

/**
 * Cartes hors-ligne (tuiles vectorielles OpenFreeMap, sans clé).
 *
 * Au téléchargement, la version des tuiles est « figée » dans un style local : la carte affichée
 * correspond exactement aux tuiles stockées, même si OpenFreeMap publie une nouvelle version pendant
 * le voyage. Si la version figée disparaît du serveur, on revient au style en ligne.
 */
class OfflineMaps(private val context: Context) {

    data class Area(val name: String, val bounds: LatLngBounds, val minZoom: Double, val maxZoom: Double)

    interface Listener {
        fun onStyleChanged()
        fun onProgress(areaIndex: Int, areaCount: Int, areaName: String, fraction: Double, bytes: Long)
        fun onFinished(error: String?)
    }

    companion object {
        private const val TILEJSON = "https://tiles.openfreemap.org/planet"
        private const val ASSET_STYLE = "asset://map_style.json"
        /** Distance (km) en dessous de laquelle deux lieux sont regroupés dans la même zone détaillée. */
        private const val CLUSTER_KM = 12.0
    }

    private val prefs = context.getSharedPreferences("offline_maps", Context.MODE_PRIVATE)
    private val pinned = File(context.filesDir, "map_style_pinned.json")
    private val main = Handler(Looper.getMainLooper())
    private val manager get() = OfflineManager.getInstance(context)

    @Volatile var running = false; private set
    private var cancelled = false

    fun styleUri(): String = if (pinned.exists()) "file://${pinned.absolutePath}" else ASSET_STYLE

    val isComplete get() = pinned.exists() && prefs.getBoolean("complete", false)
    val downloadedAt get() = prefs.getLong("date", 0L)
    val sizeBytes get() = prefs.getLong("bytes", 0L)

    // ------------------------------------------------------------------ zones

    fun areas(trip: Trip): List<Area> {
        val out = mutableListOf<Area>()
        val all = trip.pois.values.map { GeoPoint(it.lat, it.lon) }
        // 1. Vue d'ensemble de tout le voyage
        out += Area("Vue d'ensemble", bounds(all, 25.0), 4.0, 10.0)
        // 2. Chaque journée (routes et environs)
        trip.days.forEach { d ->
            val pts = d.steps.flatMap { it.poiIds }.mapNotNull { trip.pois[it] }.map { GeoPoint(it.lat, it.lon) }
            if (pts.isNotEmpty()) out += Area("Jour ${d.num} — routes", bounds(pts, 3.0), 10.0, 12.0)
        }
        // 3. Zones détaillées (rues, sentiers) autour des groupes de lieux
        clusters(all).forEachIndexed { i, c -> out += Area("Zone détaillée ${i + 1}", bounds(c, 2.5), 13.0, 14.0) }
        return out
    }

    private fun clusters(points: List<GeoPoint>): List<List<GeoPoint>> {
        val groups = mutableListOf<MutableList<GeoPoint>>()
        points.forEach { p ->
            val near = groups.filter { g -> g.any { Geo.distance(it, p) < CLUSTER_KM * 1000 } }
            when {
                near.isEmpty() -> groups += mutableListOf(p)
                else -> {
                    val merged = near.flatten().toMutableList().apply { add(p) }
                    groups.removeAll(near.toSet()); groups += merged
                }
            }
        }
        return groups
    }

    private fun bounds(points: List<GeoPoint>, marginKm: Double): LatLngBounds {
        val dLat = marginKm / 111.0
        val midLat = points.map { it.latitude }.average()
        val dLon = marginKm / (111.0 * cos(Math.toRadians(midLat)))
        return LatLngBounds.Builder()
            .include(LatLng(points.minOf { it.latitude } - dLat, points.minOf { it.longitude } - dLon))
            .include(LatLng(points.maxOf { it.latitude } + dLat, points.maxOf { it.longitude } + dLon))
            .build()
    }

    // ------------------------------------------------------------------ téléchargement

    fun cancel() { cancelled = true }

    fun download(trip: Trip, listener: Listener) {
        if (running) return
        running = true; cancelled = false
        prefs.edit().putBoolean("complete", false).apply()
        thread {
            try {
                writePinnedStyle(fetchTileJson())
            } catch (e: Exception) {
                main.post { running = false; listener.onFinished("Connexion impossible à OpenFreeMap (${e.message}). Réessayez en Wi-Fi.") }
                return@thread
            }
            main.post {
                listener.onStyleChanged()
                deleteRegions {
                    manager.setOfflineMapboxTileCountLimit(500_000)
                    downloadNext(areas(trip), 0, 0L, listener)
                }
            }
        }
    }

    private fun downloadNext(areas: List<Area>, index: Int, bytes: Long, listener: Listener) {
        if (cancelled) { running = false; listener.onFinished("Téléchargement interrompu."); return }
        if (index >= areas.size) {
            prefs.edit().putBoolean("complete", true).putLong("date", System.currentTimeMillis()).putLong("bytes", bytes).apply()
            running = false
            listener.onFinished(null)
            return
        }
        val area = areas[index]
        val def = OfflineTilePyramidRegionDefinition(
            styleUri(), area.bounds, area.minZoom, area.maxZoom,
            context.resources.displayMetrics.density,
            false, // idéogrammes rendus localement par le téléphone : inutile de les télécharger
        )
        manager.createOfflineRegion(def, area.name.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    private var finished = false
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (finished) return
                        val fraction = if (status.requiredResourceCount > 0)
                            status.completedResourceCount.toDouble() / status.requiredResourceCount else 0.0
                        listener.onProgress(index, areas.size, area.name, fraction, bytes + status.completedResourceSize)
                        if (cancelled) {
                            finished = true
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            downloadNext(areas, areas.size + 1, bytes, listener)
                        } else if (status.isComplete) {
                            finished = true
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            downloadNext(areas, index + 1, bytes + status.completedResourceSize, listener)
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        // MapLibre réessaie automatiquement les erreurs réseau ; on se contente de les signaler.
                        listener.onProgress(index, areas.size, "${area.name} (nouvel essai…)", 0.0, bytes)
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        finished = true
                        running = false
                        listener.onFinished("Limite de tuiles atteinte ($limit).")
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                running = false
                listener.onFinished("Erreur : $error")
            }
        })
    }

    fun deleteAll(onDone: () -> Unit) {
        deleteRegions {
            pinned.delete()
            prefs.edit().clear().apply()
            onDone()
        }
    }

    private fun deleteRegions(onDone: () -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().toMutableList()
                fun next() {
                    if (regions.isEmpty()) { onDone(); return }
                    regions.removeAt(0).delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = next()
                        override fun onError(error: String) = next()
                    })
                }
                next()
            }
            override fun onError(error: String) = onDone()
        })
    }

    // ------------------------------------------------------------------ version des tuiles

    private fun fetchTileJson(): JSONObject = JSONObject(httpGet(TILEJSON))

    private fun writePinnedStyle(tileJson: JSONObject) {
        val style = JSONObject(context.assets.open("map_style.json").bufferedReader().use { it.readText() })
        val src = style.getJSONObject("sources").getJSONObject("openmaptiles")
        src.remove("url")
        src.put("tiles", tileJson.getJSONArray("tiles"))
        src.put("minzoom", tileJson.optInt("minzoom", 0))
        src.put("maxzoom", tileJson.optInt("maxzoom", 14))
        tileJson.optString("attribution").takeIf { it.isNotBlank() }?.let { src.put("attribution", it) }
        pinned.writeText(style.toString())
        prefs.edit().putString("tiles", tileJson.getJSONArray("tiles").getString(0)).apply()
    }

    /**
     * À appeler au démarrage : si la version figée n'existe plus sur le serveur (et qu'on est en ligne),
     * on revient au style en ligne et on prévient l'utilisateur.
     */
    fun checkPinnedStillServed(onExpired: () -> Unit) {
        val template = prefs.getString("tiles", null) ?: return
        if (!pinned.exists()) return
        thread {
            val current = try { fetchTileJson().getJSONArray("tiles").getString(0) } catch (e: Exception) { return@thread } // hors-ligne
            if (current == template) return@thread
            val code = try { httpCode(template.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0")) } catch (e: Exception) { return@thread }
            if (code == 404 || code == 410) main.post {
                pinned.delete()
                prefs.edit().putBoolean("complete", false).apply()
                onExpired()
            }
        }
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "CarnetJapon/2.0 (Android)")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun httpCode(url: String): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "CarnetJapon/2.0 (Android)")
        return c.responseCode.also { c.disconnect() }
    }
}
