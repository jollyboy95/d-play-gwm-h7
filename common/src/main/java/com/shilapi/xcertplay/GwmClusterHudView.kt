package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The GWM H7 cluster strip drawn over the live CarPlay map: fuel and range, speed, gear along the
 * bottom and the outside temperature top right. Values come from [GwmVehicleData]; a missing or
 * stale value is left out rather than shown as a guess. Sizes follow the panel height.
 */
internal class GwmClusterHudView(context: Context) : View(context) {
    private var snapshot: GwmVehicleData.Snapshot? = null

    private val bandPaint = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 178, 192)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val low = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 159, 10) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(220, 255, 255, 255)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.RIGHT
    }
    private val rect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // the temperature shadow needs a software layer
    }

    fun setSnapshot(next: GwmVehicleData.Snapshot?) {
        if (next == snapshot) return
        snapshot = next
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val top = h * 0.70f
        bandPaint.shader = LinearGradient(0f, top, 0f, h.toFloat(),
            Color.argb(0, 5, 8, 15), Color.argb(235, 5, 8, 15), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = snapshot ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, h * 0.70f, w, h, bandPaint)

        val cx = w / 2f
        val baseline = h * 0.92f

        // Speed, centred.
        data.speedKph?.let { speed ->
            speedPaint.textSize = h * 0.15f
            canvas.drawText(speed.roundToInt().toString(), cx, h * 0.865f, speedPaint)
            unitPaint.textSize = h * 0.034f
            canvas.drawText("km/h", cx, h * 0.935f, unitPaint)
        }

        // Fuel bar and range, left of the speed.
        val barRight = cx - h * 0.20f
        val barWidth = h * 0.24f
        val barTop = baseline - h * 0.045f
        val barHeight = h * 0.017f
        data.rangeKm?.let { range ->
            text.textSize = h * 0.045f
            text.textAlign = Paint.Align.LEFT
            val label = String.format(Locale.getDefault(), "%d km", range.roundToInt())
            canvas.drawText(label, barRight - text.measureText(label), baseline - h * 0.02f, text)
        }
        data.fuelPercent?.let { fuel ->
            val left = barRight - barWidth - h * 0.20f
            val iconSize = h * 0.05f
            drawPump(canvas, left - iconSize * 1.25f, barTop - iconSize * 0.35f, iconSize)
            rect.set(left, barTop, left + barWidth, barTop + barHeight)
            canvas.drawRoundRect(rect, barHeight, barHeight, track)
            rect.right = left + barWidth * (fuel / 100f).coerceIn(0.03f, 1f)
            canvas.drawRoundRect(rect, barHeight, barHeight, if (fuel < 15f) low else fill)
        }

        // Gear badge, right of the speed.
        data.gear?.let { gear ->
            text.textSize = h * 0.055f
            text.textAlign = Paint.Align.CENTER
            val boxH = h * 0.075f
            val boxW = maxOf(boxH, text.measureText(gear) + boxH * 0.5f)
            val left = cx + h * 0.20f
            val top = baseline - boxH * 0.95f
            stroke.strokeWidth = h * 0.005f
            rect.set(left, top, left + boxW, top + boxH)
            canvas.drawRoundRect(rect, boxH * 0.24f, boxH * 0.24f, stroke)
            canvas.drawText(gear, left + boxW / 2f, top + boxH * 0.76f, text)
        }

        // Outside temperature, top right.
        data.outsideTempC?.let { temp ->
            shadow.textSize = h * 0.05f
            canvas.drawText(String.format(Locale.getDefault(), "%d°C", temp.roundToInt()), w - h * 0.08f, h * 0.10f, shadow)
        }
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
