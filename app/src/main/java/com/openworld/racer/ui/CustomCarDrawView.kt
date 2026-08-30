package com.openworld.racer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CustomCarDrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val numPoints = 16
    val profilePoints = FloatArray(numPoints) { 0.5f }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2D2D44.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555577.toInt()
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5000E5FF.toInt()
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFEA00.toInt()
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
    }

    init {
        applyPreset("SUPERCAR")
    }

    fun applyPreset(preset: String) {
        val presetData = when (preset) {
            "SUPERCAR" -> floatArrayOf(
                0.20f, 0.35f, 0.45f, 0.65f, 0.90f, 0.95f, 0.90f, 0.85f,
                0.75f, 0.70f, 0.55f, 0.45f, 0.40f, 0.35f, 0.30f, 0.25f
            )
            "CYBERTRUCK" -> floatArrayOf(
                0.35f, 0.45f, 0.55f, 0.75f, 0.95f, 1.00f, 0.90f, 0.80f,
                0.70f, 0.60f, 0.50f, 0.45f, 0.40f, 0.40f, 0.40f, 0.40f
            )
            "SUV" -> floatArrayOf(
                0.40f, 0.55f, 0.70f, 0.90f, 0.95f, 0.95f, 0.95f, 0.95f,
                0.95f, 0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.60f, 0.45f
            )
            "FORMULA" -> floatArrayOf(
                0.15f, 0.20f, 0.25f, 0.40f, 0.85f, 0.90f, 0.50f, 0.45f,
                0.40f, 0.35f, 0.35f, 0.35f, 0.40f, 0.70f, 0.85f, 0.90f
            )
            else -> floatArrayOf(
                0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.8f, 0.8f,
                0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.3f, 0.3f, 0.3f
            )
        }
        for (i in 0 until numPoints) {
            profilePoints[i] = presetData[i]
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 60f
        val drawW = w - padding * 2f
        val drawH = h - padding * 2.5f

        // Draw Grid Guidelines
        canvas.drawLine(padding, padding, padding + drawW, padding, gridPaint)
        canvas.drawLine(padding, padding + drawH / 2f, padding + drawW, padding + drawH / 2f, gridPaint)
        canvas.drawLine(padding, padding + drawH, padding + drawW, padding + drawH, gridPaint)

        // Draw 2 Wheels (Front & Rear)
        val wheelRadius = drawH * 0.16f
        val wheelY = padding + drawH + wheelRadius * 0.4f
        canvas.drawCircle(padding + drawW * 0.22f, wheelY, wheelRadius, wheelPaint)
        canvas.drawCircle(padding + drawW * 0.78f, wheelY, wheelRadius, wheelPaint)

        // Draw Front & Rear labels
        canvas.drawText("FRONT (FRONT BUMPER)", padding, padding - 15f, textPaint)
        canvas.drawText("REAR (REAR BUMPER)", padding + drawW - 320f, padding - 15f, textPaint)

        // Construct Custom Car Path
        val path = Path()
        val fillPath = Path()

        val stepX = drawW / (numPoints - 1)
        fillPath.moveTo(padding, padding + drawH)

        for (i in 0 until numPoints) {
            val px = padding + i * stepX
            val py = padding + drawH * (1.0f - profilePoints[i].coerceIn(0.1f, 1.0f))

            if (i == 0) {
                path.moveTo(px, py)
                fillPath.lineTo(px, py)
            } else {
                val prevX = padding + (i - 1) * stepX
                val prevY = padding + drawH * (1.0f - profilePoints[i - 1].coerceIn(0.1f, 1.0f))
                val cx1 = (prevX + px) / 2f
                path.cubicTo(cx1, prevY, cx1, py, px, py)
                fillPath.cubicTo(cx1, prevY, cx1, py, px, py)
            }
            // Draw Interactive Handle Points
            canvas.drawCircle(px, py, 12f, pointPaint)
        }

        fillPath.lineTo(padding + drawW, padding + drawH)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val padding = 60f
                val drawW = width.toFloat() - padding * 2f
                val drawH = height.toFloat() - padding * 2.5f

                val touchX = event.x.coerceIn(padding, padding + drawW)
                val touchY = event.y.coerceIn(padding, padding + drawH)

                val normalizedX = (touchX - padding) / drawW
                val normalizedY = 1.0f - ((touchY - padding) / drawH)

                val index = (normalizedX * (numPoints - 1)).toInt().coerceIn(0, numPoints - 1)
                profilePoints[index] = normalizedY.coerceIn(0.15f, 1.0f)

                // Smooth adjacent points slightly for natural car curves
                if (index > 0) profilePoints[index - 1] = (profilePoints[index - 1] * 0.6f + profilePoints[index] * 0.4f)
                if (index < numPoints - 1) profilePoints[index + 1] = (profilePoints[index + 1] * 0.6f + profilePoints[index] * 0.4f)

                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
