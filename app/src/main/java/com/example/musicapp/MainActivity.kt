package com.example.musicapp

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.max
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.example.musicapp.data.Track
import com.example.musicapp.data.TrackStore
import com.example.musicapp.play.PlaybackService
import com.example.musicapp.play.PlayerEngine
import com.example.musicapp.play.PlayerSettings
import com.example.musicapp.ui.DiscView
import com.example.musicapp.ui.ParticleView
import com.example.musicapp.ui.SpectrumView
import com.example.musicapp.ui.ThemeColors
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var engine: PlayerEngine
    private lateinit var store: TrackStore
    private var settings = PlayerSettings()

    private val allTracks = mutableListOf<Track>()
    private var query = ""
    private var lastRenderedTrackId: String? = null
    private var isUserSeeking = false
    private var permissionRequested = false
    private var notificationPermissionAsked = false

    private lateinit var scroll: NestedScrollView
    private lateinit var spectrumPlayer: SpectrumView
    private lateinit var spectrumLibrary: SpectrumView
    private lateinit var particleView: ParticleView
    private lateinit var playerCard: View
    private lateinit var libraryCard: View
    private lateinit var discContainer: FrameLayout
    private lateinit var discView: DiscView
    private lateinit var tvCount: TextView
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackSub: TextView
    private lateinit var seekProgress: SeekBar
    private lateinit var tvTimeCurrent: TextView
    private lateinit var tvTimeTotal: TextView
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvRepeatBadge: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyState: View
    private lateinit var searchWrap: View
    private lateinit var etSearch: EditText
    private lateinit var tvNoResults: TextView
    private lateinit var btnImport: ImageButton
    private lateinit var btnEmptyImport: MaterialButton

    // 全屏展开的播放器
    private lateinit var expandedOverlay: FrameLayout
    private lateinit var expandedScrim: View
    private lateinit var expandedSheet: FrameLayout
    private lateinit var expandedContent: LinearLayout
    private lateinit var exDiscView: DiscView
    private lateinit var exSpectrum: SpectrumView
    private lateinit var exParticle: ParticleView
    private lateinit var exDiscContainer: FrameLayout
    private lateinit var exVisualizer: FrameLayout
    private lateinit var exTrackTitle: TextView
    private lateinit var exTrackSub: TextView
    private lateinit var exSeek: SeekBar
    private lateinit var exTimeCurrent: TextView
    private lateinit var exTimeTotal: TextView
    private lateinit var exBtnRepeat: ImageButton
    private lateinit var exBtnPlay: ImageButton
    private lateinit var exBtnPrev: ImageButton
    private lateinit var exBtnNext: ImageButton
    private lateinit var exRepeatBadge: TextView
    private lateinit var btnCollapse: ImageButton
    private var isExpanded = false
    private var isExpandedSeeking = false
    private var appliedAccent = 0
    private var appliedText = 0
    private lateinit var wallpaperLayer: View
    private lateinit var wallpaperImage: ImageView
    private lateinit var wallpaperVideo: TextureView
    private lateinit var wallpaperScrim: View
    private var wallpaperPlayer: MediaPlayer? = null
    private var wallpaperVideoPath = ""

    private val tickHandler = Handler(Looper.getMainLooper())
    private var rotationAnimator: ValueAnimator? = null

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) importTracks(uris) }

    private val requestRecordAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) engine.enableVisualizerIfPossible() }

    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val engineListener: () -> Unit = { onEngineStateChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        engine = PlayerEngine.get(this)
        store = TrackStore(this)

        bindViews()
        applyWindowInsets()
        ensureRotationAnimator()
        setupListeners()

        allTracks.addAll(store.load())
        engine.addListener(engineListener)
        engine.restore(allTracks.toList())

        settings = PlayerSettings.load(this)
        applySettings()
        refreshLibrary()
        lastRenderedTrackId = engine.currentTrack?.id
        applyPlaybackState()
        if (engine.isPlaying) PlaybackService.start(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isExpanded) {
                    collapsePlayer()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val latest = PlayerSettings.load(this)
        if (latest != settings) {
            settings = latest
            applySettings()
            applyPlaybackState()
            // 列表行的选中底色是按强调色生成的，必须重建行才能跟随配色变化
            refreshLibrary()
        }
        wallpaperPlayer?.let { runCatching { it.start() } }
    }

    override fun onPause() {
        wallpaperPlayer?.let { runCatching { it.pause() } }
        super.onPause()
    }

    override fun onDestroy() {
        tickHandler.removeCallbacks(ticker)
        rotationAnimator?.cancel()
        rotationAnimator = null
        releaseWallpaperVideo()
        engine.removeListener(engineListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------ 初始化

    private fun bindViews() {
        scroll = findViewById(R.id.scroll)
        spectrumPlayer = findViewById(R.id.spectrum_player)
        particleView = findViewById(R.id.particle_view)
        spectrumLibrary = findViewById(R.id.spectrum_library)
        playerCard = findViewById(R.id.player_card)
        libraryCard = findViewById(R.id.library_card)
        discContainer = findViewById(R.id.disc_container)
        discView = findViewById(R.id.disc_view)
        tvCount = findViewById(R.id.tv_count)
        tvTrackTitle = findViewById(R.id.tv_track_title)
        tvTrackSub = findViewById(R.id.tv_track_sub)
        seekProgress = findViewById(R.id.seek_progress)
        tvTimeCurrent = findViewById(R.id.tv_time_current)
        tvTimeTotal = findViewById(R.id.tv_time_total)
        btnRepeat = findViewById(R.id.btn_repeat)
        tvRepeatBadge = findViewById(R.id.tv_repeat_badge)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlay = findViewById(R.id.btn_play)
        btnNext = findViewById(R.id.btn_next)
        listContainer = findViewById(R.id.list_container)
        emptyState = findViewById(R.id.empty_state)
        searchWrap = findViewById(R.id.search_wrap)
        etSearch = findViewById(R.id.et_search)
        tvNoResults = findViewById(R.id.tv_no_results)
        btnImport = findViewById(R.id.btn_import)
        btnEmptyImport = findViewById(R.id.btn_empty_import)

        expandedOverlay = findViewById(R.id.expanded_overlay)
        expandedScrim = findViewById(R.id.expanded_scrim)
        expandedSheet = findViewById(R.id.expanded_sheet)
        wallpaperLayer = findViewById(R.id.wallpaper_layer)
        wallpaperImage = findViewById(R.id.wallpaper_image)
        wallpaperVideo = findViewById(R.id.wallpaper_video)
        wallpaperScrim = findViewById(R.id.wallpaper_scrim)
        // 视图被移动/重建时 TextureView 的 Surface 会重新创建，需要重新绑定播放器
        wallpaperVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (settings.wallpaperType == "video" && settings.wallpaperPath.isNotBlank()) {
                    startWallpaperVideo(settings.wallpaperPath)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                wallpaperPlayer?.let { applyVideoTransform(it.videoWidth, it.videoHeight) }
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseWallpaperVideo()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        expandedContent = findViewById(R.id.expanded_content)
        exDiscView = findViewById(R.id.ex_disc_view)
        exSpectrum = findViewById(R.id.ex_spectrum)
        exParticle = findViewById(R.id.ex_particle)
        exDiscContainer = findViewById(R.id.ex_disc_container)
        exVisualizer = findViewById(R.id.ex_visualizer)
        exTrackTitle = findViewById(R.id.ex_track_title)
        exTrackSub = findViewById(R.id.ex_track_sub)
        exSeek = findViewById(R.id.ex_seek)
        exTimeCurrent = findViewById(R.id.ex_time_current)
        exTimeTotal = findViewById(R.id.ex_time_total)
        exBtnRepeat = findViewById(R.id.ex_btn_repeat)
        exBtnPlay = findViewById(R.id.ex_btn_play)
        exBtnPrev = findViewById(R.id.ex_btn_prev)
        exBtnNext = findViewById(R.id.ex_btn_next)
        exRepeatBadge = findViewById(R.id.ex_repeat_badge)
        expandedSheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateExpandedEffectOrigin()
        }
        btnCollapse = findViewById(R.id.btn_collapse)

        // 音乐库频谱柱跟随卡片高度，避免卡片为固定柱高留出空白
        libraryCard.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val target = view.height - dp(10f).toInt()
            if (target > 0 && spectrumLibrary.layoutParams.height != target) {
                spectrumLibrary.layoutParams = spectrumLibrary.layoutParams.apply { height = target }
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scroll.setPadding(0, dp(26f).toInt() + bars.top, 0, dp(28f).toInt() + bars.bottom)
            expandedContent.setPadding(
                dp(20f).toInt(),
                dp(12f).toInt() + bars.top,
                dp(20f).toInt(),
                dp(20f).toInt() + bars.bottom
            )
            insets
        }
    }

    private fun ensureRotationAnimator() {
        if (rotationAnimator != null) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 7000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val rotation = animation.animatedValue as Float
                discContainer.rotation = rotation
                // 全屏的唱盘也要一起转，否则展开后唱片是静止的
                exDiscContainer.rotation = rotation
            }
            start()
            pause()
        }
    }

    private fun setupListeners() {
        btnImport.setOnClickListener { openPicker() }
        btnEmptyImport.setOnClickListener { openPicker() }
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnPlay.setOnClickListener {
            maybeRequestAudioPermission()
            engine.toggle()
        }
        btnPrev.setOnClickListener { engine.previous() }
        btnNext.setOnClickListener { engine.next() }
        btnRepeat.setOnClickListener { engine.cycleRepeat() }

        seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = engine.durationMs
                if (duration > 0) {
                    val target = (duration.toLong() * progress / 1000L).toInt()
                    tvTimeCurrent.text = formatTime(target.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val duration = engine.durationMs
                if (duration > 0) {
                    engine.seekTo((duration.toLong() * (seekBar?.progress ?: 0) / 1000L).toInt())
                }
            }
        })

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refreshLibrary()
            }
        })

        // 点击唱片展开全屏播放器；隐藏唱片后改为点击播放器卡片空白处展开
        discContainer.contentDescription = getString(R.string.expand_player)
        discContainer.setOnClickListener { expandPlayer() }
        playerCard.setOnClickListener { expandPlayer() }

        btnCollapse.setOnClickListener { collapsePlayer() }
        exBtnPlay.setOnClickListener {
            maybeRequestAudioPermission()
            engine.toggle()
        }
        exBtnPrev.setOnClickListener { engine.previous() }
        exBtnNext.setOnClickListener { engine.next() }
        exBtnRepeat.setOnClickListener { engine.cycleRepeat() }

        exSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = engine.durationMs
                if (duration > 0) {
                    exTimeCurrent.text =
                        formatTime((duration.toLong() * progress / 1000L))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isExpandedSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isExpandedSeeking = false
                val duration = engine.durationMs
                if (duration > 0) {
                    engine.seekTo((duration.toLong() * (seekBar?.progress ?: 0) / 1000L).toInt())
                }
            }
        })
    }

    // ------------------------------------------------------------ 设置

    private fun applySettings() {
        discView.style = if (settings.discMode == PlayerSettings.DISC_COLOR) {
            DiscView.DiscStyle.COLOR
        } else {
            DiscView.DiscStyle.BLACK
        }
        // 隐藏黑胶唱片：主界面与全屏同时隐藏唱盘容器
        discContainer.visibility = if (settings.hideDisc) View.GONE else View.VISIBLE
        exDiscContainer.visibility = if (settings.hideDisc) View.GONE else View.VISIBLE
        applyDiscLabel()

        spectrumPlayer.visibility = if (settings.spectrumEnabled) View.VISIBLE else View.GONE
        spectrumPlayer.barColor = resolvedBarColor()
        spectrumPlayer.barOpacity = settings.barOpacity
        spectrumPlayer.maxBarHeightDp = settings.maxBarHeight
        spectrumPlayer.speed = settings.barSpeed
        spectrumPlayer.provider = { engine.latestSpectrum }
        spectrumPlayer.onBass = { bass ->
            val scale = 1f + bass * 0.06f
            discContainer.scaleX = scale
            discContainer.scaleY = scale
        }

        // 粒子与光晕：颜色默认跟随频谱柱，也可在设置里自定义
        particleView.visibility = if (settings.particlesEnabled) View.VISIBLE else View.GONE
        particleView.particleColor = resolvedParticleColor()
        particleView.particleSize = settings.particleSize
        particleView.glowSize = settings.glowSize
        particleView.provider = { engine.latestSpectrum }

        // 全屏播放器里的同一套频谱与粒子
        exSpectrum.visibility = if (settings.spectrumEnabled) View.VISIBLE else View.GONE
        exSpectrum.radialMode = true
        exSpectrum.radialInnerRadiusDp = 102f
        exSpectrum.barCount = 64
        exSpectrum.arcAmplitude = settings.arcAmplitude
        exSpectrum.showRingDecoration = !settings.hideDisc
        exSpectrum.barColor = resolvedBarColor()
        exSpectrum.barOpacity = settings.barOpacity
        exSpectrum.maxBarHeightDp = settings.maxBarHeight
        exSpectrum.speed = settings.barSpeed
        exSpectrum.provider = { engine.latestSpectrum }
        exSpectrum.onBass = { bass ->
            val scale = 1f + bass * 0.06f
            exDiscContainer.scaleX = scale
            exDiscContainer.scaleY = scale
        }

        exParticle.visibility = if (settings.particlesEnabled) View.VISIBLE else View.GONE
        exParticle.particleColor = resolvedParticleColor()
        exParticle.particleSize = settings.particleSize
        exParticle.glowSize = settings.glowSize
        exParticle.provider = { engine.latestSpectrum }

        spectrumLibrary.barColor = resolvedBarColor()
        spectrumLibrary.barOpacity = settings.barOpacity
        spectrumLibrary.speed = settings.barSpeed
        // 音乐库底部预留了频谱区，柱子高度限制在预留区内，避免压住曲目文字
        spectrumLibrary.maxBarHeightDp = settings.maxBarHeight.coerceAtMost(72)
        spectrumLibrary.provider = { engine.latestSpectrum }
        // 对应插件 workspace 变体底部的蓝色氛围渐变
        spectrumLibrary.ambientGlow = true

        applyPlayerBackdropBlur()

        val layoutParams = spectrumLibrary.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = if (settings.placementBottom) Gravity.BOTTOM else Gravity.TOP
        spectrumLibrary.layoutParams = layoutParams

        applyCardBackground(playerCard)
        applyCardBackground(libraryCard)
        applyWallpaper()
        applyThemeColors()
    }

    // ------------------------------------------------------------ 壁纸

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 频谱柱颜色：0 = 跟随主题，浅色黑、深色白 */
    private fun resolvedBarColor(): Int = when {
        settings.barColor != 0 -> settings.barColor
        isNightMode() -> Color.WHITE
        else -> Color.BLACK
    }

    /** 粒子颜色：跟随频谱柱，或自定义（0 时同样跟随主题） */
    private fun resolvedParticleColor(): Int = when {
        settings.particleFollowBar -> resolvedBarColor()
        settings.particleColor != 0 -> settings.particleColor
        isNightMode() -> Color.WHITE
        else -> Color.BLACK
    }

    private fun applyWallpaper() {
        val type = settings.wallpaperType
        val path = settings.wallpaperPath
        val valid = (type == "image" || type == "video") &&
            path.isNotBlank() && File(path).exists()

        if (!valid) {
            wallpaperLayer.visibility = View.GONE
            releaseWallpaperVideo()
            val previous = (wallpaperImage.drawable as? BitmapDrawable)?.bitmap
            wallpaperImage.setImageDrawable(null)
            if (previous != null && !previous.isRecycled) previous.recycle()
            expandedScrim.setBackgroundResource(R.drawable.bg_screen)
            expandedSheet.setBackgroundResource(R.drawable.bg_card)
            return
        }
        wallpaperLayer.visibility = View.VISIBLE
        // 壁纸保持原始观感，不叠加任何压色/模糊
        wallpaperScrim.visibility = View.GONE
        expandedScrim.setBackgroundColor(Color.TRANSPARENT)
        // 全屏面板完全透明，不做任何压色，保证壁纸原样呈现
        expandedSheet.background = null

        if (type == "image") {
            releaseWallpaperVideo()
            wallpaperVideo.visibility = View.GONE
            wallpaperImage.visibility = View.VISIBLE
            val previous = (wallpaperImage.drawable as? BitmapDrawable)?.bitmap
            val next = decodeWallpaper(path)?.let { cropWallpaper(it) }
            wallpaperImage.setImageBitmap(next)
            if (previous != null && previous !== next && !previous.isRecycled) previous.recycle()
        } else {
            wallpaperImage.setImageDrawable(null)
            wallpaperImage.visibility = View.GONE
            wallpaperVideo.visibility = View.VISIBLE
            // 视频解不开时退回默认背景，避免留下一块黑屏
            if (!startWallpaperVideo(path)) {
                wallpaperVideo.visibility = View.GONE
                wallpaperLayer.visibility = View.GONE
                expandedScrim.setBackgroundResource(R.drawable.bg_screen)
                expandedSheet.setBackgroundResource(R.drawable.bg_card)
            }
        }
    }

    /** 按原始分辨率解码（仅对超大图做 2 的幂缩放），避免壁纸被降采样后发糊 */
    private fun decodeWallpaper(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        // 上限取屏幕长边的两倍，既能保留原始清晰度，又不至于 OOM
        val target = max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 2
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target || bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun startWallpaperVideo(path: String): Boolean {
        if (wallpaperPlayer != null && wallpaperVideoPath == path) return true
        releaseWallpaperVideo()
        val texture = wallpaperVideo.surfaceTexture ?: return false
        val player = MediaPlayer()
        val ok = runCatching {
            player.setSurface(Surface(texture))
            player.setDataSource(path)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnVideoSizeChangedListener { _, width, height ->
                applyVideoTransform(width, height)
            }
            player.prepare()
            player.start()
        }.isSuccess
        if (!ok) {
            runCatching { player.release() }
            return false
        }
        wallpaperPlayer = player
        wallpaperVideoPath = path
        wallpaperVideo.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            runCatching { applyVideoTransform(player.videoWidth, player.videoHeight) }
        }
        return true
    }

    /** 按取景框裁出实际要显示的区域 */
    private fun cropWallpaper(source: Bitmap): Bitmap {
        val left = (settings.cropLeft * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (settings.cropTop * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (settings.cropRight * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (settings.cropBottom * source.height).toInt().coerceIn(top + 1, source.height)
        if (left == 0 && top == 0 && right == source.width && bottom == source.height) return source
        return runCatching {
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }.getOrDefault(source)
    }

    /** 视频按取景框裁剪：把归一化矩形映射成缩放+位移矩阵 */
    private fun applyVideoTransform(videoWidth: Int, videoHeight: Int) {
        val viewWidth = wallpaperVideo.width.toFloat()
        val viewHeight = wallpaperVideo.height.toFloat()
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) return

        val cropLeft = settings.cropLeft.coerceIn(0f, 0.98f)
        val cropTop = settings.cropTop.coerceIn(0f, 0.98f)
        val cropRight = settings.cropRight.coerceIn(cropLeft + 0.02f, 1f)
        val cropBottom = settings.cropBottom.coerceIn(cropTop + 0.02f, 1f)

        val sourceWidth = videoWidth * (cropRight - cropLeft)
        val sourceHeight = videoHeight * (cropBottom - cropTop)
        if (sourceWidth <= 0f || sourceHeight <= 0f) return

        val scale = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
        val centerX = videoWidth * (cropLeft + cropRight) / 2f
        val centerY = videoHeight * (cropTop + cropBottom) / 2f

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(viewWidth / 2f - centerX * scale, viewHeight / 2f - centerY * scale)
        wallpaperVideo.setTransform(matrix)
    }

    private fun moveWallpaperForFullscreen(expanded: Boolean) {
        if (wallpaperLayer.visibility != View.VISIBLE) return
        val root = findViewById<FrameLayout>(R.id.root)
        val layerIndex = root.indexOfChild(wallpaperLayer)
        if (layerIndex == -1) return
        val overlayIndex = root.indexOfChild(expandedOverlay)
        if (expanded && overlayIndex == -1) return

        root.removeView(wallpaperLayer)
        // removeView 之后索引会前移，放在全屏面板「正下方」需要相应减一
        val target = if (expanded) {
            if (layerIndex < overlayIndex) overlayIndex - 1 else overlayIndex
        } else {
            0
        }
        root.addView(wallpaperLayer, target, wallpaperLayer.layoutParams)
    }

    private fun releaseWallpaperVideo() {
        runCatching { wallpaperPlayer?.release() }
        wallpaperPlayer = null
        wallpaperVideoPath = ""
    }

    private fun applyCardBackground(view: View) {
        val alpha = settings.cardOpacity.coerceIn(0, 100) * 255 / 100
        val fill = ContextCompat.getColor(this, R.color.card_fill)
        val stroke = ContextCompat.getColor(this, R.color.card_stroke)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22f)
            setColor((fill and 0x00FFFFFF) or (alpha shl 24))
            setStroke(dp(1f).toInt(), stroke)
        }
    }

    /**
     * 对应插件卡片的 backdrop-filter: blur(18px) saturate(1.1)。
     * Android 12+ 用 RenderEffect 对卡片后方的频谱层做 GPU 虚化，强度由设置决定：
     * 0 = 关闭，50 = 4dp（默认观感），100 = 8dp。
     *
     * 只作用于主界面卡片背后的频谱；全屏的弧线是主体视觉，不参与虚化
     * （否则会被糊掉，看起来像失焦）。
     */
    private fun applyPlayerBackdropBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val strength = settings.blurStrength.coerceIn(0, 100)
        val radius = dp(MAX_BLUR_DP * strength / 100f)
        spectrumPlayer.setRenderEffect(
            if (strength == 0) {
                null
            } else {
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            }
        )
        // 全屏弧线：独立的虚化设置，0 = 完全清晰
        val arcStrength = settings.arcBlur.coerceIn(0, 100)
        val arcRadius = dp(MAX_ARC_BLUR_DP * arcStrength / 100f)
        exSpectrum.setRenderEffect(
            if (arcStrength == 0) {
                null
            } else {
                RenderEffect.createBlurEffect(arcRadius, arcRadius, Shader.TileMode.CLAMP)
            }
        )
        // 全屏粒子与光晕跟随同一档虚化；主界面的粒子层不受影响
        exParticle.setRenderEffect(
            if (arcStrength == 0) {
                null
            } else {
                RenderEffect.createBlurEffect(arcRadius, arcRadius, Shader.TileMode.CLAMP)
            }
        )
    }

    // ------------------------------------------------------------ 状态刷新

    private fun onEngineStateChanged() {
        if (engine.currentTrack?.id != lastRenderedTrackId) {
            lastRenderedTrackId = engine.currentTrack?.id
            refreshLibrary()
        }
        // 播放时提升为前台服务，保证退到后台不被回收；清空音乐库后关闭服务
        if (engine.isPlaying) {
            PlaybackService.start(this)
            maybeRequestNotificationPermission()
        } else if (engine.currentTrack == null) {
            PlaybackService.stop(this)
        }
        applyPlaybackState()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionAsked) return
        notificationPermissionAsked = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun applyPlaybackState() {
        val track = engine.currentTrack
        val hasTrack = track != null

        tvTrackTitle.text = track?.name ?: getString(R.string.no_track)
        tvTrackSub.text = if (track != null) {
            "${getString(R.string.local_audio)} · ${track.extLabel} · ${formatBytes(track.size)}"
        } else {
            getString(R.string.no_track_body)
        }

        seekProgress.isEnabled = hasTrack && engine.durationMs > 0
        btnPrev.isEnabled = hasTrack
        btnNext.isEnabled = hasTrack
        btnPlay.isEnabled = hasTrack
        btnPlay.setImageResource(if (engine.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        btnPlay.contentDescription = getString(if (engine.isPlaying) R.string.pause else R.string.play)

        applyRepeatButton(btnRepeat, tvRepeatBadge)

        discView.hasTrack = hasTrack
        discView.isPlaying = engine.isPlaying && hasTrack
        discView.hue = hueFor(track)

        spectrumPlayer.isPlaying = engine.isPlaying && hasTrack && settings.spectrumEnabled && !isExpanded
        spectrumLibrary.isPlaying = engine.isPlaying && hasTrack && !isExpanded
        particleView.isPlaying = engine.isPlaying && hasTrack && settings.particlesEnabled && !isExpanded
        exSpectrum.isPlaying = engine.isPlaying && hasTrack && settings.spectrumEnabled && isExpanded
        exParticle.isPlaying = engine.isPlaying && hasTrack && settings.particlesEnabled && isExpanded
        spectrumLibrary.visibility =
            if (settings.librarySpectrumEnabled && hasTrack) View.VISIBLE else View.GONE

        rotationAnimator?.let { animator ->
            if (engine.isPlaying && hasTrack) animator.resume() else animator.pause()
        }

        engine.errorMessage?.let {
            Toast.makeText(this, R.string.error_playback, Toast.LENGTH_SHORT).show()
            engine.clearError()
        }

        updateProgress()
        updateTicker()
        applyExpandedState()
    }

    private fun updateProgress() {
        engine.pollPosition()
        val duration = engine.durationMs
        if (duration > 0) {
            if (!isUserSeeking) {
                seekProgress.progress =
                    ((engine.currentPositionMs.toFloat() / duration) * 1000f).toInt().coerceIn(0, 1000)
            }
            tvTimeCurrent.text = formatTime(engine.currentPositionMs.toLong())
            tvTimeTotal.text = formatTime(duration.toLong())
        } else {
            if (!isUserSeeking) seekProgress.progress = 0
            tvTimeCurrent.text = "00:00"
            tvTimeTotal.text = getString(R.string.unknown_duration)
        }
        if (isExpanded) updateExpandedProgress()
    }

    private fun updateExpandedProgress() {
        val duration = engine.durationMs
        if (duration > 0) {
            if (!isExpandedSeeking) {
                exSeek.progress =
                    ((engine.currentPositionMs.toFloat() / duration) * 1000f).toInt().coerceIn(0, 1000)
            }
            exTimeCurrent.text = formatTime(engine.currentPositionMs.toLong())
            exTimeTotal.text = formatTime(duration.toLong())
        } else {
            if (!isExpandedSeeking) exSeek.progress = 0
            exTimeCurrent.text = "00:00"
            exTimeTotal.text = getString(R.string.unknown_duration)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            if (engine.isPlaying) tickHandler.postDelayed(this, 250)
        }
    }

    private fun updateTicker() {
        tickHandler.removeCallbacks(ticker)
        if (engine.isPlaying) tickHandler.postDelayed(ticker, 250)
    }

    // ------------------------------------------------------------ 音乐库

    private fun refreshLibrary() {
        val visibleTracks = if (query.isBlank()) {
            allTracks.toList()
        } else {
            allTracks.filter { it.name.contains(query, ignoreCase = true) }
        }

        listContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        visibleTracks.forEach { track ->
            val row = inflater.inflate(R.layout.item_track, listContainer, false)
            val index = allTracks.indexOf(track) + 1
            row.findViewById<TextView>(R.id.tv_index).text =
                String.format(Locale.ROOT, "%02d", index)
            row.findViewById<TextView>(R.id.tv_row_title).text = track.name
            val durationText =
                if (track.durationMs > 0) formatTime(track.durationMs) else getString(R.string.unknown_duration)
            row.findViewById<TextView>(R.id.tv_row_meta).text =
                "$durationText · ${track.extLabel} · ${formatBytes(track.size)}"

            if (track.id == engine.currentTrack?.id) {
                val accent = ThemeColors.accent(settings, this)
                row.background = roundedDrawable(
                    ColorUtils.setAlphaComponent(accent, 0x1F),
                    12f
                )
                row.findViewById<TextView>(R.id.tv_row_title)
                    .setTextColor(ThemeColors.accentLight(settings, this))
                row.findViewById<TextView>(R.id.tv_index)
                    .setTextColor(ThemeColors.accentLight(settings, this))
            }

            row.setOnClickListener {
                maybeRequestAudioPermission()
                engine.playIndex(allTracks.indexOf(track))
            }
            row.findViewById<ImageButton>(R.id.btn_remove).setOnClickListener {
                removeTrack(track)
            }
            listContainer.addView(row)
        }

        val hasAny = allTracks.isNotEmpty()
        emptyState.visibility = if (hasAny) View.GONE else View.VISIBLE
        tvNoResults.visibility = if (hasAny && visibleTracks.isEmpty()) View.VISIBLE else View.GONE
        searchWrap.visibility = if (allTracks.size > 3) View.VISIBLE else View.GONE

        tvCount.text = "${allTracks.size} ${getString(R.string.tracks_unit)}"
        applyThemeColors()
    }

    /** 播放顺序按钮：三种模式共用同一个按钮，图标随模式变化 */
    private fun applyRepeatButton(button: ImageButton, badge: TextView) {
        badge.visibility = View.GONE
        val icon: Int
        val label: Int
        when (engine.repeatMode) {
            PlayerEngine.RepeatMode.SHUFFLE -> {
                icon = R.drawable.ic_shuffle
                label = R.string.mode_shuffle
            }
            PlayerEngine.RepeatMode.LOOP_ONE -> {
                icon = R.drawable.ic_repeat_one
                label = R.string.mode_repeat_one
            }
            else -> {
                icon = R.drawable.ic_repeat
                label = R.string.mode_repeat_all
            }
        }
        button.setImageResource(icon)
        button.contentDescription = getString(label)
        button.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.primary_light)
        )
    }

    private var discLabelBitmap: Bitmap? = null
    private var discLabelLoadedPath = ""

    /** 唱片内圈图片：路径变化时才重新解码，避免每次 onResume 都读盘 */
    private fun applyDiscLabel() {
        val path = settings.discLabelPath
        if (path == discLabelLoadedPath) return
        val previous = discLabelBitmap
        val bitmap = if (path.isNotBlank() && File(path).exists()) decodeBitmap(path, 512) else null
        discLabelBitmap = bitmap
        discLabelLoadedPath = path
        discView.labelBitmap = bitmap
        exDiscView.labelBitmap = bitmap
        if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
    }

    /** 按 2 的幂降采样解码，用于内圈这类小尺寸图片 */
    private fun decodeBitmap(path: String, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    /** 把强调色 / 文字颜色应用到整棵视图树（列表行是动态创建的，所以每次重建后都要再刷一次） */
    private fun applyThemeColors() {
        ThemeColors.apply(
            findViewById(R.id.root),
            settings,
            this,
            appliedAccent,
            appliedText
        )
        appliedAccent = settings.accentColor
        appliedText = settings.textColor
    }

    private fun openPicker() {
        pickAudio.launch(arrayOf("audio/*"))
    }

    private fun importTracks(uris: List<Uri>) {
        btnImport.isEnabled = false
        btnEmptyImport.isEnabled = false

        Thread {
            val result = store.import(uris)
            runOnUiThread {
                // 导入期间页面可能已经关闭，避免对已销毁的界面做更新
                if (isFinishing || isDestroyed) return@runOnUiThread
                allTracks.addAll(result.tracks)
                store.save(allTracks)
                engine.setTracks(allTracks)
                refreshLibrary()
                applyPlaybackState()

                btnImport.isEnabled = true
                btnEmptyImport.isEnabled = true

                if (result.tracks.isEmpty() && result.skipped > 0) {
                    Toast.makeText(this, R.string.error_unsupported, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun removeTrack(track: Track) {
        allTracks.removeAll { it.id == track.id }
        store.delete(track)
        store.save(allTracks)
        engine.setTracks(allTracks)
        lastRenderedTrackId = engine.currentTrack?.id
        refreshLibrary()
        applyPlaybackState()
    }

    // ------------------------------------------------------------ 全屏展开

    private fun expandPlayer() {
        if (isExpanded) return
        isExpanded = true
        applyExpandedState()
        applyPlaybackState()

        expandedOverlay.visibility = View.VISIBLE
        // 全屏面板是半透明的，壁纸需要移到内容之上、面板之下才能透出来
        moveWallpaperForFullscreen(expanded = true)
        expandedScrim.alpha = 0f

        // 用缩放 + 位移做过渡（GPU 层完成，不触发重新布局），比逐帧改 LayoutParams 顺滑得多
        expandedSheet.alpha = 0f
        expandedSheet.post {
            val start = cardRectOnScreen()
            val fullWidth = expandedSheet.width.coerceAtLeast(1).toFloat()
            val fullHeight = expandedSheet.height.coerceAtLeast(1).toFloat()
            expandedSheet.pivotX = 0f
            expandedSheet.pivotY = 0f
            expandedSheet.scaleX = start.width() / fullWidth
            expandedSheet.scaleY = start.height() / fullHeight
            expandedSheet.translationX = start.left.toFloat()
            expandedSheet.translationY = start.top.toFloat()
            expandedSheet.alpha = 1f
            expandedSheet.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(TRANSITION_MS.toLong())
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
            expandedScrim.animate().alpha(1f).setDuration(TRANSITION_MS.toLong()).start()
        }
    }

    private fun collapsePlayer() {
        if (!isExpanded) return
        isExpanded = false
        applyPlaybackState()
        // 先把壁纸放回内容下方，收起动画过程中就能看到主界面
        moveWallpaperForFullscreen(expanded = false)

        val end = cardRectOnScreen()
        val fullWidth = expandedSheet.width.coerceAtLeast(1).toFloat()
        val fullHeight = expandedSheet.height.coerceAtLeast(1).toFloat()
        expandedSheet.animate()
            .scaleX(end.width() / fullWidth)
            .scaleY(end.height() / fullHeight)
            .translationX(end.left.toFloat())
            .translationY(end.top.toFloat())
            .setDuration(TRANSITION_MS.toLong())
            .setInterpolator(DecelerateInterpolator(1.4f))
            .withEndAction {
                expandedOverlay.visibility = View.GONE
                expandedScrim.alpha = 0f
                resetSheetTransform()
            }
            .start()
        expandedScrim.animate().alpha(0f).setDuration(TRANSITION_MS.toLong()).start()
    }

    private fun resetSheetTransform() {
        expandedSheet.animate().cancel()
        expandedSheet.scaleX = 1f
        expandedSheet.scaleY = 1f
        expandedSheet.translationX = 0f
        expandedSheet.translationY = 0f
    }

    private fun cardRectOnScreen(): Rect {
        val cardLocation = IntArray(2)
        playerCard.getLocationOnScreen(cardLocation)
        val rootLocation = IntArray(2)
        findViewById<View>(R.id.root).getLocationOnScreen(rootLocation)
        val left = cardLocation[0] - rootLocation[0]
        val top = cardLocation[1] - rootLocation[1]
        return Rect(left, top, left + playerCard.width, top + playerCard.height)
    }



    /** 全屏时把粒子/光晕的发射中心对齐到唱片圆心（唱片隐藏时用可视化区域中心） */
    private fun updateExpandedEffectOrigin() {
        if (expandedSheet.width == 0) return
        val anchor = if (settings.hideDisc) exVisualizer else exDiscContainer
        if (anchor.width == 0) return
        val sheetLocation = IntArray(2)
        expandedSheet.getLocationInWindow(sheetLocation)
        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        val centerX = anchorLocation[0] - sheetLocation[0] + anchor.width / 2f
        val centerY = anchorLocation[1] - sheetLocation[1] + anchor.height / 2f
        val radius = if (settings.hideDisc) anchor.width * 0.28f else anchor.width / 2f
        exParticle.setDiscOrigin(true, centerX, centerY, radius)
    }

    private fun applyExpandedState() {
        val track = engine.currentTrack
        val hasTrack = track != null
        exTrackTitle.text = track?.name ?: getString(R.string.no_track)
        exTrackSub.text = if (track != null) {
            "${getString(R.string.local_audio)} · ${track.extLabel} · ${formatBytes(track.size)}"
        } else {
            getString(R.string.no_track_body)
        }

        exSeek.isEnabled = hasTrack && engine.durationMs > 0
        exBtnPlay.isEnabled = hasTrack
        exBtnPrev.isEnabled = hasTrack
        exBtnNext.isEnabled = hasTrack
        exBtnPlay.setImageResource(if (engine.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        applyRepeatButton(exBtnRepeat, exRepeatBadge)
        exDiscView.hasTrack = hasTrack
        exDiscView.isPlaying = engine.isPlaying && hasTrack
        exDiscView.hue = hueFor(track)
        exDiscView.style = if (settings.discMode == PlayerSettings.DISC_COLOR) {
            DiscView.DiscStyle.COLOR
        } else {
            DiscView.DiscStyle.BLACK
        }
        updateExpandedProgress()
    }

    // ------------------------------------------------------------ 工具

    private fun maybeRequestAudioPermission() {
        if (engine.hasAudioPermission() || permissionRequested) return
        permissionRequested = true
        requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun hueFor(track: Track?): Float {
        if (track == null) return 220f
        val hash = track.name.hashCode() * 31 + track.id.hashCode()
        return (abs(hash) % 360).toFloat()
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp)
            setColor(color)
        }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && currentFocus !is EditText) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    if (engine.currentTrack != null) {
                        engine.toggle()
                        return true
                    }
                }
                KeyEvent.KEYCODE_A -> {
                    if (engine.tracks.isNotEmpty()) {
                        engine.previous()
                        return true
                    }
                }
                KeyEvent.KEYCODE_D -> {
                    if (engine.tracks.isNotEmpty()) {
                        engine.next()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        /** 虚化程度 100 时对应的最大模糊半径（dp） */
        const val MAX_BLUR_DP = 8f

        /** 弧线虚化程度 100 时对应的最大模糊半径（dp） */
        const val MAX_ARC_BLUR_DP = 10f

        /** 展开 / 收起动画时长（毫秒） */
        const val TRANSITION_MS = 380
    }
}
