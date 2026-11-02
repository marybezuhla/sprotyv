package ua.sprotyv.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.sprotyv.R
import java.io.File

/** The home-screen widgets as the phone's widget list shows them (their preview layouts), for checking by eye. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "uk-w400dp-h900dp-xhdpi")
class WidgetPreviewTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun draw(layout: Int, wDp: Int, hDp: Int): Bitmap {
        val d = app.resources.displayMetrics.density
        val v = LayoutInflater.from(app).inflate(layout, null)
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }

    @Test fun all() {
        // Cells of about 80 × 90 dp, on a wallpaper-like grey
        val items = listOf(
            R.layout.widget_now to (170 to 80), R.layout.widget_tile_blue to (170 to 80), R.layout.widget_running to (260 to 80),
            R.layout.widget_path to (170 to 80), R.layout.widget_gart to (170 to 80), R.layout.widget_kit to (170 to 80),
            R.layout.widget_services to (350 to 80), R.layout.widget_kid to (170 to 170), R.layout.widget_count to (80 to 90),
            R.layout.widget_safe to (80 to 90),
        )
        val d = app.resources.displayMetrics.density
        val pad = (12 * d).toInt()
        val width = (380 * d).toInt()
        val bitmaps = items.map { (l, s) -> draw(l, s.first, s.second) }
        val height = bitmaps.sumOf { it.height + pad } + pad
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(70, 62, 90))
        var y = pad
        bitmaps.forEach { c.drawBitmap(it, pad.toFloat(), y.toFloat(), null); y += it.height + pad }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "60-widgets.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
