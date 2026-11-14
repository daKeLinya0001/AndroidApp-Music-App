package app.endlessmusic.player

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用语言（仅中文 / 英文，默认中文）。
 *
 * 使用 AppCompat 的 per-app language：setApplicationLocales 会让所有
 * AppCompatActivity 重建并立刻切换资源语言，实现「选定后立即生效」。
 * 语言选择同时存在独立 SharedPreferences 里，启动时按它恢复。
 */
object AppLanguage {

    private const val PREFS = "app_language"
    private const val KEY = "lang"

    const val ZH = "zh"
    const val EN = "en"

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ZH)
        return if (saved == EN) EN else ZH
    }

    /** 切换语言并立即生效（当前活动会自动重建） */
    fun apply(context: Context, lang: String) {
        val value = if (lang == EN) EN else ZH
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
    }

    /** 启动时恢复到用户选择的语言 */
    fun applyStored(context: Context) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(current(context))
        )
    }
}
