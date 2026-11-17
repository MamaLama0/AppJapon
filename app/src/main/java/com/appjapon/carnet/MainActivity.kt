package com.appjapon.carnet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
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
import com.appjapon.carnet.map.MarkerFactory
import com.appjapon.carnet.map.TrackingLocationOverlay
import com.appjapon.carnet.ui.DayAdapter
import com.appjapon.carnet.ui.Format
import com.appjapon.carnet.ui.NearbyAdapter
import com.appjapon.carnet.ui.NearbyItem
import com.appjapon.carnet.ui.PoiActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private companion object {
        const val OVERVIEW = 0
        /** En dessous : étiquettes de grandes zones (Kyoto / Alpes / Tokyo). */
        const val ZOOM_GROUPS = 8.0
        /** En dessous : étiquettes de régions ; au-dessus : lieux. */
        const val ZOOM_REGIONS = 10.5
        val JAPAN_CENTER = GeoPoint(35.9, 137.6)
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var trip: Trip
    private lateinit var progress: ProgressStore
    private lateinit var tracks: TrackStore
    private lateinit var icons: MarkerFactory
    private lateinit var sheet: BottomSheetBehavior<View>

    private var selectedDay = OVERVIEW
    private var lastLocation: Location? = null
    private var locationOverlay: TrackingLocationOverlay? = null

    private val poiMarkers = HashMap<String, Marker>()
    private val regionMarkers = mutableListOf<Marker>()
    private val groupMarkers = mutableListOf<Marker>()
    private val dayOverlays = mutableListOf<Overlay>()
    private val dayPoiIds = HashSet<String>()
    private var routeDone: Polyline? = null
    private var routeTodo: Polyline? = null
    private var activeTrack: List<List<GeoPoint>>? = null
    private var activeTrackImported = false
    private var activeTrackLength = 0.0
    private var lastProjection: Geo.Projection? = null
    private val insidePois = HashSet<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val hideProximity = Runnable { b.proximityCard.visibility = View.GONE }

    private val stepAdapter = com.appjapon.carnet.ui.StepAdapter(
        onOpen = { step -> step.poiIds.firstOrNull()?.let { openPoi(it) } },
        onToggle = { step, checked -> progress.setDone(selectedDay, step.index, checked); refreshDay(recenter = false) },
    )
    private val dayAdapter = DayAdapter { selectDay(it.num) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) enableLocation()
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
            locationOverlay?.disableFollowLocation(); updateFollowIcon()
            sheet.state = BottomSheetBehavior.STATE_COLLAPSED
            b.map.controller.animateTo(GeoPoint(p.lat, p.lon), 17.0, 700L)
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "${packageName}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            tileFileSystemCacheMaxBytes = 1_000L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 800L * 1024 * 1024
        }
        enableEdgeToEdge()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        trip = TripRepository.get(this)
        progress = ProgressStore(this)
        tracks = TrackStore(this)
        icons = MarkerFactory(this)

        setupInsets()
        setupMap()
        setupSheet()
        setupChips()
        setupButtons()

        val today = trip.todayDay()
        selectDay(today?.num ?: progress.lastDay, animate = false)
        applyKeepScreen()
        ensureLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        locationOverlay?.enableMyLocation()
        refreshDay(recenter = false) // la fiche lieu peut avoir modifié la progression
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
        locationOverlay?.disableMyLocation()
    }

    // ------------------------------------------------------------------ mise en place

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.topBar.updatePadding(top = bars.top)
            b.list.updatePadding(bottom = bars.bottom + dp(24))
            b.topBar.post { onTopBarLaidOut() }
            insets
        }
    }

    private fun onTopBarLaidOut() {
        val h = b.topBar.height
        sheet.expandedOffset = h
        scaleBar?.setScaleBarOffset(dp(12), h + dp(8))
        copyright?.setOffset(dp(8), h + dp(4))
        b.map.invalidate()
    }

    private var scaleBar: ScaleBarOverlay? = null
    private var copyright: CopyrightOverlay? = null

    private fun setupMap() = with(b.map) {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 4.5
        maxZoomLevel = 19.5
        controller.setZoom(6.3)
        controller.setCenter(JAPAN_CENTER)

        copyright = CopyrightOverlay(this@MainActivity).apply { setAlignBottom(false); setAlignRight(true) }
        scaleBar = ScaleBarOverlay(this).apply { setAlignBottom(false); setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric) }
        overlays += copyright!!
        overlays += scaleBar!!

        createPoiMarkers()
        createRegionMarkers()

        addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { updateFollowIcon(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { updateVisibility(); return false }
        })
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
                b.map.controller.animateTo(GeoPoint(loc), maxOf(b.map.zoomLevelDouble, 16.0), 700L)
            } else Toast.makeText(this, "Recherche du signal GPS…", Toast.LENGTH_SHORT).show()
        }
        b.fabFollow.setOnClickListener {
            val ov = locationOverlay ?: run { ensureLocationPermission(); return@setOnClickListener }
            if (ov.isFollowLocationEnabled) ov.disableFollowLocation()
            else {
                ov.enableFollowLocation()
                if (b.map.zoomLevelDouble < 16) b.map.controller.setZoom(17.0)
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
                Toast.makeText(this, "La carte suit votre position", Toast.LENGTH_SHORT).show()
            }
            updateFollowIcon()
        }
        b.fabNearby.setOnClickListener { showNearby() }
        b.btnMore.setOnClickListener { showMenu(it) }
        b.proximityCard.setOnClickListener { v -> (v.tag as? String)?.let { openPoi(it) } }
    }

    // ------------------------------------------------------------------ marqueurs

    private fun createPoiMarkers() {
        trip.pois.values.forEach { p ->
            val m = Marker(b.map).apply {
                position = GeoPoint(p.lat, p.lon)
                icon = icons.category(p.category)
                setAnchor(Marker.ANCHOR_CENTER, icons.pinAnchorV(30f))
                title = p.name
                setOnMarkerClickListener { _, _ -> openPoi(p.id); true }
            }
            poiMarkers[p.id] = m
            b.map.overlays += m
        }
    }

    private fun groupOf(region: String) = when (region) {
        "Kyoto" -> "Kyoto"
        "Tokyo" -> "Tokyo"
        else -> "Alpes japonaises"
    }

    private fun createRegionMarkers() {
        fun label(name: String, pois: List<Poi>, list: MutableList<Marker>) {
            val color = when (name) {
                "Kyoto" -> ContextCompat.getColor(this, R.color.shu)
                "Tokyo" -> Color.parseColor("#37474F")
                else -> ContextCompat.getColor(this, R.color.matsu)
            }
            val box = BoundingBox.fromGeoPoints(pois.map { GeoPoint(it.lat, it.lon) })
            list += Marker(b.map).apply {
                position = GeoPoint(pois.map { it.lat }.average(), pois.map { it.lon }.average())
                icon = icons.region(name, pois.size, color)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, map -> zoomTo(box, map.zoomLevelDouble + 2); true }
            }
        }
        trip.pois.values.groupBy { it.region }.forEach { (r, pois) -> label(r, pois, regionMarkers) }
        trip.pois.values.groupBy { groupOf(it.region) }.forEach { (g, pois) -> label(g, pois, groupMarkers) }
        b.map.overlays.addAll(regionMarkers)
        b.map.overlays.addAll(groupMarkers)
    }

    /** Les lieux apparaissent progressivement en zoomant, selon leur importance. */
    private fun updateVisibility() {
        val z = b.map.zoomLevelDouble
        groupMarkers.forEach { it.isEnabled = z < ZOOM_GROUPS }
        regionMarkers.forEach { it.isEnabled = z >= ZOOM_GROUPS && z < ZOOM_REGIONS }
        poiMarkers.forEach { (id, m) ->
            val p = trip.pois.getValue(id)
            val threshold = when (p.importance) { 1 -> ZOOM_REGIONS; 2 -> 13.0; else -> 14.5 }
            m.isEnabled = id !in dayPoiIds && z >= threshold
            m.icon = icons.category(p.category, dimmed = selectedDay != OVERVIEW)
        }
        b.map.invalidate()
    }

    private fun zoomTo(box: BoundingBox, minZoom: Double? = null) {
        val padded = box.increaseByScale(1.25f)
        if (box.latitudeSpan < 0.002 && box.longitudeSpanWithDateLine < 0.002) {
            b.map.controller.animateTo(box.centerWithDateLine, 16.0, 700L)
        } else {
            b.map.zoomToBoundingBox(padded, true, dp(48))
            if (minZoom != null && b.map.zoomLevelDouble < minZoom) b.map.controller.zoomTo(minZoom)
        }
    }

    // ------------------------------------------------------------------ jour sélectionné

    private fun selectDay(num: Int, animate: Boolean = true) {
        selectedDay = if (trip.day(num) != null) num else OVERVIEW
        progress.lastDay = selectedDay
        for (i in 0 until b.dayChips.childCount) {
            val c = b.dayChips.getChildAt(i) as Chip
            c.isChecked = c.tag == selectedDay
            if (c.isChecked) c.post { (b.dayChips.parent as View).let { sv -> (sv as android.widget.HorizontalScrollView).smoothScrollTo(c.left - dp(40), 0) } }
        }
        lastProjection = null
        insidePois.clear()
        refreshDay(recenter = true, animate = animate)
    }

    private fun clearDayOverlays() {
        b.map.overlays.removeAll(dayOverlays.toSet())
        dayOverlays.clear()
        dayPoiIds.clear()
        routeDone = null; routeTodo = null
    }

    /** Recalcule tout l'affichage du jour (tracé, marqueurs numérotés, liste, statut). */
    private fun refreshDay(recenter: Boolean, animate: Boolean = true) {
        clearDayOverlays()
        val day = trip.day(selectedDay)
        if (day == null) {
            drawOverview()
            if (recenter) {
                if (animate) b.map.controller.animateTo(JAPAN_CENTER, 6.3, 800L)
                else { b.map.controller.setZoom(6.3); b.map.controller.setCenter(JAPAN_CENTER) }
            }
        } else {
            drawDay(day)
            if (recenter) {
                val pts = dayPoints(day)
                val box = if (pts.size >= 2) BoundingBox.fromGeoPoints(activeTrack!!.flatten() + pts) else null
                val fit = {
                    if (box != null) zoomTo(box)
                    else if (pts.size == 1) b.map.controller.animateTo(pts[0], 15.0, 700L)
                }
                if (b.map.width == 0) b.map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() } else fit()
            }
        }
        // Les overlays du jour passent sous les marqueurs de lieux mais au-dessus des tuiles.
        b.map.overlays.addAll(2, dayOverlays.filterIsInstance<Polyline>())
        b.map.overlays.addAll(dayOverlays.filterIsInstance<Marker>())
        lastLocation?.let { updateRouteProgress(GeoPoint(it)) }
        updateVisibility()
        updateSheet()
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

    private fun drawDay(day: Day) {
        val (track, imported) = trackFor(day)
        activeTrack = track; activeTrackImported = imported
        activeTrackLength = Geo.length(track)

        routeTodo = polyline(day.color, 6f, dashed = !imported)
        routeDone = polyline(ContextCompat.getColor(this, R.color.done), 6f, dashed = !imported)
        routeTodo!!.setPoints(track.flatten())
        if (track.size > 1) { // plusieurs segments importés : un polyline par segment
            routeTodo!!.setPoints(track.first())
            track.drop(1).forEach { seg -> dayOverlays += polyline(day.color, 6f, dashed = false).apply { setPoints(seg) } }
        }
        dayOverlays += routeDone!!
        dayOverlays += routeTodo!!

        val done = progress.done(day.num)
        val current = currentStepIndex(day, done)
        day.trackableSteps.forEachIndexed { i, s ->
            s.poiIds.forEach { pid ->
                if (pid in dayPoiIds) return@forEach
                val p = trip.pois[pid] ?: return@forEach
                dayPoiIds += pid
                val isCurrent = s.index == current
                val size = if (isCurrent) 38f else 32f
                dayOverlays += Marker(b.map).apply {
                    position = GeoPoint(p.lat, p.lon)
                    icon = icons.step(i + 1, day.color, s.index in done, s.optional, isCurrent)
                    setAnchor(Marker.ANCHOR_CENTER, icons.pinAnchorV(size))
                    title = p.name
                    setOnMarkerClickListener { _, _ -> openPoi(p.id); true }
                }
            }
        }
    }

    private fun drawOverview() {
        activeTrack = null
        trip.days.forEach { d ->
            val (track, _) = trackFor(d)
            track.forEach { seg -> dayOverlays += polyline(d.color, 3.5f, dashed = false, alpha = 170).apply { setPoints(seg) } }
        }
    }

    private fun polyline(color: Int, widthDp: Float, dashed: Boolean, alpha: Int = 230) = Polyline(b.map).apply {
        outlinePaint.color = color
        outlinePaint.alpha = alpha
        outlinePaint.strokeWidth = widthDp * resources.displayMetrics.density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        if (dashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(dp(10).toFloat(), dp(7).toFloat()), 0f)
        isGeodesic = false
        setOnClickListener { _, _, _ -> false }
    }

    // ------------------------------------------------------------------ position & progression

    private fun onLocation(loc: Location) {
        val first = lastLocation == null
        lastLocation = loc
        val gp = GeoPoint(loc)
        checkProximity(gp, loc.accuracy)
        updateRouteProgress(gp)
        if (selectedDay == OVERVIEW) updateOverviewSheet() else updateStatus()
        if (first) updateFollowIcon()
    }

    private fun updateRouteProgress(gp: GeoPoint) {
        val track = activeTrack ?: return
        if (selectedDay == OVERVIEW || track.isEmpty() || track.all { it.size < 2 }) return
        val proj = Geo.project(track, gp, lastProjection?.along) ?: return
        lastProjection = proj
        if (track.size == 1) {
            val (done, todo) = Geo.split(track, proj)
            routeDone?.setPoints(done.flatten())
            routeTodo?.setPoints(todo.flatten())
            b.map.invalidate()
        }
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
        b.statusText.text = lines.joinToString("\n")
    }

    private fun scheduleLine(day: Day, time: String): String? {
        if (day.date != LocalDate.now()) return null
        val t = Format.parseTime(time) ?: return null
        val diff = ChronoUnit.MINUTES.between(t, LocalTime.now())
        val prefix = if (day.approxTimes) "Vers ${time}" else "Prévu à $time"
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
        val today = LocalDate.now()
        val daysTo = ChronoUnit.DAYS.between(today, trip.start)
        b.statusText.text = when {
            daysTo > 1 -> "Départ dans $daysTo jours. Zoomez sur la carte pour faire apparaître les lieux, touchez-en un pour lire son histoire."
            daysTo == 1L -> "Départ demain ! Touchez un jour pour suivre son itinéraire."
            trip.todayDay() != null -> "Aujourd'hui : jour ${trip.todayDay()!!.num}. Touchez-le pour suivre votre itinéraire."
            else -> "Touchez un jour pour afficher son itinéraire et suivre votre progression."
        }
    }

    // ------------------------------------------------------------------ autour de moi

    private fun showNearby() {
        val loc = lastLocation
        val center = loc?.let { GeoPoint(it) } ?: (b.map.mapCenter as GeoPoint)
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
        if (day != null) {
            menu.menu.add(0, 1, 0, R.string.import_track)
            if (tracks.has(day.num)) menu.menu.add(0, 2, 1, R.string.remove_track)
            menu.menu.add(0, 3, 2, R.string.reset_day)
            menu.menu.add(0, 6, 3, "Notes & conseils du jour")
        }
        menu.menu.add(1, 4, 4, R.string.auto_check).apply { isCheckable = true; isChecked = progress.autoCheck }
        menu.menu.add(1, 5, 5, R.string.keep_screen).apply { isCheckable = true; isChecked = progress.keepScreenOn }
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
            }
            true
        }
        menu.show()
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

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) enableLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun enableLocation() {
        if (locationOverlay != null) return
        val provider = GpsMyLocationProvider(this).apply { locationUpdateMinTime = 2_000; locationUpdateMinDistance = 3f }
        locationOverlay = TrackingLocationOverlay(provider, b.map) { onLocation(it) }.apply {
            enableMyLocation()
            isDrawAccuracyEnabled = true
        }
        b.map.overlays += locationOverlay!!
        b.map.invalidate()
    }

    private fun updateFollowIcon() {
        val on = locationOverlay?.isFollowLocationEnabled == true
        b.fabFollow.imageTintList = ColorStateList.valueOf(
            if (on) ContextCompat.getColor(this, R.color.shu) else ContextCompat.getColor(this, R.color.sumi)
        )
    }

    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
