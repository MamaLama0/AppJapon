package com.appjapon.carnet.map

import android.location.Location
import android.os.Handler
import android.os.Looper
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Overlay « ma position » qui notifie l'activité à chaque nouvelle position. */
class TrackingLocationOverlay(
    provider: IMyLocationProvider,
    map: MapView,
    private val onFix: (Location) -> Unit,
) : MyLocationNewOverlay(provider, map) {
    private val main = Handler(Looper.getMainLooper())

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        super.onLocationChanged(location, source)
        if (location != null) main.post { onFix(location) }
    }
}
