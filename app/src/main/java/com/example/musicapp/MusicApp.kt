package com.example.musicapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.musicapp.play.PlayerSettings

/**
 * 启动时按设置应用主题（默认白色浅色主题）。
 * 借助 AppCompat 的夜间模式开关，配色由 values / values-night 两套资源自动切换。
 */
class MusicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode(PlayerSettings.load(this))
    }

    companion object {

        fun nightModeOf(themeMode: String): Int = when (themeMode) {
            PlayerSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            PlayerSettings.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }

        fun applyNightMode(settings: PlayerSettings) {
            val mode = nightModeOf(settings.themeMode)
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}
