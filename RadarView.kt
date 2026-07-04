package com.projenefes.radar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Basit Canvas tabanlı radar görselleştirmesi.
 * - Arka plan: ızgara + tarama halkası (jiroskop açısına göre döner)
 * - Ön plan: gerçek mikrofon darbesi algılandığında beliren "çöp adam" figürü
 *
 * Not: Bu görsel gerçek jiroskop açısı ve gerçek mikrofon darbe olayı ile tetiklenir.
 * Konum kesinliği (kaç metre, hangi derinlik) YOKTUR — sadece "hangi yöne bakılırken
 * darbe duyuldu" bilgisini gösterir. Bu bir mesafe/derinlik ölçer değildir.
 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var azimuth = 0f
    private var pitch = 0f
    private var knockDetected = false
    private var pulsePhase = 0f

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2a3324")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val sweepPaint = Paint().apply {
        color = Color.parseColor("#7a5a18")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val figureFillPaint = Paint().apply {
        color = Color.parseColor("#ff3b30")
        isAntiAlias = true
    }
    private val figureStrokePaint = Paint().apply {
        color = Color.parseColor("#ff3b30")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#d8dcc8")
        textSize = 28f
        isAntiAlias = true
    }

    fun setAngle(az: Float, pt: Float) {
        azimuth = az
        pitch = pt
        invalidate()
    }

    fun setKnockDetected(detected: Boolean) {
        knockDetected = detected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.42f

        // Izgara halkaları
        for (r in 1..3) {
            canvas.drawCircle(cx, cy, radius * r / 3f, gridPaint)
        }
        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)

        // Tarama çizgisi (azimuth açısına göre)
        val angleRad = Math.toRadians(azimuth.toDouble())
        val sweepX = cx + (radius * sin(angleRad)).toFloat()
        val sweepY = cy - (radius * cos(angleRad)).toFloat()
        canvas.drawLine(cx, cy, sweepX, sweepY, sweepPaint)

        // Açı metni
        canvas.drawText("Yön: %.0f°  Eğim: %.0f°".format(azimuth, pitch), 20f, height - 30f, textPaint)

        // Darbe algılandığında: sinyalin geldiği tahmini yönde basit çöp adam
        if (knockDetected) {
            val fx = cx + (radius * 0.6f * sin(angleRad)).toFloat()
            val fy = cy - (radius * 0.6f * cos(angleRad)).toFloat()
            drawStickman(canvas, fx, fy)
            canvas.drawText("DARBE ALGILANDI — bu yönde", 20f, height - 65f, textPaint)
        }
    }

    private fun drawStickman(canvas: Canvas, x: Float, y: Float) {
        val headR = 22f
        canvas.drawCircle(x, y - 70f, headR, figureFillPaint)          // kafa
        canvas.drawLine(x, y - 48f, x, y, figureStrokePaint)           // gövde
        canvas.drawLine(x, y - 35f, x - 30f, y - 10f, figureStrokePaint) // sol kol
        canvas.drawLine(x, y - 35f, x + 30f, y - 10f, figureStrokePaint) // sağ kol
        canvas.drawLine(x, y, x - 25f, y + 45f, figureStrokePaint)     // sol bacak
        canvas.drawLine(x, y, x + 25f, y + 45f, figureStrokePaint)     // sağ bacak
    }
}
