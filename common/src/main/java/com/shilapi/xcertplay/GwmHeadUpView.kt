package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The GWM head-up display strip: a large speed, a small turn arrow with its distance while a route
 * is active, and range with a fuel bar. The background stays transparent so the car's own HUD
 * readouts remain visible; on a windscreen HUD black is invisible anyway. Missing values are left out.
 */
internal class GwmHeadUpView(context: Context) : View(context) {
    enum class Anchor { LEFT, CENTRE, RIGHT }

    private var snapshot: GwmVehicleData.Snapshot? = null
    private var guidance: ClusterTurnGuidance? = null
    private var anchor = Anchor.CENTRE

    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 200, 214)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 76, 88) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val low = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 159, 10) }
    private val arrowTint = Color.rgb(90, 200, 250)
    private val rect = RectF()
    private var glyph: Drawable? = null
    private var glyphRes = 0

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

    fun setAnchor(next: Anchor) {
        anchor = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val data = snapshot
        val next = guidance?.takeIf { it.icon != 0 }

        // One row: [arrow + distance] [speed] [range + fuel], sized from the panel height.
        val unitH = h * 0.62f
        val sideW = unitH * 1.7f
        val rowW = sideW * 2f + unitH * 1.5f
        val left = when (anchor) {
            Anchor.LEFT -> h * 0.15f
            Anchor.CENTRE -> (w - rowW) / 2f
            Anchor.RIGHT -> w - rowW - h * 0.15f
        }.coerceIn(0f, (w - rowW).coerceAtLeast(0f))
        val midY = h / 2f
        val speedCx = left + sideW + unitH * 0.75f

        data?.speedKph?.let { speed ->
            speedPaint.textSize = unitH * 0.95f
            canvas.drawText(speed.roundToInt().toString(), speedCx, midY + unitH * 0.28f, speedPaint)
            unit.textSize = unitH * 0.20f
            canvas.drawText("km/h", speedCx, midY + unitH * 0.56f, unit)
        }

        next?.let { turn ->
            val res = clusterTurnGlyphRes(turn.icon)
            if (res != glyphRes) {
                glyph = ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(arrowTint) }
                glyphRes = res
            }
            val side = unitH * 0.62f
            val gx = left + sideW - side - unitH * 0.15f
            glyph?.setBounds(gx.toInt(), (midY - side * 0.85f).toInt(), (gx + side).toInt(), (midY + side * 0.15f).toInt())
            glyph?.draw(canvas)
            small.textSize = unitH * 0.24f
            small.textAlign = Paint.Align.RIGHT
            canvas.drawText(distance(turn.distanceMeters), gx + side, midY + unitH * 0.48f, small)
        }

        val rangeLeft = left + sideW + unitH * 1.5f + unitH * 0.15f
        data?.rangeKm?.let { range ->
            small.textSize = unitH * 0.26f
            small.textAlign = Paint.Align.LEFT
            canvas.drawText(String.format(Locale.getDefault(), "%d km", range.roundToInt()),
                rangeLeft, midY - unitH * 0.05f, small)
        }
        data?.fuelPercent?.let { fuel ->
            val barW = sideW * 0.8f
            val barH = unitH * 0.07f
            val top = midY + unitH * 0.16f
            rect.set(rangeLeft, top, rangeLeft + barW, top + barH)
            canvas.drawRoundRect(rect, barH, barH, track)
            rect.right = rangeLeft + barW * (fuel / 100f).coerceIn(0.03f, 1f)
            canvas.drawRoundRect(rect, barH, barH, if (fuel < 15f) low else fill)
        }
    }

    private fun distance(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, (meters / 10) * 10)
        else -> context.getString(R.string.turn_card_distance_km, meters / 100 / 10f)
    }
}
