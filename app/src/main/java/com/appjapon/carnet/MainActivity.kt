package com.appjapon.carnet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.appjapon.carnet.data.Day
import com.appjapon.carnet.data.Poi
import com.appjapon.carnet.data.ProgressStore
import com.appjapon.carnet.data.TrackStore
import com.appjapon.carnet.data.Trip
import com.appjapon.carnet.data.TripRepository
import com.appjapon.carnet.databinding.ActivityMainBinding
import com.appjapon.carnet.databinding.SheetNearbyBinding
import com.appjapon.carnet.map.Geo
import com.appjapon.carnet.map.GeoPoint
import com.appjapon.carnet.map.MarkerFactory
import com.appjapon.carnet.map.OfflineMaps
import com.appjapon.carnet.ui.DayAdapter
import com.appjapon.carnet.ui.Format
import com.appjapon.carnet.ui.NearbyAdapter
import com.appjapon.carnet.ui.NearbyItem
import com.appjapon.carnet.ui.PoiActivity
import com.appjapon.carnet.ui.StepAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private companion object {
        const val OVERVIEW = 0

        // Niveaux de zoom MapLibre auxquels les éléments apparaissent
        const val Z_GROUPS = 7.0      // < : Kyoto / Alpes / Tokyo
        const val Z_REGIONS = 9.5     // < : régions ; ≥ : lieux principaux
        const val Z_IMP2 = 12.0       // lieux secondaires
        const val Z_IMP3 = 13.5       // petits lieux
        const val Z_LABELS = 13.0     // noms affichés sous les marqueurs
        const val JAPAN_ZOOM = 5.3
        val JAPAN = LatLng(35.9, 137.6)

        const val SRC_ROUTE_ALL = "src-route-all"
        const val SRC_ROUTE_DONE = "src-route-done"
        const val SRC_ROUTE_TODO = "src-route-todo"
        const val SRC_POIS = "src-pois"
        const val SRC_STEPS = "src-steps"
        const val SRC_REGIONS = "src-regions"
        val CLICKABLE = arrayOf("ly-steps", "ly-poi-1", "ly-poi-2", "ly-poi-3", "ly-groups", "ly-regions")
        const val FONT = "Noto Sans Regular"
        const val FONT_BOLD = "Noto Sans Bold"
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var trip: Trip
    private lateinit var progress: ProgressStore
    private lateinit var tracks: TrackStore
    private lateinit var icons: MarkerFactory
    private lateinit var offline: OfflineMaps
    private lateinit var sheet: BottomSheetBehavior<View>

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var pendingRecenter = true

    private var selectedDay = OVERVIEW
    private var lastLocation: Location? = null
    private var locationUpdatesOn = false
    private val locationListener = LocationListener { onLocation(it) }

    private val dayPoiIds = HashSet<String>()
    private var activeTrack: List<List<GeoPoint>>? = null
    private var activeTrackImported = false
    private var activeTrackLength = 0.0
    private var lastProjection: Geo.Projection? = null
    private val insidePois = HashSet<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val hideProximity = Runnable { b.proximityCard.visibility = View.GONE }

    private val stepAdapter = StepAdapter(
        onOpen = { step -> step.poiIds.firstOrNull()?.let { openPoi(it) } },
        onToggle = { step, checked -> progress.setDone(selectedDay, step.index, checked); refreshDay(recenter = false) },
    )
    private val dayAdapter = DayAdapter { selectDay(it.num) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) { startLocationUpdates(); activateLocationComponent() }
        else Toast.makeText(this, R.string.location_needed, Toast.LENGTH_LONG).show()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || selectedDay == OVERVIEW) return@registerForActivityResult
        try {
            val n = tracks.import(selectedDay, uri)
            Toast.makeText(this, "Tracé importé pour le jour $selectedDay ($n points)", Toast.LENGTH_LONG).show()
            lastProjection = null
            refreshDay(recenter = true)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Import impossible").setMessage(e.message ?: e.toString())
                .setPositiveButton("OK", null).show()
        }
    }

    private val poiLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val id = res.data?.getStringExtra(PoiActivity.RESULT_SHOW_POI) ?: return@registerForActivityResult
        trip.pois[id]?.let { p ->
            stopFollowing()
            sheet.state = BottomSheetBehavior.STATE_COLLAPSED
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 16.0), 700)
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        trip = TripRepository.get(this)
        progress = ProgressStore(this)
        tracks = TrackStore(this)
        icons = MarkerFactory(this)
        offline = OfflineMaps(this)

        setupInsets()
        setupSheet()
        setupChips()
        setupButtons()

        b.map.onCreate(savedInstanceState)
        b.map.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            m.uiSettings.isTiltGesturesEnabled = false
            m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder().target(JAPAN).zoom(JAPAN_ZOOM).build()
            m.addOnMapClickListener { onMapClick(it) }
            positionMapOrnaments()
            loadStyle()
        }

        val today = trip.todayDay()
        selectDay(today?.num ?: progress.lastDay)
        applyKeepScreen()
        ensureLocationPermission()
        offline.checkPinnedStillServed {
            Toast.makeText(this, "Les cartes hors-ligne ont expiré : retéléchargez-les en Wi-Fi (menu ⋮).", Toast.LENGTH_LONG).show()
            loadStyle()
        }
    }

    override fun onStart() { super.onStart(); b.map.onStart() }
    override fun onStop() { super.onStop(); b.map.onStop() }
    override fun onLowMemory() { super.onLowMemory(); b.map.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); b.map.onSaveInstanceState(outState) }
    override fun onDestroy() { super.onDestroy(); b.map.onDestroy() }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        startLocationUpdates()
        refreshDay(recenter = false) // la fiche lieu peut avoir modifié la progression
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
        stopLocationUpdates()
    }

    // ------------------------------------------------------------------ mise en place

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.topBar.updatePadding(top = bars.top)
            b.list.updatePadding(bottom = bars.bottom + dp(24))
            b.topBar.post {
                sheet.expandedOffset = b.topBar.height
                positionMapOrnaments()
            }
            insets
        }
    }

    /** Boussole, logo et attribution sous la barre des jours (le bas est couvert par le panneau). */
    private fun positionMapOrnaments() {
        val m = map ?: return
        val top = b.topBar.height + dp(8)
        with(m.uiSettings) {
            compassGravity = Gravity.TOP or Gravity.END
            setCompassMargins(0, top + dp(8), dp(16), 0)
            logoGravity = Gravity.TOP or Gravity.START
            setLogoMargins(dp(12), top, 0, 0)
            attributionGravity = Gravity.TOP or Gravity.START
            setAttributionMargins(dp(100), top - dp(4), 0, 0)
        }
    }

    private fun setupSheet() {
        sheet = BottomSheetBehavior.from(b.sheet)
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        b.list.layoutManager = LinearLayoutManager(this)
    }

    private fun setupChips() {
        b.dayChips.removeAllViews()
        b.dayChips.addView(chip(OVERVIEW, "Carte", ContextCompat.getColor(this, R.color.sumi)))
        trip.days.forEach { d -> b.dayChips.addView(chip(d.num, "J${d.num} · ${Format.shortDate(d.date)}", d.color)) }
    }

    private fun chip(id: Int, label: String, color: Int) = Chip(this).apply {
        this.id = View.generateViewId()
        tag = id
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        chipStrokeWidth = 0f
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chipBackgroundColor = ColorStateList(states, intArrayOf(color, Color.TRANSPARENT))
        setTextColor(ColorStateList(states, intArrayOf(Color.WHITE, ContextCompat.getColor(context, R.color.sumi))))
        setOnClickListener { selectDay(id) }
    }

    private fun setupButtons() {
        b.fabLocate.setOnClickListener {
            if (!hasLocationPermission()) { ensureLocationPermission(); return@setOnClickListener }
            val loc = lastLocation
            if (loc != null) {
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
                val z = maxOf(map?.cameraPosition?.zoom ?: 0.0, 15.0)
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), z), 700)
            } else Toast.makeText(this, "Recherche du signal GPS…", Toast.LENGTH_SHORT).show()
        }
        b.fabFollow.setOnClickListener {
            val lc = map?.locationComponent
            if (lc == null || !lc.isLocationComponentActivated) { ensureLocationPermission(); return@setOnClickListener }
            if (lc.cameraMode != CameraMode.NONE) stopFollowing()
            else {
                lc.cameraMode = CameraMode.TRACKING_COMPASS
                if ((map?.cameraPosition?.zoom ?: 0.0) < 15) lc.zoomWhileTracking(16.0)
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
                Toast.makeText(this, "La carte suit votre position et votre orientation", Toast.LENGTH_SHORT).show()
            }
            updateFollowIcon()
        }
        b.fabNearby.setOnClickListener { showNearby() }
        b.btnMore.setOnClickListener { showMenu(it) }
        b.proximityCard.setOnClickListener { v -> (v.tag as? String)?.let { openPoi(it) } }
    }

    // ------------------------------------------------------------------ style & couches

    private fun loadStyle() {
        val m = map ?: return
        style = null
        m.setStyle(Style.Builder().fromUri(offline.styleUri())) { st ->
            style = st
            setupLayers(st)
            activateLocationComponent()
            refreshDay(recenter = pendingRecenter)
        }
    }

    private fun setupLayers(st: Style) {
        com.appjapon.carnet.data.Category.entries.forEach { c ->
            st.addImage(icons.categoryKey(c, false), icons.categoryBitmap(c, false))
            st.addImage(icons.categoryKey(c, true), icons.categoryBitmap(c, true))
        }
        listOf(SRC_ROUTE_ALL, SRC_ROUTE_DONE, SRC_ROUTE_TODO, SRC_POIS, SRC_STEPS, SRC_REGIONS)
            .forEach { st.addSource(GeoJsonSource(it)) }

        val round = arrayOf(lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))
        st.addLayer(LineLayer("ly-route-all", SRC_ROUTE_ALL).withProperties(
            *round, lineColor(Expression.toColor(get("color"))), lineWidth(3.5f), lineOpacity(0.75f)))
        st.addLayer(LineLayer("ly-route-done", SRC_ROUTE_DONE).withProperties(
            *round, lineColor("#9AA0A6"), lineWidth(6f), lineOpacity(0.9f)))
        st.addLayer(LineLayer("ly-route-todo", SRC_ROUTE_TODO).withFilter(eq(get("dashed"), false)).withProperties(
            *round, lineColor(Expression.toColor(get("color"))), lineWidth(6f), lineOpacity(0.9f)))
        st.addLayer(LineLayer("ly-route-todo-dashed", SRC_ROUTE_TODO).withFilter(eq(get("dashed"), true)).withProperties(
            lineCap(Property.LINE_CAP_BUTT), lineJoin(Property.LINE_JOIN_ROUND),
            lineColor(Expression.toColor(get("color"))), lineWidth(5f), lineOpacity(0.9f), lineDasharray(arrayOf(1.6f, 1.2f))))

        fun poiLayer(id: String, imp: Int, minZoom: Double) = SymbolLayer(id, SRC_POIS)
            .withFilter(all(eq(get("imp"), imp), eq(get("inDay"), false)))
            .withProperties(*markerProps(bold = false)).apply { this.minZoom = minZoom.toFloat() }
        st.addLayer(poiLayer("ly-poi-3", 3, Z_IMP3))
        st.addLayer(poiLayer("ly-poi-2", 2, Z_IMP2))
        st.addLayer(poiLayer("ly-poi-1", 1, Z_REGIONS))
        st.addLayer(SymbolLayer("ly-steps", SRC_STEPS).withProperties(*markerProps(bold = true), symbolSortKey(get("sort"))))

        st.addLayer(SymbolLayer("ly-regions", SRC_REGIONS).withFilter(eq(get("kind"), "region"))
            .withProperties(iconImage(get("icon")), iconAllowOverlap(true))
            .apply { minZoom = Z_GROUPS.toFloat(); maxZoom = Z_REGIONS.toFloat() })
        st.addLayer(SymbolLayer("ly-groups", SRC_REGIONS).withFilter(eq(get("kind"), "group"))
            .withProperties(iconImage(get("icon")), iconAllowOverlap(true))
            .apply { maxZoom = Z_GROUPS.toFloat() })

        createRegionFeatures(st)
    }

    private fun markerProps(bold: Boolean) = arrayOf(
        iconImage(get("icon")),
        iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        iconOffset(arrayOf(0f, 3f)),
        iconAllowOverlap(true),
        iconIgnorePlacement(false),
        textField(Expression.step(zoom(), literal(""), stop(Z_LABELS, get("name")))),
        textFont(arrayOf(if (bold) FONT_BOLD else FONT)),
        textSize(11.5f),
        textMaxWidth(9f),
        textAnchor(Property.TEXT_ANCHOR_TOP),
        textOffset(arrayOf(0f, 0.3f)),
        textOptional(true),
        textColor("#1E2328"),
        textHaloColor("#FFFFFF"),
        textHaloWidth(1.6f),
    )

    private fun groupOf(region: String) = when (region) {
        "Kyoto" -> "Kyoto"
        "Tokyo" -> "Tokyo"
        else -> "Alpes japonaises"
    }

    private fun createRegionFeatures(st: Style) {
        val features = mutableListOf<Feature>()
        fun add(kind: String, name: String, pois: List<Poi>) {
            val color = when (name) {
                "Kyoto" -> ContextCompat.getColor(this, R.color.shu)
                "Tokyo" -> Color.parseColor("#37474F")
                else -> ContextCompat.getColor(this, R.color.matsu)
            }
            st.addImage(icons.regionKey(name), icons.regionBitmap(name, pois.size, color))
            features += Feature.fromGeometry(Point.fromLngLat(pois.map { it.lon }.average(), pois.map { it.lat }.average())).apply {
                addStringProperty("kind", kind)
                addStringProperty("icon", icons.regionKey(name))
                addNumberProperty("minLat", pois.minOf { it.lat }); addNumberProperty("maxLat", pois.maxOf { it.lat })
                addNumberProperty("minLon", pois.minOf { it.lon }); addNumberProperty("maxLon", pois.maxOf { it.lon })
            }
        }
        trip.pois.values.groupBy { it.region }.forEach { (r, pois) -> add("region", r, pois) }
        trip.pois.values.groupBy { groupOf(it.region) }.forEach { (g, pois) -> add("group", g, pois) }
        st.getSourceAs<GeoJsonSource>(SRC_REGIONS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun onMapClick(point: LatLng): Boolean {
        val m = map ?: return false
        val px = m.projection.toScreenLocation(point)
        val box = RectF(px.x - dp(18), px.y - dp(18), px.x + dp(18), px.y + dp(18))
        val f = m.queryRenderedFeatures(box, *CLICKABLE).firstOrNull() ?: return false
        if (f.hasProperty("poi")) { openPoi(f.getStringProperty("poi")); return true }
        if (f.hasProperty("minLat")) {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(f.getNumberProperty("minLat").toDouble(), f.getNumberProperty("minLon").toDouble()))
                .include(LatLng(f.getNumberProperty("maxLat").toDouble(), f.getNumberProperty("maxLon").toDouble()))
                .build()
            fitBounds(bounds, minZoom = m.cameraPosition.zoom + 2)
            return true
        }
        return false
    }

    private fun fitBounds(bounds: LatLngBounds, minZoom: Double? = null) {
        val m = map ?: return
        if (bounds.latitudeSpan < 0.002 && bounds.longitudeSpan < 0.002) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, 15.0), 700)
            return
        }
        val top = b.topBar.height + dp(36)
        val bottom = dp(168) + dp(36)
        val update = CameraUpdateFactory.newLatLngBounds(bounds, dp(36), top, dp(56), bottom)
        val target = update.getCameraPosition(m)
        if (minZoom != null && target != null && target.zoom < minZoom) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, minZoom), 800)
        } else m.animateCamera(update, 800)
    }

    // ------------------------------------------------------------------ jour sélectionné

    private fun selectDay(num: Int) {
        selectedDay = if (trip.day(num) != null) num else OVERVIEW
        progress.lastDay = selectedDay
        for (i in 0 until b.dayChips.childCount) {
            val c = b.dayChips.getChildAt(i) as Chip
            c.isChecked = c.tag == selectedDay
            if (c.isChecked) c.post { (b.dayChips.parent as android.widget.HorizontalScrollView).smoothScrollTo(c.left - dp(40), 0) }
        }
        lastProjection = null
        insidePois.clear()
        refreshDay(recenter = true)
    }

    /** Recalcule tout l'affichage du jour (tracé, marqueurs numérotés, liste, statut). */
    private fun refreshDay(recenter: Boolean) {
        val day = trip.day(selectedDay)
        if (day != null) {
            val (track, imported) = trackFor(day)
            activeTrack = track; activeTrackImported = imported
            activeTrackLength = Geo.length(track)
        } else activeTrack = null

        val st = style
        if (st == null) {
            pendingRecenter = pendingRecenter || recenter
        } else {
            drawMapData(st, day)
            if (recenter || pendingRecenter) recenter(day)
            pendingRecenter = false
        }
        lastLocation?.let { updateRouteProgress(GeoPoint(it.latitude, it.longitude)) }
        updateSheet()
    }

    private fun recenter(day: Day?) {
        val m = map ?: return
        if (day == null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(JAPAN, JAPAN_ZOOM), 800)
            return
        }
        val pts = dayPoints(day) + activeTrack.orEmpty().flatten()
        if (pts.isEmpty()) return
        val bounds = LatLngBounds.Builder().apply {
            pts.forEach { include(LatLng(it.latitude, it.longitude)) }
            if (pts.size == 1) include(LatLng(pts[0].latitude + 0.001, pts[0].longitude + 0.001))
        }.build()
        b.map.post { fitBounds(bounds) }
    }

    private fun dayPoints(day: Day): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        day.trackableSteps.forEach { s ->
            s.poiIds.mapNotNull { trip.pois[it] }.forEach { p ->
                val g = GeoPoint(p.lat, p.lon)
                if (out.isEmpty() || Geo.distance(out.last(), g) > 1) out += g
            }
        }
        return out
    }

    private fun trackFor(day: Day): Pair<List<List<GeoPoint>>, Boolean> {
        tracks.load(day.num)?.let { return it to true }
        return listOf(dayPoints(day)) to false
    }

    private fun currentStepIndex(day: Day, done: Set<Int>): Int? {
        val trackable = day.trackableSteps
        val lastDone = trackable.lastOrNull { it.index in done }?.index ?: -1
        return (trackable.firstOrNull { it.index > lastDone && it.index !in done }
            ?: trackable.firstOrNull { it.index !in done })?.index
    }

    private fun lineFeature(points: List<GeoPoint>, color: Int, dashed: Boolean = false): Feature =
        Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
            addStringProperty("color", String.format("#%06X", 0xFFFFFF and color))
            addBooleanProperty("dashed", dashed)
        }

    private fun drawMapData(st: Style, day: Day?) {
        dayPoiIds.clear()
        val allRoutes = mutableListOf<Feature>()
        val todo = mutableListOf<Feature>()
        val steps = mutableListOf<Feature>()

        if (day == null) {
            trip.days.forEach { d ->
                trackFor(d).first.filter { it.size >= 2 }.forEach { allRoutes += lineFeature(it, d.color) }
            }
        } else {
            activeTrack.orEmpty().filter { it.size >= 2 }.forEach { todo += lineFeature(it, day.color, dashed = !activeTrackImported) }
            val done = progress.done(day.num)
            val current = currentStepIndex(day, done)
            day.trackableSteps.forEachIndexed { i, s ->
                s.poiIds.forEach { pid ->
                    val p = trip.pois[pid] ?: return@forEach
                    if (!dayPoiIds.add(pid)) return@forEach
                    val isDone = s.index in done
                    val isCurrent = s.index == current
                    val key = icons.stepKey(i + 1, day.color, isDone, s.optional, isCurrent)
                    if (st.getImage(key) == null) st.addImage(key, icons.stepBitmap(i + 1, day.color, isDone, s.optional, isCurrent))
                    steps += Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                        addStringProperty("poi", p.id)
                        addStringProperty("icon", key)
                        addStringProperty("name", p.name)
                        addNumberProperty("sort", if (isCurrent) 1000 else if (isDone) 0 else 500 - i)
                    }
                }
            }
        }
        val dimmed = day != null
        val pois = trip.pois.values.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addStringProperty("poi", p.id)
                addNumberProperty("imp", p.importance)
                addBooleanProperty("inDay", p.id in dayPoiIds)
                addStringProperty("icon", icons.categoryKey(p.category, dimmed))
                addStringProperty("name", p.name)
            }
        }
        st.getSourceAs<GeoJsonSource>(SRC_ROUTE_ALL)?.setGeoJson(FeatureCollection.fromFeatures(allRoutes))
        st.getSourceAs<GeoJsonSource>(SRC_ROUTE_TODO)?.setGeoJson(FeatureCollection.fromFeatures(todo))
        st.getSourceAs<GeoJsonSource>(SRC_ROUTE_DONE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        st.getSourceAs<GeoJsonSource>(SRC_STEPS)?.setGeoJson(FeatureCollection.fromFeatures(steps))
        st.getSourceAs<GeoJsonSource>(SRC_POIS)?.setGeoJson(FeatureCollection.fromFeatures(pois))
    }

    // ------------------------------------------------------------------ position & progression

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) { startLocationUpdates(); activateLocationComponent() }
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationUpdatesOn || !hasLocationPermission()) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        var any = false
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.allProviders.contains(p)) {
                lm.requestLocationUpdates(p, 2_000L, 3f, locationListener, Looper.getMainLooper())
                any = true
            }
        }
        locationUpdatesOn = any
        if (lastLocation == null) {
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                ?.let { onLocation(it) }
        }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesOn) return
        getSystemService(LocationManager::class.java)?.removeUpdates(locationListener)
        locationUpdatesOn = false
    }

    @SuppressLint("MissingPermission")
    private fun activateLocationComponent() {
        val m = map ?: return
        val st = style ?: return
        if (!hasLocationPermission()) return
        val lc = m.locationComponent
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, st).useDefaultLocationEngine(false).build()
            )
            lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() = updateFollowIcon()
                override fun onCameraTrackingChanged(currentMode: Int) = updateFollowIcon()
            })
        }
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.COMPASS
        lastLocation?.let { lc.forceLocationUpdate(it) }
    }

    private fun onLocation(loc: Location) {
        val prev = lastLocation
        // Une position réseau (imprécise) ne remplace pas une position GPS récente.
        if (prev != null && loc.provider == LocationManager.NETWORK_PROVIDER &&
            prev.provider == LocationManager.GPS_PROVIDER && loc.time - prev.time < 20_000) return
        lastLocation = loc
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(loc)
        val gp = GeoPoint(loc.latitude, loc.longitude)
        checkProximity(gp, loc.accuracy)
        updateRouteProgress(gp)
        if (selectedDay == OVERVIEW) updateOverviewSheet() else updateStatus()
        if (prev == null) updateFollowIcon()
    }

    private fun updateRouteProgress(gp: GeoPoint) {
        val track = activeTrack ?: return
        val day = trip.day(selectedDay) ?: return
        if (track.all { it.size < 2 }) return
        val proj = Geo.project(track, gp, lastProjection?.along) ?: return
        lastProjection = proj
        val st = style ?: return
        val (done, todo) = Geo.split(track, proj)
        st.getSourceAs<GeoJsonSource>(SRC_ROUTE_DONE)?.setGeoJson(FeatureCollection.fromFeatures(
            done.filter { it.size >= 2 }.map { lineFeature(it, Color.GRAY) }))
        st.getSourceAs<GeoJsonSource>(SRC_ROUTE_TODO)?.setGeoJson(FeatureCollection.fromFeatures(
            todo.filter { it.size >= 2 }.map { lineFeature(it, day.color, dashed = !activeTrackImported) }))
    }

    private fun checkProximity(gp: GeoPoint, accuracy: Float) {
        val acc = accuracy.coerceAtMost(60f)
        trip.pois.values.forEach { p ->
            val d = Geo.distance(gp.latitude, gp.longitude, p.lat, p.lon)
            if (p.id !in insidePois && d <= p.radius + acc) {
                insidePois += p.id
                onEnterPoi(p)
            } else if (p.id in insidePois && d > p.radius * 1.5 + acc + 30) {
                insidePois -= p.id
            }
        }
    }

    private fun onEnterPoi(p: Poi) {
        val day = trip.day(selectedDay)
        var checked = false
        if (day != null && progress.autoCheck) {
            val done = progress.done(day.num)
            day.steps.firstOrNull { p.id in it.poiIds && it.index !in done }?.let {
                progress.setDone(day.num, it.index, true)
                checked = true
            }
        }
        b.proximityCard.tag = p.id
        b.proximityText.text = buildString {
            append("📍 Vous êtes à ${p.name}")
            if (checked) append(" — étape cochée ✓")
            append("\nTouchez pour lire son histoire")
        }
        b.proximityCard.visibility = View.VISIBLE
        handler.removeCallbacks(hideProximity)
        handler.postDelayed(hideProximity, 15_000)
        vibrate()
        if (checked) refreshDay(recenter = false)
    }

    // ------------------------------------------------------------------ panneau du bas

    private fun updateSheet() {
        val day = trip.day(selectedDay)
        if (day == null) { updateOverviewSheet(); return }
        if (b.list.adapter !== stepAdapter) b.list.adapter = stepAdapter

        val done = progress.done(day.num)
        val trackable = day.trackableSteps
        val doneCount = trackable.count { it.index in done }
        stepAdapter.submit(day, done, currentStepIndex(day, done))

        b.sheetKicker.text = "Jour ${day.num} / ${trip.days.size} · ${Format.longDate(day.date)}"
        b.sheetKicker.setTextColor(day.color)
        b.sheetTitle.text = day.title
        b.progressRow.visibility = View.VISIBLE
        b.progressBar.setIndicatorColor(day.color)
        b.progressBar.max = trackable.size
        b.progressBar.setProgressCompat(doneCount, true)
        b.progressText.text = "$doneCount / ${trackable.size}"
        updateStatus()
    }

    /** Texte de statut : prochaine étape, distance, horaire, position sur le tracé. */
    private fun updateStatus() {
        val day = trip.day(selectedDay) ?: return
        val current = currentStepIndex(day, progress.done(day.num))
        val lines = mutableListOf<String>()
        val loc = lastLocation
        val step = current?.let { day.steps[it] }
        if (step == null) {
            lines += "Journée terminée ! Toutes les étapes sont cochées. 🎉"
        } else {
            lines += "Prochaine étape : ${step.title}"
            val p = trip.pois[step.poiIds.first()]
            if (loc != null && p != null) {
                val d = Geo.distance(loc.latitude, loc.longitude, p.lat, p.lon)
                val dir = Geo.compass(Geo.bearing(loc.latitude, loc.longitude, p.lat, p.lon))
                lines += if (d < 15_000) "➜ ${Geo.formatDistance(d)} ($dir) · ≈ ${Format.minutes(Geo.walkMinutes(d).toLong())} à pied"
                         else "➜ ${Geo.formatDistance(d)} ($dir)"
            }
            scheduleLine(day, step.time)?.let { lines += it }
        }
        val proj = lastProjection
        if (loc != null && proj != null && activeTrackLength > 0) {
            val off = proj.distanceToTrack
            val status = when {
                off > 2_000 -> "loin du tracé (${Geo.formatDistance(off)})"
                off > 120 -> "hors tracé (${Geo.formatDistance(off)})"
                else -> "sur le tracé"
            }
            lines += "Tracé : ${Geo.formatDistance(proj.along)} sur ${Geo.formatDistance(activeTrackLength)} · $status"
        } else if (loc == null) {
            lines += "Activez la localisation pour vous situer sur le tracé."
        }
        if (!activeTrackImported) lines += "Tracé indicatif en pointillés — importez le vôtre via ⋮"
        if (!offline.isComplete) lines += "Cartes hors-ligne non téléchargées (menu ⋮)"
        b.statusText.text = lines.joinToString("\n")
    }

    private fun scheduleLine(day: Day, time: String): String? {
        if (day.date != LocalDate.now()) return null
        val t = Format.parseTime(time) ?: return null
        val diff = ChronoUnit.MINUTES.between(t, LocalTime.now())
        val prefix = if (day.approxTimes) "Vers $time" else "Prévu à $time"
        return when {
            diff < -5 -> "$prefix · dans ${Format.minutes(-diff)}"
            diff <= 10 -> "$prefix · vous êtes dans les temps"
            else -> "$prefix · ${Format.minutes(diff)} de retard"
        }
    }

    private fun updateOverviewSheet() {
        if (b.list.adapter !== dayAdapter) b.list.adapter = dayAdapter
        val prog = trip.days.associate { d ->
            val done = progress.done(d.num)
            d.num to (d.trackableSteps.count { it.index in done } to d.trackableSteps.size)
        }
        dayAdapter.submit(trip.days, prog)
        val total = prog.values.sumOf { it.second }
        val done = prog.values.sumOf { it.first }
        b.sheetKicker.text = "Carnet de route · ${Format.shortDate(trip.start)} → ${Format.shortDate(trip.days.last().date)}"
        b.sheetKicker.setTextColor(ContextCompat.getColor(this, R.color.shu))
        b.sheetTitle.text = "Kyoto historique & Alpes japonaises"
        b.progressRow.visibility = View.VISIBLE
        b.progressBar.setIndicatorColor(ContextCompat.getColor(this, R.color.shu))
        b.progressBar.max = total
        b.progressBar.setProgressCompat(done, true)
        b.progressText.text = "$done / $total"
        val daysTo = ChronoUnit.DAYS.between(LocalDate.now(), trip.start)
        val main = when {
            daysTo > 1 -> "Départ dans $daysTo jours. Zoomez sur la carte pour faire apparaître les lieux, touchez-en un pour lire son histoire."
            daysTo == 1L -> "Départ demain ! Touchez un jour pour suivre son itinéraire."
            trip.todayDay() != null -> "Aujourd'hui : jour ${trip.todayDay()!!.num}. Touchez-le pour suivre votre itinéraire."
            else -> "Touchez un jour pour afficher son itinéraire et suivre votre progression."
        }
        val off = if (offline.isComplete) "Cartes hors-ligne prêtes ✓"
                  else "Pensez à télécharger les cartes hors-ligne en Wi-Fi avant le départ (menu ⋮)."
        b.statusText.text = "$main\n$off"
    }

    // ------------------------------------------------------------------ autour de moi

    private fun showNearby() {
        val loc = lastLocation
        val center = loc?.let { GeoPoint(it.latitude, it.longitude) }
            ?: map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }
            ?: GeoPoint(JAPAN.latitude, JAPAN.longitude)
        val all = trip.pois.values.map { p ->
            NearbyItem(
                p, Geo.distance(center.latitude, center.longitude, p.lat, p.lon),
                Geo.bearing(center.latitude, center.longitude, p.lat, p.lon),
                trip.stepsFor(p.id).map { it.first.num }.distinct(),
            )
        }.sortedBy { it.distance }
        val within = all.filter { it.distance <= 3_000 }
        val items = if (within.size >= 3) within.take(30) else all.take(10)

        val dialog = BottomSheetDialog(this)
        val v = SheetNearbyBinding.inflate(layoutInflater)
        v.nearbyTitle.text = when {
            loc == null -> "Autour du centre de la carte"
            within.size >= 3 -> "${within.size} lieux à moins de 3 km"
            else -> "Les lieux les plus proches"
        }
        v.nearbyList.layoutManager = LinearLayoutManager(this)
        v.nearbyList.adapter = NearbyAdapter(items, icons) { p -> dialog.dismiss(); openPoi(p.id) }
        dialog.setContentView(v.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ menu

    private fun showMenu(anchor: View) {
        val day = trip.day(selectedDay)
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 7, 0, "Cartes hors-ligne…")
        if (day != null) {
            menu.menu.add(0, 1, 1, R.string.import_track)
            if (tracks.has(day.num)) menu.menu.add(0, 2, 2, R.string.remove_track)
            menu.menu.add(0, 3, 3, R.string.reset_day)
            menu.menu.add(0, 6, 4, "Notes & conseils du jour")
        }
        menu.menu.add(1, 4, 5, R.string.auto_check).apply { isCheckable = true; isChecked = progress.autoCheck }
        menu.menu.add(1, 5, 6, R.string.keep_screen).apply { isCheckable = true; isChecked = progress.keepScreenOn }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> importLauncher.launch(arrayOf("*/*"))
                2 -> { tracks.delete(selectedDay); lastProjection = null; refreshDay(recenter = false) }
                3 -> AlertDialog.Builder(this).setMessage("Décocher toutes les étapes du jour $selectedDay ?")
                    .setPositiveButton("Réinitialiser") { _, _ -> progress.reset(selectedDay); refreshDay(recenter = false) }
                    .setNegativeButton("Annuler", null).show()
                4 -> progress.autoCheck = !progress.autoCheck
                5 -> { progress.keepScreenOn = !progress.keepScreenOn; applyKeepScreen() }
                6 -> day?.let { showNotes(it) }
                7 -> showOfflineDialog()
            }
            true
        }
        menu.show()
    }

    private fun showOfflineDialog() {
        if (offline.running) { showDownloadProgress(); return }
        val status = if (offline.isComplete) {
            val date = DateFormat.getMediumDateFormat(this).format(java.util.Date(offline.downloadedAt))
            "Cartes téléchargées le $date (≈ ${offline.sizeBytes / 1_048_576} Mo).\n\nElles couvrent tous les lieux du carnet : rues et sentiers autour de chaque étape, routes de chaque journée, et la vue d'ensemble."
        } else {
            "Les cartes ne sont pas encore disponibles hors connexion.\n\nLe téléchargement (≈ 120 Mo, à faire en Wi-Fi, appli ouverte) couvre les rues et sentiers autour de chaque étape, les routes de chaque journée et la vue d'ensemble du Japon. Idéal pour Kamikōchi, Gokayama ou la Seseragi Kaidō où le réseau est faible.\n\nConseil : faites-le (ou mettez-le à jour) quelques jours avant le départ."
        }
        val dlg = AlertDialog.Builder(this).setTitle("Cartes hors-ligne").setMessage(status)
            .setPositiveButton(if (offline.isComplete) "Mettre à jour" else "Télécharger") { _, _ -> startOfflineDownload() }
            .setNegativeButton("Fermer", null)
        if (offline.isComplete) dlg.setNeutralButton("Supprimer") { _, _ ->
            offline.deleteAll { loadStyle(); updateSheet(); Toast.makeText(this, "Cartes hors-ligne supprimées", Toast.LENGTH_SHORT).show() }
        }
        dlg.show()
    }

    private var progressDialog: AlertDialog? = null
    private var progressBarView: LinearProgressIndicator? = null
    private var progressLabel: TextView? = null

    private fun showDownloadProgress() {
        if (progressDialog?.isShowing == true) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), 0) }
        progressLabel = TextView(this).apply { text = "Préparation…" }
        progressBarView = LinearProgressIndicator(this).apply { max = 1000; isIndeterminate = false; setPadding(0, dp(12), 0, 0) }
        box.addView(progressLabel); box.addView(progressBarView)
        progressDialog = AlertDialog.Builder(this).setTitle("Téléchargement des cartes").setView(box)
            .setPositiveButton("Masquer", null)
            .setNegativeButton("Arrêter") { _, _ -> offline.cancel() }
            .setCancelable(true).show()
    }

    private fun startOfflineDownload() {
        showDownloadProgress()
        offline.download(trip, object : OfflineMaps.Listener {
            override fun onStyleChanged() = loadStyle()
            override fun onProgress(areaIndex: Int, areaCount: Int, areaName: String, fraction: Double, bytes: Long) {
                val overall = (areaIndex + fraction.coerceIn(0.0, 1.0)) / areaCount
                progressBarView?.setProgressCompat((overall * 1000).toInt(), true)
                progressLabel?.text = "Zone ${areaIndex + 1} / $areaCount — $areaName\n${(overall * 100).toInt()} % · ${bytes / 1_048_576} Mo\nGardez l'application ouverte."
            }
            override fun onFinished(error: String?) {
                progressDialog?.dismiss(); progressDialog = null
                if (error == null) {
                    Toast.makeText(this@MainActivity, "Cartes hors-ligne prêtes (≈ ${offline.sizeBytes / 1_048_576} Mo) ✓", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(this@MainActivity).setTitle("Cartes hors-ligne").setMessage(error).setPositiveButton("OK", null).show()
                }
                updateSheet()
            }
        })
    }

    private fun showNotes(day: Day) {
        val text = buildString {
            append(day.part).append("\n\n")
            append(day.subtitle).append("\n\n")
            append("Nuit : ").append(day.night).append("\n")
            append("Transports : ").append(day.transport).append("\n\n")
            day.notes.forEach { append("• ").append(it).append("\n\n") }
        }
        AlertDialog.Builder(this).setTitle("Jour ${day.num} — ${day.title}").setMessage(text.trim())
            .setPositiveButton("OK", null).show()
    }

    private fun applyKeepScreen() {
        if (progress.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ------------------------------------------------------------------ utilitaires

    private fun openPoi(id: String) {
        val i = Intent(this, PoiActivity::class.java).putExtra(PoiActivity.EXTRA_POI, id)
        lastLocation?.let { i.putExtra(PoiActivity.EXTRA_LAT, it.latitude).putExtra(PoiActivity.EXTRA_LON, it.longitude) }
        poiLauncher.launch(i)
    }

    private fun stopFollowing() {
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
        updateFollowIcon()
    }

    private fun updateFollowIcon() {
        val lc = map?.locationComponent
        val on = lc != null && lc.isLocationComponentActivated && lc.cameraMode != CameraMode.NONE
        b.fabFollow.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (on) R.color.shu else R.color.sumi)
        )
    }

    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
