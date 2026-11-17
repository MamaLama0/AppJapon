package com.appjapon.carnet

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import com.appjapon.carnet.ui.PoiActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
    fun poiScreen() {
        val i = Intent(ApplicationProvider.getApplicationContext(), PoiActivity::class.java)
            .putExtra(PoiActivity.EXTRA_POI, "kiyomizudera").putExtra(PoiActivity.EXTRA_LAT, 35.0093).putExtra(PoiActivity.EXTRA_LON, 135.7625)
        val a = Robolectric.buildActivity(PoiActivity::class.java, i).setup().get()
        save(a.window.decorView, "5_poi_kiyomizu")
    }
}
