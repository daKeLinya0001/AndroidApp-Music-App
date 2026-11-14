package app.endlessmusic.player

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.endlessmusic.player.play.PlayerSettings
import app.endlessmusic.player.ui.ThemeColors

/**
 * 用户设置页：显示/修改用户名，切换中英文。
 *
 * 配色完全复用设置页的方案（ThemeColors.apply），因此强调色、文字颜色、
 * 深色模式都会同步生效。
 */
class UserActivity : AppCompatActivity() {

    private lateinit var settings: PlayerSettings
    private lateinit var tvName: TextView
    private lateinit var rgLanguage: RadioGroup
    private lateinit var rbZh: RadioButton
    private lateinit var rbEn: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user)

        settings = PlayerSettings.load(this)
        tvName = findViewById(R.id.tv_user_name)
        rgLanguage = findViewById(R.id.rg_language)
        rbZh = findViewById(R.id.rb_language_zh)
        rbEn = findViewById(R.id.rb_language_en)

        findViewById<ImageButton>(R.id.btn_user_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_change_name).setOnClickListener { showChangeNameDialog() }
        findViewById<View>(R.id.btn_clear_cache).setOnClickListener { showClearCacheDialog() }

        // 语言：默认中文，切换后立即生效
        val current = AppLanguage.current(this)
        if (current == AppLanguage.EN) rbEn.isChecked = true else rbZh.isChecked = true
        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = if (checkedId == R.id.rb_language_en) AppLanguage.EN else AppLanguage.ZH
            if (lang != AppLanguage.current(this)) {
                AppLanguage.apply(this, lang)
            }
        }

        applyWindowInsets()
        applyColors()
    }

    override fun onResume() {
        super.onResume()
        settings = PlayerSettings.load(this)
        applyColors()
        refreshName()
    }

    private fun refreshName() {
        val name = WelcomePrefs.username(this)
        tvName.text = name.ifBlank { getString(R.string.user_name_unset) }
    }

    private fun applyColors() {
        ThemeColors.apply(findViewById(R.id.user_root), settings, this)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.user_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun showChangeNameDialog() {
        val input = EditText(this).apply {
            background = getDrawable(R.drawable.bg_welcome_input)
            hint = getString(R.string.user_change_hint)
            setText(WelcomePrefs.username(this@UserActivity))
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(24))
            setPadding(dp(22), dp(14), dp(22), dp(14))
            isSingleLine = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.user_change_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ThemeColors.textPrimary(settings, this))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ThemeColors.textSecondary(settings, this))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString()
                when {
                    name.isEmpty() -> input.error = getString(R.string.welcome_error_empty)
                    name.any { it.isWhitespace() } -> input.error = getString(R.string.welcome_error_space)
                    else -> {
                        WelcomePrefs.setUsername(this, name)
                        refreshName()
                        Toast.makeText(this, R.string.user_change_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
        // 弹窗里的输入框默认不聚焦，手动聚焦并弹出键盘，否则键盘输入会落到页面上
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.user_cache_confirm_title)
            .setMessage(R.string.user_cache_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.user_cache_action) { _, _ ->
                AppCache.clear(this)
                // 只重置欢迎页标记，用户名保持
                WelcomePrefs.resetWelcome(this)
                Toast.makeText(this, R.string.user_cache_done, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
