package com.appjapon.carnet.map

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.appjapon.carnet.data.Category

/** Génère les icônes de la carte (pastilles dessinées, sans ressources externes). */
class MarkerFactory(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val cache = HashMap<String, Bitmap>()

    private fun dp(v: Float) = v * density

    /** Pastille de catégorie : cercle coloré + idéogramme blanc. */
    fun categoryKey(cat: Category, dimmed: Boolean = false) = "cat_${cat.key}_$dimmed"
    fun categoryBitmap(cat: Category, dimmed: Boolean = false): Bitmap =
        cache.getOrPut(categoryKey(cat, dimmed)) {
            pin(cat.glyph, if (dimmed) blend(cat.color, Color.WHITE, 0.45f) else cat.color, size = 30f, pointer = true)
        }
    fun category(cat: Category, dimmed: Boolean = false): Drawable = BitmapDrawable(context.resources, categoryBitmap(cat, dimmed))

    /** Marqueur numéroté d'une étape du jour. */
    fun stepKey(number: Int, color: Int, done: Boolean, optional: Boolean, current: Boolean) =
        "step_${number}_${color}_${done}_${optional}_$current"
    fun stepBitmap(number: Int, color: Int, done: Boolean, optional: Boolean, current: Boolean): Bitmap =
        cache.getOrPut(stepKey(number, color, done, optional, current)) {
            val fill = if (done) Color.parseColor("#9AA0A6") else color
            pin(if (done) "✓" else number.toString(), fill, size = if (current) 38f else 32f, pointer = true,
                ring = if (current) Color.parseColor("#FFC107") else Color.WHITE, dashed = optional && !done)
        }

    /** Étiquette de région (zoom éloigné). */
    fun regionKey(label: String) = "region_$label"
    fun regionBitmap(label: String, count: Int, color: Int): Bitmap = cache.getOrPut(regionKey(label)) {
        val text = "$label · $count"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; textSize = dp(13f); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val w = (paint.measureText(text) + dp(24f)).toInt()
        val h = dp(30f).toInt()
        val bmp = Bitmap.createBitmap(w, h + dp(6f).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; setShadowLayer(dp(3f), 0f, dp(1f), 0x55000000) }
        val r = RectF(dp(2f), dp(2f), w - dp(2f), h - dp(2f))
        c.drawRoundRect(r, h / 2f, h / 2f, bg)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f); this.color = Color.WHITE }
        c.drawRoundRect(r, h / 2f, h / 2f, border)
        val fm = paint.fontMetrics
        c.drawText(text, dp(12f), h / 2f - (fm.ascent + fm.descent) / 2, paint)
        bmp.also { it.density = context.resources.displayMetrics.densityDpi }
    }

    private fun pin(text: String, fill: Int, size: Float, pointer: Boolean, ring: Int = Color.WHITE, dashed: Boolean = false): Bitmap {
        val d = dp(size)
        val pointerH = if (pointer) dp(7f) else 0f
        val pad = dp(3f)
        val w = (d + pad * 2).toInt()
        val h = (d + pad * 2 + pointerH).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f; val cy = pad + d / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; setShadowLayer(dp(2.5f), 0f, dp(1f), 0x66000000) }
        if (pointer) {
            val path = Path().apply {
                moveTo(cx - d * 0.28f, cy + d * 0.35f)
                lineTo(cx, cy + d / 2f + pointerH)
                lineTo(cx + d * 0.28f, cy + d * 0.35f)
                close()
            }
            c.drawPath(path, fillPaint)
        }
        c.drawCircle(cx, cy, d / 2f, fillPaint)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2.2f); color = ring
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(2.5f)), 0f)
        }
        c.drawCircle(cx, cy, d / 2f - dp(1.1f), ringPaint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER
            textSize = d * if (text.length >= 2) 0.46f else 0.56f
            typeface = Typeface.DEFAULT_BOLD
        }
        val fm = tp.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, tp)
        bmp.density = context.resources.displayMetrics.densityDpi
        return bmp
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
    )
}
