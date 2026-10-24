package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The GWM H7 cluster layer drawn over the live CarPlay map:
 * - navigation card (turn, distance, road; arrival, time and distance left) top centre or top left;
 * - now-playing card top right while the iPhone reports a track;
 * - outside temperature top right;
 * - bottom bar with fuel and range, speed, and odometer.
 * Vehicle values come from [GwmVehicleData]; a missing or stale value is left out, never guessed.
 * The car's own lamps (READY, gear, drive mode, warnings) draw above this layer.
 */
internal class GwmClusterHudView(context: Context) : View(context) {
    private var snapshot: GwmVehicleData.Snapshot? = null
    private var guidance: ClusterTurnGuidance? = null
    private var music = ClusterMusicState.Snapshot()
    private var musicReceivedAt = 0L
    private var stopMusic: (() -> Unit)? = null

    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val dim = Color.rgb(170, 180, 194)

    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(222, 14, 20, 26) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(48, 255, 255, 255)
    }
    private val bar = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val low = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 159, 10) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private var glyph: Drawable? = null
    private var glyphRes = 0
    private val tick = Runnable { invalidate() }

    fun setSnapshot(next: GwmVehicleData.Snapshot?) {
        if (next == snapshot) return
        snapshot = next
        invalidate()
    }

    fun setGuidance(next: ClusterTurnGuidance?) {
        if (next == guidance) return
        guidance = next
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bar.shader = android.graphics.LinearGradient(0f, h * 0.68f, 0f, h.toFloat(),
            intArrayOf(Color.argb(0, 6, 10, 16), Color.argb(150, 6, 10, 16), Color.argb(215, 6, 10, 16)),
            floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopMusic = ClusterMusicState.observe {
            music = it
            musicReceivedAt = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        stopMusic?.invoke()
        stopMusic = null
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        drawBottomBar(canvas, w, h)
        snapshot?.outsideTempC?.let { temp ->
            text.typeface = medium
            text.textSize = h * 0.045f
            text.textAlign = Paint.Align.RIGHT
            text.color = Color.WHITE
            canvas.drawText(String.format(Locale.getDefault(), "%d°C", temp.roundToInt()), w - w * 0.022f, h * 0.075f, text)
        }
        guidance?.takeIf { it.icon != 0 }?.let { drawNavigation(canvas, it, w, h) }
        if (AirPlayPersistence.loadGwmMusicCard(context)) drawMusic(canvas, w, h)
    }

    /**
     * Lower row, laid out around the H7 stock theme with its lamps along the bottom: the car's
     * EV, speed-limit and lane icons sit in the left fifth, READY under the centre, ECO near 79 %.
     * A soft fade replaces a solid bar so the map runs to the bottom edge.
     */
    private fun drawBottomBar(canvas: Canvas, w: Float, h: Float) {
        val data = snapshot ?: return
        canvas.drawRect(0f, h * 0.68f, w, h, bar)
        val unit = h * 0.165f
        val row = h * 0.835f
        val cx = w / 2f
        text.color = Color.WHITE
        data.speedKph?.let { speed ->
            val baseline = h * 0.855f
            val number = speed.roundToInt().toString()
            text.typeface = bold
            text.textSize = unit * 0.72f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(number, cx, baseline, text)
            val half = text.measureText(number) / 2f
            text.typeface = medium
            text.textSize = unit * 0.22f
            text.textAlign = Paint.Align.LEFT
            text.color = dim
            canvas.drawText("km/h", cx + half + unit * 0.12f, baseline, text)
            text.color = Color.WHITE
        }
        // Fuel and range between the car's lane icon (about 18 %) and the speed.
        val fuelLeft = w * 0.24f
        data.fuelPercent?.let { fuel ->
            val icon = unit * 0.36f
            drawPump(canvas, fuelLeft, row - icon / 2f, icon)
            val barLeft = fuelLeft + icon * 1.3f
            val barW = w * 0.075f
            val barH = unit * 0.07f
            rect.set(barLeft, row - barH / 2f, barLeft + barW, row + barH / 2f)
            canvas.drawRoundRect(rect, barH, barH, track)
            rect.right = barLeft + barW * (fuel / 100f).coerceIn(0.03f, 1f)
            canvas.drawRoundRect(rect, barH, barH, if (fuel < 15f) low else fill)
        }
        data.rangeKm?.let { range ->
            text.typeface = medium
            text.textSize = unit * 0.27f
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(String.format(Locale.getDefault(), "%d km", range.roundToInt()),
                fuelLeft + unit * 0.36f * 1.3f + w * 0.075f + w * 0.012f, row + unit * 0.10f, text)
        }
        // Odometer right of the car's ECO lamp.
        data.odometerKm?.let { odo ->
            text.typeface = medium
            text.textSize = unit * 0.24f
            text.textAlign = Paint.Align.RIGHT
            val value = NumberFormat.getIntegerInstance().format(odo.roundToInt()) + " km"
            val right = w - w * 0.022f
            canvas.drawText(value, right, row + unit * 0.09f, text)
            text.color = dim
            canvas.drawText("ODO  ", right - text.measureText(value), row + unit * 0.09f, text)
            text.color = Color.WHITE
        }
    }

    private fun drawNavigation(canvas: Canvas, next: ClusterTurnGuidance, w: Float, h: Float) {
        val cw = w * 0.235f
        val ch = h * 0.20f
        val topLeft = AirPlayPersistence.loadGwmCardAnchor(context) == 1
        val left = if (topLeft) w * 0.06f else (w - cw) / 2f
        val top = if (topLeft) h * 0.13f else h * 0.105f
        val radius = ch * 0.16f
        rect.set(left, top, left + cw, top + ch)
        canvas.drawRoundRect(rect, radius, radius, glass)
        edge.strokeWidth = h * 0.002f
        canvas.drawRoundRect(rect, radius, radius, edge)

        val res = clusterTurnGlyphRes(next.icon)
        if (res != glyphRes) {
            glyph = ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(Color.WHITE) }
            glyphRes = res
        }
        val side = ch * 0.40f
        val gx = left + ch * 0.12f
        val gy = top + ch * 0.10f
        glyph?.setBounds(gx.toInt(), gy.toInt(), (gx + side).toInt(), (gy + side).toInt())
        glyph?.draw(canvas)

        val textLeft = left + ch * 0.66f
        val textWidth = left + cw - ch * 0.10f - textLeft
        text.color = Color.WHITE
        text.textAlign = Paint.Align.LEFT
        text.typeface = bold
        text.textSize = ch * 0.20f
        canvas.drawText(fit(distanceLabel(next.distanceMeters), textWidth), textLeft, top + ch * 0.30f, text)
        text.typeface = sans
        text.textSize = ch * 0.155f
        val road = if (next.roundaboutExit in 1..9) context.getString(R.string.turn_card_exit, next.roundaboutExit) else next.road
        if (road.isNotEmpty()) canvas.drawText(fit(road, textWidth), textLeft, top + ch * 0.53f, text)

        canvas.drawRect(left + ch * 0.12f, top + ch * 0.655f, left + cw - ch * 0.12f, top + ch * 0.655f + 1.5f, line)
        val info = infoParts(next).joinToString("  ·  ")
        if (info.isNotEmpty()) {
            text.typeface = medium
            text.textSize = ch * 0.135f
            canvas.drawText(fit(info, left + cw - ch * 0.12f - textLeft), textLeft, top + ch * 0.87f, text)
        }
    }

    private fun drawMusic(canvas: Canvas, w: Float, h: Float) {
        val info = music.info
        val title = info.title?.takeIf { it.isNotBlank() } ?: return
        val mw = w * 0.17f
        val mh = h * 0.185f
        val left = w - mw - w * 0.015f
        val top = h * 0.29f // below the car's top-right warning lamps
        val radius = mh * 0.14f
        rect.set(left, top, left + mw, top + mh)
        canvas.drawRoundRect(rect, radius, radius, glass)
        edge.strokeWidth = h * 0.002f
        canvas.drawRoundRect(rect, radius, radius, edge)

        val side = mh * 0.76f
        val ax = left + mh * 0.12f
        val ay = top + (mh - side) / 2f
        music.artwork?.takeUnless(Bitmap::isRecycled)?.let { bitmap ->
            rect.set(ax, ay, ax + side, ay + side)
            canvas.save()
            clip.reset()
            clip.addRoundRect(rect, side * 0.08f, side * 0.08f, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawBitmap(bitmap, null, rect, art)
            canvas.restore()
        }
        val textLeft = ax + side + mh * 0.12f
        val textWidth = left + mw - mh * 0.10f - textLeft
        if (textWidth <= 0f) return
        text.textAlign = Paint.Align.LEFT
        text.color = Color.WHITE
        text.typeface = bold
        text.textSize = mh * 0.16f
        canvas.drawText(fit(title, textWidth), textLeft, top + mh * 0.36f, text)
        info.artist?.takeIf { it.isNotBlank() }?.let { artist ->
            text.typeface = sans
            text.textSize = mh * 0.135f
            text.color = dim
            canvas.drawText(fit(artist, textWidth), textLeft, top + mh * 0.58f, text)
            text.color = Color.WHITE
        }
        val duration = info.durationMillis?.takeIf { it > 0 } ?: return
        var elapsed = info.elapsedMillis ?: return
        if (info.playing) {
            elapsed += SystemClock.elapsedRealtime() - musicReceivedAt
            removeCallbacks(tick)
            postDelayed(tick, 1_000L)
        }
        val barH = mh * 0.035f
        val barTop = top + mh * 0.76f
        rect.set(textLeft, barTop, textLeft + textWidth, barTop + barH)
        canvas.drawRoundRect(rect, barH, barH, track)
        rect.right = textLeft + textWidth * (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        canvas.drawRoundRect(rect, barH, barH, fill)
    }

    private fun infoParts(next: ClusterTurnGuidance): List<String> {
        val parts = mutableListOf<String>()
        next.arrivalEpochSeconds?.takeIf { it > 0 }?.let { epoch ->
            parts += context.getString(R.string.turn_card_info_arrival,
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch * 1000L)))
        }
        next.remainingSeconds?.takeIf { it > 0 }?.let { total ->
            val hours = (total / 3600).toInt()
            val minutes = ((total % 3600) / 60).toInt()
            parts += if (hours > 0) context.getString(R.string.turn_card_info_duration_hm, hours, minutes)
            else context.getString(R.string.turn_card_info_duration_m, minutes)
        }
        next.remainingMeters?.takeIf { it > 0 }?.let { meters ->
            parts += context.getString(R.string.turn_card_info_km, meters / 1000.0)
        }
        return parts
    }

    private fun distanceLabel(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, (meters / 10) * 10)
        else -> context.getString(R.string.turn_card_distance_km, meters / 100 / 10f)
    }

    private fun fit(value: String, maxWidth: Float): String {
        if (text.measureText(value) <= maxWidth) return value
        var end = value.length
        while (end > 0 && text.measureText(value.take(end) + "…") > maxWidth) end--
        return value.take(end) + "…"
    }

    /** A plain fuel-pump outline: body, window and nozzle hose. */
    private fun drawPump(canvas: Canvas, left: Float, top: Float, size: Float) {
        stroke.strokeWidth = size * 0.11f
        rect.set(left, top, left + size * 0.62f, top + size)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, stroke)
        rect.set(left + size * 0.14f, top + size * 0.16f, left + size * 0.48f, top + size * 0.40f)
        canvas.drawRect(rect, fill)
        canvas.drawLine(left + size * 0.62f, top + size * 0.30f, left + size * 0.86f, top + size * 0.46f, stroke)
        canvas.drawLine(left + size * 0.86f, top + size * 0.46f, left + size * 0.86f, top + size * 0.86f, stroke)
    }
}
