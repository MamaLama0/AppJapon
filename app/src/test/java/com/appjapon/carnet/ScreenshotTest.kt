package com.appjapon.carnet

import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.view.View
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import com.appjapon.carnet.ui.PoiActivity
import com.google.android.material.chip.Chip
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Lance les vrais écrans et enregistre des captures dans build/screenshots (vérification visuelle). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun save(view: View, name: String) {
        ShadowLooper.idleMainLooper()
        view.drawToBitmap(Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 90, File(out, "$name.png").outputStream())
    }

    @Test
    fun mainScreens() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        val root = a.window.decorView
        save(root, "1_overview")

        // Sélection du jour 2 via le chip
        val chips = a.findViewById<com.google.android.material.chip.ChipGroup>(R.id.dayChips)
        (0 until chips.childCount).map { chips.getChildAt(it) as Chip }.first { it.tag == 2 }.performClick()
        save(root, "2_day2")

        // Simule une position GPS juste devant Kiyomizu-dera
        val m = MainActivity::class.java.getDeclaredMethod("onLocation", Location::class.java).apply { isAccessible = true }
        m.invoke(a, Location("gps").apply { latitude = 34.99445; longitude = 135.78450; accuracy = 10f })
        save(root, "3_day2_at_kiyomizu")
        m.invoke(a, Location("gps").apply { latitude = 34.99800; longitude = 135.78090; accuracy = 10f })
        save(root, "4_day2_walking")
    }

    @Test
    fun poiScreen() {
        val i = Intent(ApplicationProvider.getApplicationContext(), PoiActivity::class.java)
            .putExtra(PoiActivity.EXTRA_POI, "kiyomizudera").putExtra(PoiActivity.EXTRA_LAT, 35.0093).putExtra(PoiActivity.EXTRA_LON, 135.7625)
        val a = Robolectric.buildActivity(PoiActivity::class.java, i).setup().get()
        save(a.window.decorView, "5_poi_kiyomizu")
    }
}
