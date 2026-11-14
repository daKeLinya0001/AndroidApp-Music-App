package app.endlessmusic.player

import android.content.Context

/** 用户名与「欢迎页已完成」标记，集中在这里读写，避免多处硬编码 key */
object WelcomePrefs {

    private const val PREFS = "welcome"
    private const val KEY_DONE = "done"
    private const val KEY_USERNAME = "username"

    fun username(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, "") ?: ""

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun setUsername(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, name)
            .apply()
    }

    fun complete(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, name)
            .putBoolean(KEY_DONE, true)
            .apply()
    }

    /** 清理缓存时调用：只重置「已完成」标记，用户名保留，下次启动重新显示欢迎页 */
    fun resetWelcome(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, false)
            .apply()
    }
}
