package com.example.musicapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 32 根音频频谱柱，视觉规格对齐插件 CSS：
 *
 * - 柱身三色渐变：顶部 = 颜色混白 38%，中部 = 原色，底部 = 颜色混 #0b1220 22%
 * - 只有顶部两角是圆角（对应 border-radius: 999px 999px 0 0）
 * - 每根柱子顶端持续冒出小粒子（颜色取自柱色），随音量增强而变多
 * - 可选底部蓝色氛围渐变（对应 workspace 变体的 linear-gradient）
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var barColor: Int = DEFAULT_COLOR
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    var barOpacity: Int = 64
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var maxBarHeightDp: Int = 200
        set(value) {
            field = value.coerceIn(0, 300)
            shader = null
            invalidate()
        }

    var speed: Int = 35
        set(value) {
            field = value.coerceIn(10, 100)
        }

    /** 底部蓝色氛围渐变，对应插件的 workspace 频谱背景 */
    var ambientGlow: Boolean = false
        set(value) {
            field = value
            glowShader = null
            invalidate()
        }

    /** 柱子数量，默认 32；全屏径向模式用 64 根 */
    var barCount: Int = BAR_COUNT
        set(value) {
            val count = value.coerceIn(MIN_BAR_COUNT, MAX_BAR_COUNT)
            if (field == count) return
            field = count
            levels = FloatArray(count) { IDLE_LEVEL }
            rawLevels = FloatArray(count) { 0f }
            barAvgs = FloatArray(count) { 0.05f }
            sparks = Array(count * SPARKS_PER_BAR) { BarSpark() }
            radialShader = null
            startLoop()
            invalidate()
        }

    /** 径向模式：32 根柱子以视图中心为圆心，向外辐射环绕（全屏播放器使用） */
    var radialMode: Boolean = false
        set(value) {
            field = value
            radialShader = null
            invalidate()
        }

    /** 径向模式下柱子的起点半径（dp），应略大于唱片半径 */
    var radialInnerRadiusDp: Float = 112f
        set(value) {
            field = value
            radialShader = null
            invalidate()
        }

    /** 全屏弧线的震动幅度：0 = 平滑圆环，100 = 最大起伏 */
    var arcAmplitude: Int = 80
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    /** 是否绘制弧线内部的填充与放射连接线；唱片隐藏时应关闭，否则会变成一块灰饼 */
    var showRingDecoration: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            startLoop()
            invalidate()
        }

    /** 返回 32 个 0..1 的能量值；返回 null 时使用模拟律动 */
    var provider: (() -> FloatArray?)? = null

    /** 低频能量，用于让唱片随音乐轻微脉动 */
    var onBass: ((Float) -> Unit)? = null

    /** 柱顶粒子：每个粒子记录位置、速度与剩余寿命 */
    private class BarSpark {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var radius = 1f
    }

    private var levels = FloatArray(BAR_COUNT) { IDLE_LEVEL }
    private var rawLevels = FloatArray(BAR_COUNT) { 0f }
    private var barAvgs = FloatArray(BAR_COUNT) { 0.05f }
    private var sparks = Array(BAR_COUNT * SPARKS_PER_BAR) { BarSpark() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val barPath = Path()
    private val radii = FloatArray(8)

    private var shader: LinearGradient? = null
    private var shaderHeight = 0f
    private var shaderMaxHeight = 0f
    private var shaderColor = 0

    private var glowShader: LinearGradient? = null
    private var glowHeight = 0f

    private var radialShader: RadialGradient? = null
    private var radialKey = 0f
    private val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val radialRect = RectF()
    private val ringPath = Path()
    private val radialFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var radialFillShader: RadialGradient? = null
    private var radialFillKey = 0f
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringX = FloatArray(MAX_BAR_COUNT)
    private val ringY = FloatArray(MAX_BAR_COUNT)

    private var running = false
    private var startNanos = 0L
    private var lastNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (startNanos == 0L) {
                startNanos = frameTimeNanos
                lastNanos = frameTimeNanos
            }
            val seconds = (frameTimeNanos - startNanos) / 1_000_000_000f
            val delta = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = frameTimeNanos
            step(seconds, delta)
            invalidate()
            if (shouldAnimate()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startLoop() else invalidate()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        running = false
        onBass?.invoke(0f)
        super.onDetachedFromWindow()
    }

    private fun startLoop() {
        if (running) return
        running = true
        startNanos = 0L
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun shouldAnimate(): Boolean {
        if (isPlaying) return true
        for (level in levels) {
            if (abs(level - IDLE_LEVEL) > 0.01f) return true
        }
        for (spark in sparks) {
            if (spark.life > 0f) return true
        }
        return false
    }

    private fun step(seconds: Float, delta: Float) {
        // 与收起状态一致的响应速度，不再额外加速
        val easing = speed / 100f
        val data = provider?.invoke()
        val count = barCount

        if (isPlaying) {
            for (index in 0 until count) {
                val value = if (data != null && data.isNotEmpty()) {
                    sampleData(data, index)
                } else {
                    synthetic(seconds, index)
                }
                // 径向模式保留原始幅度（可能大于 1），交给逐柱峰值归一化
                rawLevels[index] = if (radialMode) max(0f, value) else value.coerceIn(0f, 1f)
            }
            if (radialMode) normalizeRadial(count, delta)
        }

        for (index in 0 until count) {
            val target = if (isPlaying) rawLevels[index] else IDLE_LEVEL
            levels[index] += (target - levels[index]) * easing
        }
        updateSparks(delta)
        val bass = (
            levels.getOrElse(0) { 0f } + levels.getOrElse(1) { 0f } +
                levels.getOrElse(2) { 0f } + levels.getOrElse(3) { 0f }
            ) / 4f
        onBass?.invoke(bass)
    }

    /** 线性插值采样，64 根柱子之间过渡平滑而不是两两相同 */
    private fun sampleData(data: FloatArray, index: Int): Float {
        if (data.isEmpty()) return 0f
        if (barCount <= 1) return data[0]
        val position = index * (data.size - 1).toFloat() / (barCount - 1)
        val lower = position.toInt().coerceIn(0, data.size - 1)
        val upper = min(lower + 1, data.size - 1)
        val fraction = position - lower
        return data[lower] * (1f - fraction) + data[upper] * fraction
    }

    /**
     * 径向归一化：按每根柱子的慢速平均值计算，并把偏离均值的变化放大，
     * 这样曲线能明显起落，而不是一直贴着平均半径。
     */
    private fun normalizeRadial(count: Int, delta: Float) {
        val follow = (delta * 3.5f).coerceIn(0f, 1f)
        val lastIndex = max(1, count - 1)
        for (index in 0 until count) {
            val raw = rawLevels[index]
            val avg = barAvgs[index] + (raw - barAvgs[index]) * follow
            barAvgs[index] = max(avg, 0.02f)

            val base = (raw / (barAvgs[index] * 1.8f)).coerceIn(0f, 1f)
            // 以 0.5 为中心放大 ±0.95 的偏离，让起伏肉眼可见
            val boosted = (0.5f + (base - 0.5f) * 1.9f).coerceIn(0f, 1f)
            val tilt = 0.9f + 0.3f * (index.toFloat() / lastIndex)
            rawLevels[index] = (boosted * tilt).coerceIn(0f, 1f)
        }
    }

    private fun updateSparks(delta: Float) {
        if (radialMode) return
        for (spark in sparks) {
            if (spark.life <= 0f) continue
            spark.life -= delta
            spark.x += spark.vx * delta
            spark.y += spark.vy * delta
            // 轻微减速，粒子上升到一定高度后变慢
            spark.vy += dp(10f) * delta
        }
        if (!isPlaying || delta <= 0f) return

        val width = width.toFloat()
        val height = height.toFloat()
        val maxHeight = min(dp(maxBarHeightDp.toFloat()), height)
        if (width <= 0f || height <= 0f || maxHeight <= 0f) return

        val gap = width * 0.008f
        val barWidth = (width - gap * (barCount + 1)) / barCount
        if (barWidth <= 0f) return

        for (index in 0 until barCount) {
            val level = levels[index]
            if (level < MIN_SPARK_LEVEL) continue
            // 音量越大，冒出粒子的概率越高
            val rate = level * SPARK_RATE
            if (Random.nextFloat() >= rate * delta) continue
            val centerX = gap + index * (barWidth + gap) + barWidth / 2f
            val topY = height - max(dp(2f), level * maxHeight)
            val spark = sparks.firstOrNull { it.life <= 0f } ?: continue
            spark.x = centerX + (Random.nextFloat() - 0.5f) * barWidth
            spark.y = topY
            spark.vx = (Random.nextFloat() - 0.5f) * dp(6f)
            spark.vy = -(dp(16f) + Random.nextFloat() * dp(24f))
            spark.maxLife = 0.6f + Random.nextFloat() * 0.6f
            spark.life = spark.maxLife
            spark.radius = dp(0.7f + Random.nextFloat() * 1.2f)
        }
    }

    private fun synthetic(seconds: Float, index: Int): Float {
        val base = 0.18f + 0.62f * abs(sin(seconds * 1.7f + index * 0.55f))
        val modulation = 0.55f + 0.45f * sin(seconds * 0.9f + index * 0.31f)
        return (base * modulation).coerceIn(0.05f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        if (radialMode) {
            drawRadial(canvas, width, height)
            return
        }

        drawAmbientGlow(canvas, width, height)

        val maxHeight = min(dp(maxBarHeightDp.toFloat()), height)
        if (maxHeight <= 0f || barOpacity <= 0) return

        val gap = width * 0.008f
        val barWidth = (width - gap * (barCount + 1)) / barCount
        if (barWidth <= 0f) return

        ensureShader(height, maxHeight)
        paint.shader = shader
        paint.alpha = barOpacity * 255 / 100

        var left = gap
        for (index in 0 until barCount) {
            val level = levels[index].coerceIn(0f, 1f)
            val barHeight = max(dp(2f), level * maxHeight)
            rect.set(left, height - barHeight, left + barWidth, height)

            // 只有顶部圆角，底部贴边（插件 border-radius: 999px 999px 0 0）
            val radius = min(barWidth / 2f, barHeight / 2f)
            radii[0] = radius
            radii[1] = radius
            radii[2] = radius
            radii[3] = radius
            radii[4] = 0f
            radii[5] = 0f
            radii[6] = 0f
            radii[7] = 0f
            barPath.reset()
            barPath.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.drawPath(barPath, paint)

            left += barWidth + gap
        }

        drawSparks(canvas)
    }

    /**
     * 径向模式：把 32/64 个能量点连成一条首尾相接的平滑曲线（闭合 Catmull-Rom），
     * 半径随能量起伏，形成环绕唱片律动的光环。
     */
    private fun drawRadial(canvas: Canvas, width: Float, height: Float) {
        val halfSize = min(width, height) / 2f
        val inner = dp(radialInnerRadiusDp)
        val available = halfSize - inner - dp(2f)
        if (available <= 0f || barOpacity <= 0) return
        val maxLength = min(dp(maxBarHeightDp.toFloat()), available)
        if (maxLength <= 0f) return

        val cx = width / 2f
        val cy = height / 2f
        val outer = inner + maxLength
        val cacheKey = width * 1000f + outer + barColor
        if (radialShader == null || radialKey != cacheKey) {
            val innerRatio = inner / outer
            radialShader = RadialGradient(
                cx,
                cy,
                outer,
                intArrayOf(
                    blend(barColor, Color.WHITE, 0.35f),
                    barColor,
                    blend(barColor, 0xFF0B1220.toInt(), 0.30f)
                ),
                floatArrayOf(innerRatio, (innerRatio + 1f) / 2f, 1f),
                Shader.TileMode.CLAMP
            )
            radialKey = cacheKey
        }

        val count = barCount
        val stepAngle = (2f * PI / count).toFloat()
        // 幅度系数：0 时是一条平滑圆环，100 时用满可用宽度（留出描边余量）
        val amplitude = (maxLength - dp(4f)) * (arcAmplitude / 100f)
        for (index in 0 until count) {
            val prev = (index - 1 + count) % count
            val next = (index + 1) % count
            // 相邻三点做一次加权平滑，避免曲线出现尖锐毛刺
            val level = (
                levels[prev] * 0.25f + levels[index] * 0.5f + levels[next] * 0.25f
                ).coerceIn(0f, 1f)
            val radius = inner + dp(2f) + amplitude * level
            val angle = -PI.toFloat() / 2f + index * stepAngle
            ringX[index] = cx + cos(angle) * radius
            ringY[index] = cy + sin(angle) * radius
        }

        // 闭合曲线：每段用三次贝塞尔连接，控制点取自前后相邻点（Catmull-Rom 推导）
        ringPath.reset()
        ringPath.moveTo(ringX[0], ringY[0])
        for (index in 0 until count) {
            val prev = (index - 1 + count) % count
            val next = (index + 1) % count
            val next2 = (index + 2) % count
            val c1x = ringX[index] + (ringX[next] - ringX[prev]) / 6f
            val c1y = ringY[index] + (ringY[next] - ringY[prev]) / 6f
            val c2x = ringX[next] - (ringX[next2] - ringX[index]) / 6f
            val c2y = ringY[next] - (ringY[next2] - ringY[index]) / 6f
            ringPath.cubicTo(c1x, c1y, c2x, c2y, ringX[next], ringY[next])
        }
        ringPath.close()

        if (showRingDecoration) {
            // 曲线内部半透明填充，颜色跟随频谱柱：靠近唱片略浓，靠近曲线边缘渐淡
            val fillKey = outer * 1000f + barColor
            if (radialFillShader == null || radialFillKey != fillKey) {
                val red = Color.red(barColor)
                val green = Color.green(barColor)
                val blue = Color.blue(barColor)
                radialFillShader = RadialGradient(
                    cx,
                    cy,
                    outer,
                    intArrayOf(
                        Color.argb(130, red, green, blue),
                        Color.argb(36, red, green, blue)
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                radialFillKey = fillKey
            }
            radialFillPaint.shader = radialFillShader
            radialFillPaint.alpha = barOpacity * 255 / 100
            canvas.drawPath(ringPath, radialFillPaint)

            // 从唱片边缘连到曲线的放射细线，让曲线看起来"长"在唱片上而不是飘在空中
            connectorPaint.color = barColor
            connectorPaint.strokeWidth = dp(1f)
            for (index in 0 until count) {
                val angle = -PI.toFloat() / 2f + index * stepAngle
                val startX = cx + cos(angle) * (inner + dp(1.5f))
                val startY = cy + sin(angle) * (inner + dp(1.5f))
                val distance = hypot(ringX[index] - startX, ringY[index] - startY)
                // 线越长越淡，避免外扩时出现生硬的色块
                val alpha = (barOpacity * 0.32f * 255f / 100f * (1f - distance / (maxLength * 1.6f))
                    .coerceIn(0.25f, 1f)).toInt().coerceIn(0, 255)
                connectorPaint.alpha = alpha
                canvas.drawLine(startX, startY, ringX[index], ringY[index], connectorPaint)
            }
        }

        radialPaint.shader = radialShader
        radialPaint.alpha = barOpacity * 255 / 100
        radialPaint.strokeWidth = dp(4f)
        radialPaint.strokeCap = Paint.Cap.ROUND
        radialPaint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(ringPath, radialPaint)
    }

    private fun drawSparks(canvas: Canvas) {
        if (barOpacity <= 0) return
        sparkPaint.color = blend(barColor, Color.WHITE, 0.25f)
        sparkHaloPaint.color = barColor
        for (spark in sparks) {
            if (spark.life <= 0f) continue
            val progress = (spark.life / spark.maxLife).coerceIn(0f, 1f)
            val alpha = (progress * 0.9f * barOpacity / 100f * 255f).toInt().coerceIn(0, 255)
            if (alpha <= 3) continue
            sparkHaloPaint.alpha = (alpha * 0.30f).toInt().coerceIn(0, 255)
            canvas.drawCircle(spark.x, spark.y, spark.radius * 2.6f, sparkHaloPaint)
            sparkPaint.alpha = alpha
            canvas.drawCircle(spark.x, spark.y, spark.radius, sparkPaint)
        }
    }

    private fun drawAmbientGlow(canvas: Canvas, width: Float, height: Float) {
        if (!ambientGlow) return
        if (glowShader == null || glowHeight != height) {
            glowShader = LinearGradient(
                0f, 0f, 0f, height,
                Color.TRANSPARENT,
                Color.argb(15, 37, 99, 235),
                Shader.TileMode.CLAMP
            )
            glowHeight = height
        }
        glowPaint.shader = glowShader
        canvas.drawRect(0f, 0f, width, height, glowPaint)
    }

    private fun ensureShader(height: Float, maxHeight: Float) {
        if (shader != null &&
            shaderHeight == height &&
            shaderMaxHeight == maxHeight &&
            shaderColor == barColor
        ) {
            return
        }
        shader = LinearGradient(
            0f,
            height - maxHeight,
            0f,
            height,
            intArrayOf(
                blend(barColor, Color.WHITE, 0.38f),
                barColor,
                blend(barColor, 0xFF0B1220.toInt(), 0.22f)
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        shaderHeight = height
        shaderMaxHeight = maxHeight
        shaderColor = barColor
    }

    /** 按比例混合两个颜色，对应 CSS 的 color-mix() */
    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val red = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val green = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
        val blue = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.rgb(red, green, blue)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val BAR_COUNT = 32
        const val MIN_BAR_COUNT = 8
        const val MAX_BAR_COUNT = 96
        const val IDLE_LEVEL = 0.05f
        const val DEFAULT_COLOR = 0xFF4F8CFF.toInt()
        const val SPARKS_PER_BAR = 3
        const val MIN_SPARK_LEVEL = 0.10f
        const val SPARK_RATE = 16f
    }
}
