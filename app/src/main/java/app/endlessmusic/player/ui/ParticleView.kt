package app.endlessmusic.player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 播放器卡片后方的环境特效层：
 *
 * - 底部径向光晕：随低频脉动，颜色跟随频谱柱
 * - 漂浮粒子：从卡片底部向上飘散，带柔和外发光，同样跟随频谱柱颜色
 *
 * 粒子大小由设置控制（0–100 映射到 0.7–4dp 半径），暂停时整体淡出并停止逐帧刷新。
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var particleColor: Int = DEFAULT_COLOR
        set(value) {
            if (field == value) return
            field = value
            glowShader = null
            invalidate()
        }

    var particleSize: Int = 50
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    /** 底部光晕大小，0–100，越大扩散范围越广 */
    var glowSize: Int = 50
        set(value) {
            field = value.coerceIn(0, 100)
            glowShader = null
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            startLoop()
        }

    /** 提供 32 段频谱能量，用于驱动光晕与粒子速度；返回 null 时退化为静态 */
    var provider: (() -> FloatArray?)? = null

    private class Particle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var radius = 1f
        var alpha = 0.4f
        var phase = 0f
        var life = 0f
        var maxLife = 1f
    }

    /** 粒子数量 10–100：越大画面越丰富，也更耗电；100 时优先保证视觉效果 */
    var particleCount: Int = DEFAULT_PARTICLE_COUNT
        set(value) {
            val clamped = value.coerceIn(MIN_PARTICLE_COUNT, MAX_PARTICLE_COUNT)
            if (field == clamped) return
            field = clamped
            particles = Array(clamped) { Particle() }
            seeded = false
            glowShader = null
            if (isPlaying) startLoop()
            invalidate()
        }

    private var particles = Array(DEFAULT_PARTICLE_COUNT) { Particle() }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var glowShader: RadialGradient? = null
    private var glowWidth = 0f
    private var glowHeight = 0f
    private var glowColor = 0
    private var glowRadius = 0f
    private var glowCenterX = 0f
    private var glowCenterY = 0f

    private var energy = 0f
    private var globalAlpha = 0f
    private var running = false
    private var seeded = false
    private var lastNanos = 0L

    // 唱片圆心模式：粒子与光晕从唱片向外散发（全屏播放器使用）
    private var discMode = false
    private var discX = 0f
    private var discY = 0f
    private var discRadius = 0f

    fun setDiscOrigin(enabled: Boolean, centerX: Float, centerY: Float, radius: Float) {
        val changed = discMode != enabled ||
            discX != centerX ||
            discY != centerY ||
            discRadius != radius
        if (!changed) return

        val wasDisc = discMode
        val offsetX = centerX - discX
        val offsetY = centerY - discY
        discMode = enabled
        discX = centerX
        discY = centerY
        discRadius = radius

        // 圆心平移时带着粒子一起平移，避免重新随机导致画面「抽搐」
        if (wasDisc && enabled) {
            for (particle in particles) {
                particle.x += offsetX
                particle.y += offsetY
            }
        }
        glowShader = null
        invalidate()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!seeded) {
                seed()
                seeded = true
                lastNanos = frameTimeNanos
            }
            val delta = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = frameTimeNanos
            step(delta)
            invalidate()
            if (isPlaying || globalAlpha > 0.02f) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startLoop()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        running = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 只重建光晕缓存；不重置粒子，否则展开动画期间每帧都会重新随机
        glowShader = null
        if (isPlaying) startLoop()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun seed() {
        for (particle in particles) {
            reset(particle, atBottom = false)
        }
        globalAlpha = if (isPlaying) 1f else 0f
    }

    private fun reset(particle: Particle, atBottom: Boolean) {
        val width = width.toFloat()
        val height = height.toFloat()
        particle.phase = Random.nextFloat() * 6.28f
        particle.alpha = 0.30f + Random.nextFloat() * 0.45f
        particle.radius = baseRadius() * (0.55f + Random.nextFloat() * 0.9f)

        if (discMode && discRadius > 0f) {
            val angle = Random.nextFloat() * (2 * PI).toFloat()
            val startRadius = discRadius * (1.02f + Random.nextFloat() * 0.18f)
            // 贴着唱片外圈缓慢扩散，飞太快会显得画面很乱
            val speed = dp(12f) + Random.nextFloat() * dp(26f)
            particle.x = discX + cos(angle) * startRadius
            particle.y = discY + sin(angle) * startRadius
            particle.vx = cos(angle) * speed
            particle.vy = sin(angle) * speed
            particle.alpha = 0.45f + Random.nextFloat() * 0.4f
            particle.maxLife = 0.9f + Random.nextFloat() * 0.9f
            particle.life = particle.maxLife
            return
        }

        particle.x = Random.nextFloat() * max(width, 1f)
        particle.y = if (atBottom) {
            height + Random.nextFloat() * dp(24f)
        } else {
            Random.nextFloat() * max(height, 1f)
        }
        particle.vx = (Random.nextFloat() - 0.5f) * dp(10f)
        particle.vy = -(dp(14f) + Random.nextFloat() * dp(34f))
        particle.life = Float.MAX_VALUE
        particle.maxLife = Float.MAX_VALUE
    }

    private fun baseRadius(): Float = dp(0.7f + particleSize / 100f * 3.3f)

    private fun step(delta: Float) {
        val target = if (isPlaying) 1f else 0f
        globalAlpha += (target - globalAlpha) * (delta * 5f)

        val spectrum = provider?.invoke()
        val bass = if (spectrum != null && spectrum.size >= 4) {
            (spectrum[0] + spectrum[1] + spectrum[2] + spectrum[3]) / 4f
        } else {
            0f
        }
        energy += (bass.coerceIn(0f, 1f) - energy) * (delta * 6f)

        if (width <= 0 || height <= 0) return
        for (particle in particles) {
            particle.phase += delta * 1.4f
            if (discMode) {
                particle.life -= delta
                particle.x += particle.vx * delta
                particle.y += particle.vy * delta
            } else {
                particle.x += (particle.vx + sin(particle.phase) * dp(8f)) * delta
                particle.y += particle.vy * delta * (1f + energy * 1.2f)
            }
            val outOfBounds = particle.y < -dp(24f) ||
                particle.y > height + dp(24f) ||
                particle.x < -dp(48f) ||
                particle.x > width + dp(48f)
            if (outOfBounds || (discMode && particle.life <= 0f)) {
                reset(particle, atBottom = !discMode)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (globalAlpha <= 0.01f) return
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        drawGlow(canvas, width, height)

        corePaint.color = particleColor
        haloPaint.color = particleColor
        for (particle in particles) {
            val fade = if (discMode) {
                (particle.life / particle.maxLife).coerceIn(0f, 1f)
            } else {
                1f
            }
            val alpha = (particle.alpha * fade * globalAlpha * 255f).toInt().coerceIn(0, 255)
            if (alpha <= 4) continue
            haloPaint.alpha = (alpha * 0.22f).toInt().coerceIn(0, 255)
            canvas.drawCircle(particle.x, particle.y, particle.radius * 2.4f, haloPaint)
            corePaint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.radius, corePaint)
        }
    }

    private fun drawGlow(canvas: Canvas, width: Float, height: Float) {
        val centerX: Float
        val centerY: Float
        val radius: Float
        if (discMode && discRadius > 0f) {
            // 以唱片为圆心向外扩散
            centerX = discX
            centerY = discY
            radius = max(discRadius * (1.15f + glowSize / 100f * 1.5f), dp(90f))
        } else {
            // 0 → 卡片的 20% 宽，100 → 与卡片同宽；默认 50 与之前 0.62 倍宽的观感一致
            val factor = 0.2f + glowSize / 100f * 0.8f
            centerX = width / 2f
            centerY = height
            radius = max(width * factor, dp(60f))
        }

        if (glowShader == null ||
            glowWidth != width ||
            glowHeight != height ||
            glowColor != particleColor ||
            glowRadius != radius ||
            glowCenterX != centerX ||
            glowCenterY != centerY
        ) {
            glowShader = if (discMode && discRadius > 0f) {
                // 唱片内部保持透明，只在唱片外圈形成光环
                val innerRatio = (discRadius / radius).coerceIn(0.01f, 0.95f)
                RadialGradient(
                    centerX,
                    centerY,
                    radius,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        particleColor,
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, innerRatio, (innerRatio + 1f) / 2f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                RadialGradient(
                    centerX,
                    centerY,
                    radius,
                    intArrayOf(particleColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            glowWidth = width
            glowHeight = height
            glowColor = particleColor
            glowRadius = radius
            glowCenterX = centerX
            glowCenterY = centerY
        }
        glowPaint.shader = glowShader
        glowPaint.alpha = ((0.16f + energy * 0.18f) * globalAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width, height, glowPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val DEFAULT_PARTICLE_COUNT = 40
        const val MIN_PARTICLE_COUNT = 10
        const val MAX_PARTICLE_COUNT = 100
        const val DEFAULT_COLOR = 0xFF4F8CFF.toInt()
    }
}
