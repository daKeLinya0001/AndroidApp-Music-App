package app.endlessmusic.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max

/**
 * 壁纸取景框：图片/视频首帧铺满取景框，双指缩放、单指拖动选范围。
 * 取景框之外压暗，确认时把框内区域换算成 0–1 的归一化裁剪矩形。
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private val bitmapRect = RectF()
    private val frameRect = RectF()
    private var minScale = 1f

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val dimPaint = Paint().apply { color = 0x8C000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.WHITE
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val current = currentScale()
                if (current <= 0f) return false
                val target = (current * detector.scaleFactor).coerceIn(minScale, minScale * 8f)
                val factor = target / current
                imageMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                constrain()
                invalidate()
                return true
            }
        }
    )

    fun setBitmap(source: Bitmap) {
        bitmap = source
        if (width > 0) fit()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = dp(14f)
        frameRect.set(
            paddingLeft + inset,
            paddingTop + inset,
            w - paddingRight - inset,
            h - paddingBottom - inset
        )
        fit()
    }

    private fun fit() {
        val source = bitmap ?: return
        if (width == 0 || frameRect.isEmpty) return
        val scale = max(frameRect.width() / source.width, frameRect.height() / source.height)
        minScale = scale
        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(
            frameRect.centerX() - source.width * scale / 2f,
            frameRect.centerY() - source.height * scale / 2f
        )
        constrain()
        invalidate()
    }

    private fun currentScale(): Float {
        val values = FloatArray(9)
        imageMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** 保证图片始终盖满取景框 */
    private fun constrain() {
        val source = bitmap ?: return
        bitmapRect.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        val mapped = RectF()
        imageMatrix.mapRect(mapped, bitmapRect)
        var dx = 0f
        var dy = 0f
        if (mapped.left > frameRect.left) dx = frameRect.left - mapped.left
        if (mapped.right < frameRect.right) dx = frameRect.right - mapped.right
        if (mapped.top > frameRect.top) dy = frameRect.top - mapped.top
        if (mapped.bottom < frameRect.bottom) dy = frameRect.bottom - mapped.bottom
        if (dx != 0f || dy != 0f) imageMatrix.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        canvas.drawBitmap(source, imageMatrix, imagePaint)

        val radius = dp(16f)
        val dim = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(frameRect, radius, radius, Path.Direction.CW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(dim, dimPaint)
        canvas.drawRoundRect(frameRect, radius, radius, borderPaint)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragging) {
                    imageMatrix.postTranslate(event.x - lastX, event.y - lastY)
                    lastX = event.x
                    lastY = event.y
                    constrain()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** 取景框对应的原图区域，返回 0–1 的归一化矩形 */
    fun cropRect(): RectF {
        val source = bitmap ?: return RectF(0f, 0f, 1f, 1f)
        val inverse = Matrix()
        if (!imageMatrix.invert(inverse)) return RectF(0f, 0f, 1f, 1f)
        val mapped = RectF()
        inverse.mapRect(mapped, frameRect)
        val left = (mapped.left / source.width).coerceIn(0f, 1f)
        val top = (mapped.top / source.height).coerceIn(0f, 1f)
        val right = (mapped.right / source.width).coerceIn(0f, 1f)
        val bottom = (mapped.bottom / source.height).coerceIn(0f, 1f)
        if (right - left < 0.02f || bottom - top < 0.02f) return RectF(0f, 0f, 1f, 1f)
        return RectF(left, top, right, bottom)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
