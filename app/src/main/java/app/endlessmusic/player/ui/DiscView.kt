package app.endlessmusic.player.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * 黑胶唱片。
 *
 * - [DiscStyle.BLACK]（默认）：黑色vinyl，带唱片纹路与中心孔
 * - [DiscStyle.COLOR]：按曲目名生成的双色渐变封面（对应插件的 hueFor）
 *
 * 播放时叠加双光环呼吸动画（68% / 86%，2.6s，第二圈延迟 0.35s）。
 */
class DiscView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DiscStyle { BLACK, COLOR }

    var style: DiscStyle = DiscStyle.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var hue: Float = 220f
        set(value) {
            field = value
            invalidate()
        }

    var hasTrack: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startBreathing() else stopBreathing()
        }

    /** 唱片内圈图片；为空时画白色内圈 */
    var labelBitmap: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val labelSrc = Rect()
    private val labelDst = RectF()
    private val clipPath = Path()

    private var breathPhase = 0f
    private var breathAnimator: ValueAnimator? = null

    private fun startBreathing() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 2600L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                breathPhase = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathing() {
        breathAnimator?.cancel()
        breathAnimator = null
        breathPhase = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - dp(1f)
        if (radius <= 0f) return

        val black = style == DiscStyle.BLACK
        val topColor: Int
        val bottomColor: Int
        when {
            black -> {
                topColor = Color.parseColor("#2E3037")
                bottomColor = Color.parseColor("#06070A")
            }
            hasTrack -> {
                topColor = Color.HSVToColor(floatArrayOf(hue % 360f, 0.68f, 0.80f))
                bottomColor = Color.HSVToColor(floatArrayOf((hue + 58f) % 360f, 0.62f, 0.42f))
            }
            else -> {
                topColor = Color.parseColor("#39465A")
                bottomColor = Color.parseColor("#222C3C")
            }
        }

        fillPaint.shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            topColor,
            bottomColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)

        drawInnerHighlight(canvas, cx, cy, radius, if (black) 58 else 46)

        // 唱片纹路
        strokePaint.color = Color.argb(if (black) 36 else 26, 255, 255, 255)
        strokePaint.strokeWidth = dp(1f)
        for (index in 1..5) {
            canvas.drawCircle(cx, cy, radius * (0.90f - index * 0.13f), strokePaint)
        }

        // 内圈：默认白色标贴，也可用自定义图片覆盖，更接近真实黑胶
        drawLabel(canvas, cx, cy, radius)

        // 外缘高光
        strokePaint.color = Color.argb(if (black) 96 else 70, 255, 255, 255)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, radius - dp(1f), strokePaint)

        // 双光环（播放时呼吸）
        drawBreathRing(canvas, cx, cy, radius, 0.68f, 0f)
        drawBreathRing(canvas, cx, cy, radius, 0.86f, RING_PHASE_DELAY)

        // 中心孔
        dotPaint.color = if (black) Color.parseColor("#0A0B0F") else Color.argb(220, 8, 12, 20)
        canvas.drawCircle(cx, cy, radius * 0.13f, dotPaint)
        strokePaint.color = Color.argb(if (black) 70 else 90, 255, 255, 255)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawCircle(cx, cy, radius * 0.13f, strokePaint)
    }

    /** 唱片内圈标贴：默认白色圆形，设置图片后按居中裁切绘制 */
    private fun drawLabel(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val labelRadius = radius * 0.40f
        val bitmap = labelBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            clipPath.reset()
            clipPath.addCircle(cx, cy, labelRadius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            val size = min(bitmap.width, bitmap.height)
            val left = (bitmap.width - size) / 2
            val top = (bitmap.height - size) / 2
            labelSrc.set(left, top, left + size, top + size)
            labelDst.set(cx - labelRadius, cy - labelRadius, cx + labelRadius, cy + labelRadius)
            canvas.drawBitmap(bitmap, labelSrc, labelDst, labelPaint)
            canvas.restore()
        } else {
            dotPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, labelRadius, dotPaint)
        }

        // 标贴边缘一圈极淡的暗色，避免纯白和黑胶之间过渡生硬
        strokePaint.color = Color.argb(46, 0, 0, 0)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, labelRadius, strokePaint)
    }

    private fun drawInnerHighlight(canvas: Canvas, cx: Float, cy: Float, radius: Float, alpha: Int) {
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        highlightPaint.shader = LinearGradient(
            cx,
            cy - radius,
            cx,
            cy - radius * 0.45f,
            Color.argb(alpha, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy, highlightPaint)
        canvas.restore()
    }

    private fun drawBreathRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        fraction: Float,
        phaseDelay: Float
    ) {
        val scale: Float
        val opacity: Float
        if (isPlaying) {
            val k = (1f - cos(breathPhase + phaseDelay)) / 2f
            scale = 1f + 0.035f * k
            opacity = 0.65f + 0.35f * k
        } else {
            scale = 1f
            opacity = 1f
        }
        strokePaint.color = Color.argb(
            (BASE_RING_ALPHA * opacity).toInt().coerceIn(0, 255),
            255,
            255,
            255
        )
        strokePaint.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, radius * fraction * scale, strokePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** 0.22 * 255 ≈ 56，对应 border-color rgba(255,255,255,0.22) */
        const val BASE_RING_ALPHA = 56f

        /** 第二圈光环延迟 0.35s，换算成 2.6s 周期下的相位 */
        const val RING_PHASE_DELAY = 0.35f / 2.6f * (2 * PI).toFloat()
    }
}
