package app.endlessmusic.player.play

import android.content.Context

/**
 * 播放器设置，对应插件里的 DEFAULT_SETTINGS，保存在 SharedPreferences。
 */
data class PlayerSettings(
    val spectrumEnabled: Boolean = true,
    val librarySpectrumEnabled: Boolean = true,
    val placementBottom: Boolean = false,
    val maxBarHeight: Int = 200,
    /** 频谱柱颜色，0 = 跟随主题（浅色黑 / 深色白） */
    val barColor: Int = 0,
    val barSpeed: Int = 35,
    val barOpacity: Int = 64,
    val cardOpacity: Int = 64,
    /** 播放器卡片后方频谱的虚化程度，0 = 关闭，100 = 最强 */
    val blurStrength: Int = 50,
    /** 主题：light（默认白色）/ dark / system */
    val themeMode: String = THEME_LIGHT,
    /** 唱片颜色：black（默认）/ color（跟随曲目渐变） */
    val discMode: String = DISC_BLACK,
    /** 隐藏黑胶唱片（主界面与全屏都不再显示） */
    val hideDisc: Boolean = false,
    /** 粒子特效铺满整屏（主界面与全屏一致） */
    val particleFullscreen: Boolean = false,
    /** 粒子数量 10–100 */
    val particleCount: Int = 40,
    /** 歌词整体提前/延后，单位毫秒（-5000..5000） */
    val lyricOffsetMs: Int = 0,
    /** 是否显示歌词（默认关闭，开启后才联网匹配） */
    val lyricsEnabled: Boolean = false,
    /** 唱片内圈图片路径，空 = 默认白色内圈 */
    val discLabelPath: String = "",
    /** 粒子与光晕特效 */
    val particlesEnabled: Boolean = true,
    val particleSize: Int = 50,
    /** true = 粒子颜色跟随频谱柱颜色 */
    val particleFollowBar: Boolean = true,
    val particleColor: Int = 0,
    /** 底部光晕大小，0–150 */
    val glowSize: Int = 50,
    /** 全屏弧线的震动幅度，0–100 */
    val arcAmplitude: Int = 80,
    /** 全屏弧线的虚化程度，0–100（0 = 清晰） */
    val arcBlur: Int = 0,
    /** 整体强调色（按钮、进度条等），0 = 跟随主题 */
    val accentColor: Int = 0,
    /** 文字颜色，0 = 跟随主题 */
    val textColor: Int = 0,
    /** 壁纸类型："" = 纯色默认，"image" = 图片，"video" = 视频 */
    val wallpaperType: String = "",
    /** 壁纸文件路径（应用私有目录） */
    val wallpaperPath: String = "",
    /** 壁纸取景框（0–1 归一化），默认整张图 */
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SPECTRUM, spectrumEnabled)
            .putBoolean(KEY_LIBRARY_SPECTRUM, librarySpectrumEnabled)
            .putBoolean(KEY_PLACEMENT_BOTTOM, placementBottom)
            .putInt(KEY_MAX_HEIGHT, maxBarHeight)
            .putInt(KEY_COLOR, barColor)
            .putInt(KEY_SPEED, barSpeed)
            .putInt(KEY_OPACITY, barOpacity)
            .putInt(KEY_CARD_OPACITY, cardOpacity)
            .putInt(KEY_BLUR, blurStrength)
            .putString(KEY_THEME, themeMode)
            .putString(KEY_DISC, discMode)
            .putBoolean(KEY_HIDE_DISC, hideDisc)
            .putBoolean(KEY_PARTICLE_FULLSCREEN, particleFullscreen)
            .putInt(KEY_PARTICLE_COUNT, particleCount)
            .putInt(KEY_LYRIC_OFFSET, lyricOffsetMs)
            .putBoolean(KEY_LYRICS_ENABLED, lyricsEnabled)
            .putString(KEY_DISC_LABEL, discLabelPath)
            .putBoolean(KEY_PARTICLES, particlesEnabled)
            .putInt(KEY_PARTICLE_SIZE, particleSize)
            .putBoolean(KEY_PARTICLE_FOLLOW, particleFollowBar)
            .putInt(KEY_PARTICLE_COLOR, particleColor)
            .putInt(KEY_GLOW_SIZE, glowSize)
            .putInt(KEY_ARC_AMPLITUDE, arcAmplitude)
            .putInt(KEY_ARC_BLUR, arcBlur)
            .putInt(KEY_ACCENT, accentColor)
            .putInt(KEY_TEXT_COLOR, textColor)
            .putString(KEY_WALLPAPER_TYPE, wallpaperType)
            .putString(KEY_WALLPAPER_PATH, wallpaperPath)
            .putFloat(KEY_CROP_LEFT, cropLeft)
            .putFloat(KEY_CROP_TOP, cropTop)
            .putFloat(KEY_CROP_RIGHT, cropRight)
            .putFloat(KEY_CROP_BOTTOM, cropBottom)
            // 同步写入，避免进程被立刻杀掉时设置丢失
            .commit()
    }

    companion object {
        /** 旧版本的默认蓝色；升级时把它当作「跟随主题」 */
        private const val LEGACY_DEFAULT_COLOR = 0xFF4F8CFF.toInt()

        private fun normaliseFollowThemeDefault(color: Int): Int =
            if (color == LEGACY_DEFAULT_COLOR) 0 else color

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"

        const val DISC_BLACK = "black"
        const val DISC_COLOR = "color"

        private const val PREFS = "music_settings"
        private const val KEY_SPECTRUM = "spectrumEnabled"
        private const val KEY_LIBRARY_SPECTRUM = "librarySpectrumEnabled"
        private const val KEY_PLACEMENT_BOTTOM = "placementBottom"
        private const val KEY_MAX_HEIGHT = "maxBarHeight"
        private const val KEY_COLOR = "barColor"
        private const val KEY_SPEED = "barSpeed"
        private const val KEY_OPACITY = "barOpacity"
        private const val KEY_CARD_OPACITY = "cardOpacity"
        private const val KEY_BLUR = "blurStrength"
        private const val KEY_THEME = "themeMode"
        private const val KEY_DISC = "discMode"
        private const val KEY_HIDE_DISC = "hideDisc"
        private const val KEY_PARTICLE_FULLSCREEN = "particleFullscreen"
        private const val KEY_PARTICLE_COUNT = "particleCount"
        private const val KEY_LYRIC_OFFSET = "lyricOffsetMs"
        private const val KEY_LYRICS_ENABLED = "lyricsEnabled"
        private const val KEY_DISC_LABEL = "discLabelPath"
        private const val KEY_PARTICLES = "particlesEnabled"
        private const val KEY_PARTICLE_SIZE = "particleSize"
        private const val KEY_PARTICLE_FOLLOW = "particleFollowBar"
        private const val KEY_PARTICLE_COLOR = "particleColor"
        private const val KEY_GLOW_SIZE = "glowSize"
        private const val KEY_ARC_AMPLITUDE = "arcAmplitude"
        private const val KEY_ARC_BLUR = "arcBlur"
        private const val KEY_ACCENT = "accentColor"
        private const val KEY_TEXT_COLOR = "textColor"
        private const val KEY_WALLPAPER_TYPE = "wallpaperType"
        private const val KEY_WALLPAPER_PATH = "wallpaperPath"
        private const val KEY_CROP_LEFT = "cropLeft"
        private const val KEY_CROP_TOP = "cropTop"
        private const val KEY_CROP_RIGHT = "cropRight"
        private const val KEY_CROP_BOTTOM = "cropBottom"

        val PRESET_COLORS = listOf(
            0xFF4F8CFF.toInt(),
            0xFFA855F7.toInt(),
            0xFF22C55E.toInt(),
            0xFFF59E0B.toInt(),
            0xFFEF4444.toInt(),
            0xFFEC4899.toInt()
        )

        fun load(context: Context): PlayerSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val theme = prefs.getString(KEY_THEME, THEME_LIGHT) ?: THEME_LIGHT
            val disc = prefs.getString(KEY_DISC, DISC_BLACK) ?: DISC_BLACK
            return PlayerSettings(
                spectrumEnabled = prefs.getBoolean(KEY_SPECTRUM, true),
                librarySpectrumEnabled = prefs.getBoolean(KEY_LIBRARY_SPECTRUM, true),
                placementBottom = prefs.getBoolean(KEY_PLACEMENT_BOTTOM, false),
                maxBarHeight = prefs.getInt(KEY_MAX_HEIGHT, 200).coerceIn(0, 300),
                barColor = normaliseFollowThemeDefault(prefs.getInt(KEY_COLOR, 0)),
                barSpeed = prefs.getInt(KEY_SPEED, 35).coerceIn(10, 100),
                barOpacity = prefs.getInt(KEY_OPACITY, 64).coerceIn(0, 100),
                cardOpacity = prefs.getInt(KEY_CARD_OPACITY, 64).coerceIn(0, 100),
                blurStrength = prefs.getInt(KEY_BLUR, 50).coerceIn(0, 100),
                themeMode = if (theme == THEME_DARK || theme == THEME_SYSTEM) theme else THEME_LIGHT,
                discMode = if (disc == DISC_COLOR) DISC_COLOR else DISC_BLACK,
                hideDisc = prefs.getBoolean(KEY_HIDE_DISC, false),
                particleFullscreen = prefs.getBoolean(KEY_PARTICLE_FULLSCREEN, false),
                particleCount = prefs.getInt(KEY_PARTICLE_COUNT, 40).coerceIn(10, 100),
                lyricOffsetMs = prefs.getInt(KEY_LYRIC_OFFSET, 0).coerceIn(-5000, 5000),
                lyricsEnabled = prefs.getBoolean(KEY_LYRICS_ENABLED, false),
                discLabelPath = prefs.getString(KEY_DISC_LABEL, null) ?: "",
                particlesEnabled = prefs.getBoolean(KEY_PARTICLES, true),
                particleSize = prefs.getInt(KEY_PARTICLE_SIZE, 50).coerceIn(0, 100),
                particleFollowBar = prefs.getBoolean(KEY_PARTICLE_FOLLOW, true),
                particleColor = normaliseFollowThemeDefault(prefs.getInt(KEY_PARTICLE_COLOR, 0)),
                glowSize = prefs.getInt(KEY_GLOW_SIZE, 50).coerceIn(0, 150),
                arcAmplitude = prefs.getInt(KEY_ARC_AMPLITUDE, 80).coerceIn(0, 100),
                arcBlur = prefs.getInt(KEY_ARC_BLUR, 0).coerceIn(0, 100),
                accentColor = prefs.getInt(KEY_ACCENT, 0),
                textColor = prefs.getInt(KEY_TEXT_COLOR, 0),
                wallpaperType = prefs.getString(KEY_WALLPAPER_TYPE, null) ?: "",
                wallpaperPath = prefs.getString(KEY_WALLPAPER_PATH, null) ?: "",
                cropLeft = prefs.getFloat(KEY_CROP_LEFT, 0f),
                cropTop = prefs.getFloat(KEY_CROP_TOP, 0f),
                cropRight = prefs.getFloat(KEY_CROP_RIGHT, 1f),
                cropBottom = prefs.getFloat(KEY_CROP_BOTTOM, 1f)
            )
        }
    }
}
