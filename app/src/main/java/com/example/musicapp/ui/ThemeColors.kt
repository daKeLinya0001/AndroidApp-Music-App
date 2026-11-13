package com.example.musicapp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.musicapp.R
import com.example.musicapp.play.PlayerSettings
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * 运行时着色：把「强调色」「文字颜色」应用到整棵视图树。
 *
 * 布局颜色来自 @color 资源，运行时无法改资源值，因此这里把默认色（以及上一次应用的颜色）
 * 映射到新的自定义色。传入 previousAccent / previousText 后多次切换颜色也能正确重着色，
 * 不需要重建页面。
 */
object ThemeColors {

    fun accent(settings: PlayerSettings, context: Context): Int =
        ensureVisible(accentFor(settings.accentColor, context), context)

    fun accentLight(settings: PlayerSettings, context: Context): Int =
        lighten(accent(settings, context), 0.22f)

    fun textPrimary(settings: PlayerSettings, context: Context): Int =
        ensureVisible(textFor(settings.textColor, context), context)

    fun textSecondary(settings: PlayerSettings, context: Context): Int =
        withAlpha(textPrimary(settings, context), 0.72f)

    fun textTertiary(settings: PlayerSettings, context: Context): Int =
        withAlpha(textPrimary(settings, context), 0.52f)

    private fun accentFor(value: Int, context: Context): Int =
        if (value != 0) value else ContextCompat.getColor(context, R.color.primary)

    private fun textFor(value: Int, context: Context): Int =
        if (value != 0) value else ContextCompat.getColor(context, R.color.text_primary)

    /** 深色背景上黑色不可见、浅色背景上白色不可见时，自动回退到主题色 */
    private fun ensureVisible(color: Int, context: Context): Int {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val luminance = ColorUtils.calculateLuminance(color)
        if (night && luminance < 0.03f) return Color.WHITE
        if (!night && luminance > 0.93f) return Color.BLACK
        return color
    }

    fun apply(
        root: View,
        settings: PlayerSettings,
        context: Context,
        previousAccent: Int = 0,
        previousText: Int = 0
    ) {
        val accent = accent(settings, context)
        val accentLight = lighten(accent, 0.22f)
        // 强调色上的前景色：浅色强调色（如深色主题的白色）用黑字/黑图标，深色强调色用白色
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.55f) Color.BLACK else Color.WHITE
        val textPrimary = textPrimary(settings, context)
        val textSecondary = withAlpha(textPrimary, 0.72f)
        val textTertiary = withAlpha(textPrimary, 0.52f)

        val defaultAccent = ContextCompat.getColor(context, R.color.primary)
        val defaultAccentLight = ContextCompat.getColor(context, R.color.primary_light)
        val defaultTextPrimary = ContextCompat.getColor(context, R.color.text_primary)
        val defaultTextSecondary = ContextCompat.getColor(context, R.color.text_secondary)
        val defaultTextTertiary = ContextCompat.getColor(context, R.color.text_tertiary)

        val previousAccentColor = accentFor(previousAccent, context)
        val previousAccentLight = lighten(previousAccentColor, 0.22f)
        val previousTextPrimary = textFor(previousText, context)
        val previousTextSecondary = withAlpha(previousTextPrimary, 0.72f)
        val previousTextTertiary = withAlpha(previousTextPrimary, 0.52f)

        val mapping = mutableListOf<Pair<Int, Int>>()
        fun add(from: Int, to: Int) {
            if (from != to && mapping.none { it.first == from }) mapping.add(from to to)
        }
        add(defaultAccent, accent)
        add(defaultAccentLight, accentLight)
        add(previousAccentColor, accent)
        add(previousAccentLight, accentLight)
        add(defaultTextPrimary, textPrimary)
        add(defaultTextSecondary, textSecondary)
        add(defaultTextTertiary, textTertiary)
        add(previousTextPrimary, textPrimary)
        add(previousTextSecondary, textSecondary)
        add(previousTextTertiary, textTertiary)

        fun map(color: Int): Int {
            for (pair in mapping) {
                if (pair.first == color) return pair.second
            }
            return color
        }

        fun visit(view: View) {
            when (view) {
                is MaterialButton -> {
                    // 只给「有实际填充色」的按钮换色；文字按钮（透明背景）保持透明
                    val currentFill = view.backgroundTintList?.defaultColor ?: 0
                    if (view.id != R.id.btn_reset_settings && Color.alpha(currentFill) > 10) {
                        view.backgroundTintList = ColorStateList.valueOf(accent)
                        view.setTextColor(onAccent)
                        if (view.icon != null) {
                            view.iconTint = ColorStateList.valueOf(onAccent)
                        }
                    }
                }
                is SeekBar -> {
                    view.progressTintList = ColorStateList.valueOf(accent)
                    // 滑块用对比色，避免深色主题下白轨道 + 白圆点糊成一片
                    view.thumbTintList = ColorStateList.valueOf(onAccent)
                }
                is Slider -> {
                    view.trackActiveTintList = ColorStateList.valueOf(accent)
                    view.thumbTintList = ColorStateList.valueOf(onAccent)
                    view.haloTintList = ColorStateList.valueOf(withAlpha(accent, 0.18f))
                    view.tickActiveTintList = ColorStateList.valueOf(accent)
                }
                is MaterialSwitch -> {
                    // 轨道跟随强调色；滑块用「与强调色相反的对比色」，
                    // 否则深色主题下强调色是白色时，轨道 + 滑块会糊成一整块纯白
                    val checkedThumb = onAccent
                    val uncheckedThumb = textTertiary
                    view.thumbTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(checkedThumb, uncheckedThumb)
                    )
                    view.trackTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(accent, withAlpha(defaultTextTertiary, 0.35f))
                    )
                }
                is RadioButton -> view.buttonTintList = ColorStateList.valueOf(accent)
                is TextView -> {
                    val mapped = map(view.currentTextColor)
                    if (mapped != view.currentTextColor) view.setTextColor(mapped)
                }
            }

            if (view is ImageView) {
                val current = view.imageTintList?.defaultColor
                if (current != null) {
                    val mapped = map(current)
                    if (mapped != current) {
                        view.imageTintList = ColorStateList.valueOf(mapped)
                    }
                }
            }

            when (view.id) {
                R.id.btn_play, R.id.ex_btn_play -> {
                    view.backgroundTintList = ColorStateList.valueOf(accent)
                    (view as? ImageView)?.imageTintList = ColorStateList.valueOf(onAccent)
                }
                R.id.tv_count -> {
                    view.backgroundTintList = ColorStateList.valueOf(withAlpha(accentLight, 0.18f))
                }
                R.id.tv_repeat_badge, R.id.ex_repeat_badge -> {
                    view.backgroundTintList = ColorStateList.valueOf(accent)
                    (view as? TextView)?.setTextColor(onAccent)
                }
                R.id.btn_accent_default, R.id.btn_text_default -> {
                    (view as? TextView)?.setTextColor(accent)
                }
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }

        visit(root)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(color, a)
    }

    private fun lighten(color: Int, ratio: Float): Int {
        val red = (Color.red(color) + (255 - Color.red(color)) * ratio).toInt()
        val green = (Color.green(color) + (255 - Color.green(color)) * ratio).toInt()
        val blue = (Color.blue(color) + (255 - Color.blue(color)) * ratio).toInt()
        return Color.rgb(red, green, blue)
    }
}
