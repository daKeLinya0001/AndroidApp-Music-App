package com.example.musicapp

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.example.musicapp.play.PlayerSettings
import com.example.musicapp.ui.ThemeColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 全屏播放器设置，对应插件的 settings overlay。
 * 所有修改即时写入 SharedPreferences，没有「应用」按钮。
 */
class SettingsActivity : AppCompatActivity() {

    private var settings = PlayerSettings()
    private var updating = false

    private lateinit var scroll: NestedScrollView
    private lateinit var swSpectrum: MaterialSwitch
    private lateinit var swLibrarySpectrum: MaterialSwitch
    private lateinit var rgPlacement: RadioGroup
    private lateinit var rbTop: RadioButton
    private lateinit var rbBottom: RadioButton
    private lateinit var rgTheme: RadioGroup
    private lateinit var rbThemeLight: RadioButton
    private lateinit var rbThemeDark: RadioButton
    private lateinit var rbThemeSystem: RadioButton
    private lateinit var rgDisc: RadioGroup
    private lateinit var rbDiscBlack: RadioButton
    private lateinit var rbDiscColor: RadioButton
    private lateinit var sliderMaxHeight: Slider
    private lateinit var sliderSpeed: Slider
    private lateinit var sliderOpacity: Slider
    private lateinit var sliderCardOpacity: Slider
    private lateinit var sliderBlur: Slider
    private lateinit var tvMaxHeightValue: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvOpacityValue: TextView
    private lateinit var tvCardOpacityValue: TextView
    private lateinit var tvBlurValue: TextView
    private lateinit var tvColorValue: TextView
    private lateinit var presetsContainer: LinearLayout
    private lateinit var etCustomColor: EditText
    private lateinit var swParticles: MaterialSwitch
    private lateinit var swHideDisc: MaterialSwitch
    private lateinit var sliderParticleSize: Slider
    private lateinit var tvParticleSizeValue: TextView
    private lateinit var sliderGlowSize: Slider
    private lateinit var tvGlowSizeValue: TextView
    private lateinit var sliderArcAmplitude: Slider
    private lateinit var tvArcAmplitudeValue: TextView
    private lateinit var sliderArcBlur: Slider
    private lateinit var tvArcBlurValue: TextView
    private lateinit var accentChips: LinearLayout
    private lateinit var textChips: LinearLayout
    private lateinit var btnAccentDefault: MaterialButton
    private lateinit var btnTextDefault: MaterialButton
    private lateinit var btnBarDefault: MaterialButton
    private var appliedAccent = 0
    private var appliedText = 0
    private lateinit var tvWallpaperStatus: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveWallpaper(it, "image") }
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveWallpaper(it, "video") }
    }

    private var pendingWallpaperPath = ""
    private var pendingWallpaperType = ""

    /** 取景框确认后保存，取消则删掉刚复制的文件 */
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = pendingWallpaperPath
        val type = pendingWallpaperType
        pendingWallpaperPath = ""
        pendingWallpaperType = ""
        if (path.isBlank()) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val left = data?.getFloatExtra(CropActivity.EXTRA_LEFT, 0f) ?: 0f
            val top = data?.getFloatExtra(CropActivity.EXTRA_TOP, 0f) ?: 0f
            val right = data?.getFloatExtra(CropActivity.EXTRA_RIGHT, 1f) ?: 1f
            val bottom = data?.getFloatExtra(CropActivity.EXTRA_BOTTOM, 1f) ?: 1f
            deleteWallpaperFile(settings.wallpaperPath)
            settings = settings.copy(
                wallpaperType = type,
                wallpaperPath = path,
                cropLeft = left,
                cropTop = top,
                cropRight = right,
                cropBottom = bottom
            )
            settings.save(this)
            updateWallpaperStatus()
        } else {
            runCatching { File(path).delete() }
        }
    }
    private lateinit var rgParticleColor: RadioGroup
    private lateinit var rbParticleBar: RadioButton
    private lateinit var rbParticleCustom: RadioButton
    private lateinit var particleCustomWrap: View
    private lateinit var particlePresets: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        settings = PlayerSettings.load(this)
        bindViews()
        applyWindowInsets()
        setupPresets()
        setupParticlePresets()
        setupColorChips()
        bindValues()
        setupListeners()
        applyColors()

        // 进程/页面重建后恢复待确认的壁纸，避免取景返回时丢失路径变成孤儿文件
        pendingWallpaperPath = savedInstanceState?.getString(KEY_PENDING_PATH) ?: ""
        pendingWallpaperType = savedInstanceState?.getString(KEY_PENDING_TYPE) ?: ""
        cleanupOrphanWallpapers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_PATH, pendingWallpaperPath)
        outState.putString(KEY_PENDING_TYPE, pendingWallpaperType)
    }

    /** 清掉没有被任何设置引用的壁纸文件（例如取景框被系统回收后遗留的临时文件） */
    private fun cleanupOrphanWallpapers() {
        // 正在取景的临时文件不能删，否则确认后会得到一个失效路径
        if (pendingWallpaperPath.isNotBlank()) return
        val keep = settings.wallpaperPath
        runCatching {
            File(filesDir, "wallpapers").listFiles()?.forEach { file ->
                if (file.absolutePath != keep) file.delete()
            }
        }
    }

    /**
     * 滑杆、开关、输入框都会把状态存进 instance state，重建时系统会先恢复它们，
     * 把 onCreate 里按设置写入的值覆盖掉（重置后「数值回正、滑杆不动」就是这个原因）。
     * 更麻烦的是恢复输入框文本会触发 TextWatcher，把旧颜色重新写回设置，
     * 所以恢复期间必须先屏蔽所有监听回调，恢复完再按当前设置重新绑定一次。
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        updating = true
        super.onRestoreInstanceState(savedInstanceState)
        bindValues()
        applyColors()
    }

    private fun bindViews() {
        scroll = findViewById(R.id.settings_scroll)
        swSpectrum = findViewById(R.id.sw_spectrum)
        swLibrarySpectrum = findViewById(R.id.sw_library_spectrum)
        rgPlacement = findViewById(R.id.rg_placement)
        rbTop = findViewById(R.id.rb_top)
        rbBottom = findViewById(R.id.rb_bottom)
        rgTheme = findViewById(R.id.rg_theme)
        rbThemeLight = findViewById(R.id.rb_theme_light)
        rbThemeDark = findViewById(R.id.rb_theme_dark)
        rbThemeSystem = findViewById(R.id.rb_theme_system)
        rgDisc = findViewById(R.id.rg_disc)
        rbDiscBlack = findViewById(R.id.rb_disc_black)
        rbDiscColor = findViewById(R.id.rb_disc_color)
        sliderMaxHeight = findViewById(R.id.slider_max_height)
        sliderSpeed = findViewById(R.id.slider_speed)
        sliderOpacity = findViewById(R.id.slider_opacity)
        sliderCardOpacity = findViewById(R.id.slider_card_opacity)
        sliderBlur = findViewById(R.id.slider_blur)
        tvMaxHeightValue = findViewById(R.id.tv_max_height_value)
        tvSpeedValue = findViewById(R.id.tv_speed_value)
        tvOpacityValue = findViewById(R.id.tv_opacity_value)
        tvCardOpacityValue = findViewById(R.id.tv_card_opacity_value)
        tvBlurValue = findViewById(R.id.tv_blur_value)
        tvColorValue = findViewById(R.id.tv_color_value)
        btnBarDefault = findViewById(R.id.btn_bar_default)
        presetsContainer = findViewById(R.id.presets_container)
        etCustomColor = findViewById(R.id.et_custom_color)
        swParticles = findViewById(R.id.sw_particles)
        swHideDisc = findViewById(R.id.sw_hide_disc)
        sliderParticleSize = findViewById(R.id.slider_particle_size)
        tvParticleSizeValue = findViewById(R.id.tv_particle_size_value)
        sliderGlowSize = findViewById(R.id.slider_glow_size)
        tvGlowSizeValue = findViewById(R.id.tv_glow_size_value)
        sliderArcAmplitude = findViewById(R.id.slider_arc_amplitude)
        tvArcAmplitudeValue = findViewById(R.id.tv_arc_amplitude_value)
        sliderArcBlur = findViewById(R.id.slider_arc_blur)
        tvArcBlurValue = findViewById(R.id.tv_arc_blur_value)
        accentChips = findViewById(R.id.accent_chips)
        textChips = findViewById(R.id.text_chips)
        btnAccentDefault = findViewById(R.id.btn_accent_default)
        btnTextDefault = findViewById(R.id.btn_text_default)
        tvWallpaperStatus = findViewById(R.id.tv_wallpaper_status)
        rgParticleColor = findViewById(R.id.rg_particle_color)
        rbParticleBar = findViewById(R.id.rb_particle_bar)
        rbParticleCustom = findViewById(R.id.rb_particle_custom)
        particleCustomWrap = findViewById(R.id.particle_custom_wrap)
        particlePresets = findViewById(R.id.particle_presets)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scroll.setPadding(0, dp(26f).toInt() + bars.top, 0, dp(32f).toInt() + bars.bottom)
            insets
        }
    }

    private fun bindValues() {
        updating = true
        swSpectrum.isChecked = settings.spectrumEnabled
        swLibrarySpectrum.isChecked = settings.librarySpectrumEnabled
        if (settings.placementBottom) rbBottom.isChecked = true else rbTop.isChecked = true
        when (settings.themeMode) {
            PlayerSettings.THEME_DARK -> rbThemeDark.isChecked = true
            PlayerSettings.THEME_SYSTEM -> rbThemeSystem.isChecked = true
            else -> rbThemeLight.isChecked = true
        }
        if (settings.discMode == PlayerSettings.DISC_COLOR) {
            rbDiscColor.isChecked = true
        } else {
            rbDiscBlack.isChecked = true
        }
        sliderMaxHeight.value = settings.maxBarHeight.toFloat()
        sliderSpeed.value = settings.barSpeed.toFloat()
        sliderOpacity.value = settings.barOpacity.toFloat()
        sliderCardOpacity.value = settings.cardOpacity.toFloat()
        sliderBlur.value = settings.blurStrength.toFloat()
        swParticles.isChecked = settings.particlesEnabled
        swHideDisc.isChecked = settings.hideDisc
        updateDiscControls()
        sliderParticleSize.value = settings.particleSize.toFloat()
        sliderGlowSize.value = settings.glowSize.toFloat()
        sliderArcAmplitude.value = settings.arcAmplitude.toFloat()
        sliderArcBlur.value = settings.arcBlur.toFloat()
        if (settings.particleFollowBar) {
            rbParticleBar.isChecked = true
        } else {
            rbParticleCustom.isChecked = true
        }
        particleCustomWrap.visibility = if (settings.particleFollowBar) View.GONE else View.VISIBLE
        refreshParticlePresetSelection()
        updateLabels()
        refreshPresetSelection()
        if (etCustomColor.text.toString().uppercase(Locale.ROOT) != barColorLabel()) {
            etCustomColor.setText(barColorLabel())
        }
        updateWallpaperStatus()
        updating = false
    }

    // ------------------------------------------------------------ 壁纸

    private fun updateWallpaperStatus() {
        tvWallpaperStatus.text = getString(
            when (settings.wallpaperType) {
                "image" -> R.string.wallpaper_image_status
                "video" -> R.string.wallpaper_video_status
                else -> R.string.wallpaper_default
            }
        )
    }

    private fun saveWallpaper(uri: Uri, type: String) {
        val extension = when (type) {
            "video" -> "mp4"
            else -> "jpg"
        }
        val folder = File(filesDir, "wallpapers").apply { mkdirs() }
        val target = File(folder, "wallpaper_${System.currentTimeMillis()}.$extension")

        // 视频可能很大，复制放到后台线程，避免卡住界面甚至 ANR
        Thread {
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
            }.getOrDefault(false)

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    runCatching { target.delete() }
                    return@runOnUiThread
                }
                if (!copied) {
                    runCatching { target.delete() }
                    Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                // 复制成功后先进入取景框，确认后再替换旧壁纸
                pendingWallpaperPath = target.absolutePath
                pendingWallpaperType = type
                cropLauncher.launch(
                    Intent(this, CropActivity::class.java)
                        .putExtra(CropActivity.EXTRA_PATH, target.absolutePath)
                        .putExtra(CropActivity.EXTRA_TYPE, type)
                )
            }
        }.start()
    }

    private fun clearWallpaperSetting() {
        deleteWallpaperFile(settings.wallpaperPath)
        settings = settings.copy(wallpaperType = "", wallpaperPath = "")
        settings.save(this)
        updateWallpaperStatus()
    }

    private fun deleteWallpaperFile(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).delete() }
    }

    private fun updateLabels() {
        tvMaxHeightValue.text = "${settings.maxBarHeight} px"
        tvSpeedValue.text = "${settings.barSpeed} / 100"
        tvOpacityValue.text = "${settings.barOpacity}%"
        tvCardOpacityValue.text = "${settings.cardOpacity}%"
        tvParticleSizeValue.text = "${settings.particleSize}"
        tvGlowSizeValue.text = "${settings.glowSize}"
        tvArcAmplitudeValue.text = "${settings.arcAmplitude}"
        tvArcBlurValue.text = "${settings.arcBlur}"
        tvBlurValue.text = "${settings.blurStrength}"
        tvColorValue.text = barColorLabel()
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btn_reset_settings).setOnClickListener { confirmReset() }
        findViewById<MaterialButton>(R.id.btn_pick_image).setOnClickListener { pickImage.launch("image/*") }
        findViewById<MaterialButton>(R.id.btn_pick_video).setOnClickListener { pickVideo.launch("video/*") }
        findViewById<MaterialButton>(R.id.btn_clear_wallpaper).setOnClickListener { clearWallpaperSetting() }
        btnBarDefault.setOnClickListener { resetBarColorToTheme() }

        swSpectrum.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            settings = settings.copy(spectrumEnabled = checked)
            settings.save(this)
        }

        swLibrarySpectrum.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            settings = settings.copy(librarySpectrumEnabled = checked)
            settings.save(this)
        }

        rgPlacement.setOnCheckedChangeListener { _, checkedId ->
            if (updating) return@setOnCheckedChangeListener
            settings = settings.copy(placementBottom = checkedId == R.id.rb_bottom)
            settings.save(this)
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            if (updating) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.rb_theme_dark -> PlayerSettings.THEME_DARK
                R.id.rb_theme_system -> PlayerSettings.THEME_SYSTEM
                else -> PlayerSettings.THEME_LIGHT
            }
            if (mode == settings.themeMode) return@setOnCheckedChangeListener
            settings = settings.copy(themeMode = mode)
            settings.save(this)
            // 切换夜间模式会让 Activity 自动重建，配色由 values / values-night 切换
            MusicApp.applyNightMode(settings)
        }

        rgDisc.setOnCheckedChangeListener { _, checkedId ->
            if (updating) return@setOnCheckedChangeListener
            val mode = if (checkedId == R.id.rb_disc_color) {
                PlayerSettings.DISC_COLOR
            } else {
                PlayerSettings.DISC_BLACK
            }
            if (mode == settings.discMode) return@setOnCheckedChangeListener
            settings = settings.copy(discMode = mode)
            settings.save(this)
        }

        swHideDisc.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            if (checked == settings.hideDisc) return@setOnCheckedChangeListener
            settings = settings.copy(hideDisc = checked)
            settings.save(this)
            updateDiscControls()
        }

        sliderMaxHeight.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(maxBarHeight = value.roundToInt().coerceIn(0, 300))
            settings.save(this)
            tvMaxHeightValue.text = "${settings.maxBarHeight} px"
        }

        sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(barSpeed = value.roundToInt().coerceIn(10, 100))
            settings.save(this)
            tvSpeedValue.text = "${settings.barSpeed} / 100"
        }

        sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(barOpacity = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvOpacityValue.text = "${settings.barOpacity}%"
        }

        sliderCardOpacity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(cardOpacity = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvCardOpacityValue.text = "${settings.cardOpacity}%"
        }

        sliderBlur.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(blurStrength = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvBlurValue.text = "${settings.blurStrength}"
        }

        swParticles.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            settings = settings.copy(particlesEnabled = checked)
            settings.save(this)
        }

        sliderParticleSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(particleSize = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvParticleSizeValue.text = "${settings.particleSize}"
        }

        sliderGlowSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(glowSize = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvGlowSizeValue.text = "${settings.glowSize}"
        }

        sliderArcAmplitude.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(arcAmplitude = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvArcAmplitudeValue.text = "${settings.arcAmplitude}"
        }

        sliderArcBlur.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || updating) return@addOnChangeListener
            settings = settings.copy(arcBlur = value.roundToInt().coerceIn(0, 100))
            settings.save(this)
            tvArcBlurValue.text = "${settings.arcBlur}"
        }

        rgParticleColor.setOnCheckedChangeListener { _, checkedId ->
            if (updating) return@setOnCheckedChangeListener
            val follow = checkedId == R.id.rb_particle_bar
            settings = settings.copy(particleFollowBar = follow)
            settings.save(this)
            particleCustomWrap.visibility = if (follow) View.GONE else View.VISIBLE
            refreshParticlePresetSelection()
        }

        etCustomColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val text = s?.toString()?.trim().orEmpty()
                if (!HEX_PATTERN.matches(text)) return
                val color = runCatching { Color.parseColor(text) }.getOrNull() ?: return
                applyColor(color)
            }
        })
    }

    private fun setupPresets() {
        presetsContainer.removeAllViews()
        val size = dp(36f).toInt()
        PlayerSettings.PRESET_COLORS.forEach { color ->
            val chip = View(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = dp(10f).toInt()
            chip.layoutParams = params
            chip.tag = color
            chip.contentDescription = hexOf(color)
            chip.background = chipBackground(color, false)
            chip.setOnClickListener { applyColor(color) }
            presetsContainer.addView(chip)
        }
    }

    /** 隐藏唱片时，「唱片颜色」就不再有意义，置灰提示 */
    private fun updateDiscControls() {
        val enabled = !settings.hideDisc
        rgDisc.isEnabled = enabled
        rgDisc.alpha = if (enabled) 1f else 0.4f
    }

    /** 频谱柱颜色的展示文本：跟随主题时显示解析后的实际颜色 */
    private fun barColorLabel(): String {
        if (settings.barColor != 0) return hexOf(settings.barColor)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) "#FFFFFF" else "#000000"
    }

    /** 频谱柱颜色恢复「跟随主题」：浅色黑、深色白 */
    private fun resetBarColorToTheme() {
        if (settings.barColor == 0) return
        settings = settings.copy(barColor = 0)
        settings.save(this)
        tvColorValue.text = barColorLabel()
        refreshPresetSelection()
        updating = true
        etCustomColor.setText(barColorLabel())
        updating = false
    }

    private fun applyColor(color: Int) {        settings = settings.copy(barColor = color)
        settings.save(this)
        tvColorValue.text = hexOf(color)
        refreshPresetSelection()
        updating = true
        if (etCustomColor.text.toString().uppercase(Locale.ROOT) != hexOf(color)) {
            etCustomColor.setText(hexOf(color))
        }
        updating = false
    }

    private fun setupParticlePresets() {
        particlePresets.removeAllViews()
        val size = dp(32f).toInt()
        PlayerSettings.PRESET_COLORS.forEach { color ->
            val chip = View(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = dp(10f).toInt()
            chip.layoutParams = params
            chip.tag = color
            chip.contentDescription = hexOf(color)
            chip.background = chipBackground(color, false)
            chip.setOnClickListener { applyParticleColor(color) }
            particlePresets.addView(chip)
        }
    }

    private fun applyParticleColor(color: Int) {
        settings = settings.copy(particleFollowBar = false, particleColor = color)
        settings.save(this)
        refreshParticlePresetSelection()
        updating = true
        rbParticleCustom.isChecked = true
        updating = false
        particleCustomWrap.visibility = View.VISIBLE
    }

    private fun refreshParticlePresetSelection() {
        for (index in 0 until particlePresets.childCount) {
            val chip = particlePresets.getChildAt(index)
            val color = chip.tag as? Int ?: continue
            val selected = !settings.particleFollowBar && color == settings.particleColor
            chip.background = chipBackground(color, selected)
        }
    }

    private fun refreshPresetSelection() {
        for (index in 0 until presetsContainer.childCount) {
            val chip = presetsContainer.getChildAt(index)
            val color = chip.tag as? Int ?: continue
            chip.background = chipBackground(color, color == settings.barColor)
        }
    }

    private fun chipBackground(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke(dp(2.5f).toInt(), Color.WHITE)
            } else {
                setStroke(dp(1f).toInt(), 0x33FFFFFF)
            }
        }

    private fun hexOf(color: Int): String =
        String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color)

    // ------------------------------------------------------------ 整体配色

    private val accentPresets = intArrayOf(
        0xFF111111.toInt(),
        0xFF2563EB.toInt(),
        0xFF7C3AED.toInt(),
        0xFF059669.toInt(),
        0xFFEA580C.toInt(),
        0xFFDC2626.toInt(),
        0xFFDB2777.toInt()
    )

    private val textPresets = intArrayOf(
        0xFF0F172A.toInt(),
        0xFF334155.toInt(),
        0xFF64748B.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF1E3A8A.toInt(),
        0xFF7C2D12.toInt()
    )

    private fun setupColorChips() {
        buildChips(accentChips, accentPresets) { color -> changeAccent(color) }
        buildChips(textChips, textPresets) { color -> changeText(color) }
        btnAccentDefault.setOnClickListener { changeAccent(0) }
        btnTextDefault.setOnClickListener { changeText(0) }
        refreshColorChips()
    }

    private fun buildChips(container: LinearLayout, presets: IntArray, onPick: (Int) -> Unit) {
        container.removeAllViews()
        val size = dp(30f).toInt()
        presets.filter { visibleOnCurrentTheme(it) }.forEach { color ->
            val chip = View(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = dp(9f).toInt()
            chip.layoutParams = params
            chip.tag = color
            chip.contentDescription = hexOf(color)
            chip.background = chipBackground(color, false)
            chip.setOnClickListener { onPick(color) }
            container.addView(chip)
        }
    }

    /** 浅色主题不提供接近白色的选项，深色主题不提供接近黑色的选项 */
    private fun visibleOnCurrentTheme(color: Int): Boolean {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val luminance = ColorUtils.calculateLuminance(color)
        return if (night) luminance > 0.03 else luminance < 0.93
    }

    private fun changeAccent(color: Int) {
        if (settings.accentColor == color) return
        settings = settings.copy(accentColor = color)
        settings.save(this)
        applyColors()
        refreshColorChips()
    }

    private fun changeText(color: Int) {
        if (settings.textColor == color) return
        settings = settings.copy(textColor = color)
        settings.save(this)
        applyColors()
        refreshColorChips()
    }

    private fun applyColors() {
        ThemeColors.apply(
            findViewById(R.id.settings_root),
            settings,
            this,
            appliedAccent,
            appliedText
        )
        appliedAccent = settings.accentColor
        appliedText = settings.textColor
    }

    private fun refreshColorChips() {
        // accentColor = 0 表示「跟随主题」，此时主题默认色就是黑色，让黑色预设显示选中
        val effectiveAccent = if (settings.accentColor != 0) settings.accentColor else 0xFF111111.toInt()
        refreshChips(accentChips, effectiveAccent)
        refreshChips(textChips, settings.textColor)
    }

    private fun refreshChips(container: LinearLayout, selectedColor: Int) {
        for (index in 0 until container.childCount) {
            val chip = container.getChildAt(index)
            val color = chip.tag as? Int ?: continue
            chip.background = chipBackground(color, selectedColor != 0 && color == selectedColor)
        }
    }

    /** 重置前再确认一次，避免误触 */
    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_dialog_title)
            .setMessage(R.string.reset_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset) { _, _ -> resetAllSettings() }
            .show()
    }

    private fun resetAllSettings() {
        val defaults = PlayerSettings()
        // 重置也清掉壁纸文件
        deleteWallpaperFile(settings.wallpaperPath)
        defaults.save(this)
        settings = defaults
        updateWallpaperStatus()

        val targetNightMode = MusicApp.nightModeOf(defaults.themeMode)
        val nightModeChanged = AppCompatDelegate.getDefaultNightMode() != targetNightMode
        MusicApp.applyNightMode(defaults)
        // 主题没变化时手动重建，让所有控件回到默认值；主题变了则由系统自行重建
        if (!nightModeChanged) {
            recreate()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        val HEX_PATTERN = Regex("^#[0-9a-fA-F]{6}$")
        const val KEY_PENDING_PATH = "pendingWallpaperPath"
        const val KEY_PENDING_TYPE = "pendingWallpaperType"
    }
}
