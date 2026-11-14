package com.example.musicapp

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.util.Log
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
import android.media.MediaMetadataRetriever
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
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.content.SharedPreferences
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.example.musicapp.play.LyricLine
import com.example.musicapp.play.LyricsRepository
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
    private lateinit var particleFullscreen: ParticleView
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
    private lateinit var btnUser: ImageButton
    private lateinit var tvHeaderUsername: TextView
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
    private lateinit var exLyricsCurrent: TextView
    private lateinit var exLyricsNext: TextView
    private lateinit var exLyricsBlock: LinearLayout
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
    /** 已设置视频但 TextureView 的 Surface 还没就绪，等 onSurfaceTextureAvailable 再启动 */
    private var wallpaperVideoPending = false
    /** 复用同一个 Surface 与播放器：重复换视频时，旧播放器 release 会把新 Surface detach 掉，画面就没了 */
    private var wallpaperSurface: Surface? = null
    private var wallpaperSurfaceTexture: SurfaceTexture? = null
    /** 视频实际显示尺寸（已按旋转元数据换算），用于等比铺满而不是被压扁 */
    private var videoAspectWidth = 0
    private var videoAspectHeight = 0
    private var wallpaperVideoRetried = false
    private val videoLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        runCatching { applyVideoTransform() }
    }

    private val tickHandler = Handler(Looper.getMainLooper())
    private var rotationAnimator: ValueAnimator? = null
    private var launchOverlay: View? = null
    private var launchAnimator: ValueAnimator? = null
    private var welcomeOverlay: View? = null

    private val lyricsRepository by lazy { LyricsRepository(this) }
    private var lyricTrackId = ""
    private var lyricLines: List<LyricLine> = emptyList()
    private var lyricIndex = -1
    private var lyricRequestToken = 0
    private var appliedLyricOffset = Int.MIN_VALUE

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
                if (welcomeOverlay != null) {
                    // 欢迎页必须填完用户名才能离开
                    return
                }
                if (launchOverlay != null) {
                    // 启动动画期间按返回键：只跳过动画，不退出应用
                    finishLaunchAnimation()
                } else if (isExpanded) {
                    collapsePlayer()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // 启动动画只在冷启动（进程重建）时播放，转屏/换主题不会重复
        maybeShowLaunchAnimation(savedInstanceState)
        if (launchOverlay == null) maybeShowWelcome()
    }

    /** 启动动画期间由 Activity 统一拦截点击：命中浮层的空白处也一定能跳过 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (launchOverlay != null) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                finishLaunchAnimation()
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
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
        refreshHeaderUser()
    }

    /** 顶栏用户名：改了用户名从用户页返回后立即同步 */
    private fun refreshHeaderUser() {
        val name = WelcomePrefs.username(this)
        tvHeaderUsername.text = name
        tvHeaderUsername.visibility = if (name.isBlank()) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        wallpaperPlayer?.let { runCatching { it.pause() } }
        super.onPause()
    }

    override fun onDestroy() {
        tickHandler.removeCallbacks(ticker)
        rotationAnimator?.cancel()
        rotationAnimator = null
        launchAnimator?.cancel()
        launchAnimator = null
        launchOverlay?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        launchOverlay = null
        releaseWallpaperVideo()
        engine.removeListener(engineListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------ 初始化

    private fun bindViews() {
        scroll = findViewById(R.id.scroll)
        spectrumPlayer = findViewById(R.id.spectrum_player)
        particleView = findViewById(R.id.particle_view)
        particleFullscreen = findViewById(R.id.particle_fullscreen)
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
        btnUser = findViewById(R.id.btn_user)
        tvHeaderUsername = findViewById(R.id.tv_header_username)
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
                    // 新的 SurfaceTexture：旧的 Surface 必须一起释放，否则会拿到失效的 Surface
                    releaseWallpaperVideo()
                    releaseWallpaperSurface()
                    startWallpaperVideo(settings.wallpaperPath)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                runCatching { applyVideoTransform() }
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseWallpaperVideo()
                releaseWallpaperSurface()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        expandedContent = findViewById(R.id.expanded_content)
        exDiscView = findViewById(R.id.ex_disc_view)
        exSpectrum = findViewById(R.id.ex_spectrum)
        exLyricsCurrent = findViewById(R.id.ex_lyrics_current)
        exLyricsNext = findViewById(R.id.ex_lyrics_next)
        exLyricsBlock = findViewById(R.id.ex_lyrics_block)
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
        btnUser.setOnClickListener {
            startActivity(Intent(this, UserActivity::class.java))
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

    // ------------------------------------------------------------ 首次启动欢迎页

    /** 首次安装（或卸载重装）后展示一次：必须填写用户名才能进入主界面 */
    private fun maybeShowWelcome() {
        if (welcomeOverlay != null || isFinishing) return
        if (WelcomePrefs.isDone(this)) return

        val container = findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = layoutInflater.inflate(R.layout.view_welcome, container, false)
        val input = overlay.findViewById<EditText>(R.id.welcome_input)
        val error = overlay.findViewById<TextView>(R.id.welcome_error)
        val confirm = overlay.findViewById<View>(R.id.welcome_confirm)

        confirm.setOnClickListener { submitWelcome(input, error) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitWelcome(input, error)
                true
            } else {
                false
            }
        }

        container.addView(overlay)
        welcomeOverlay = overlay
        // 抢走焦点：否则底层按钮仍是焦点态，键入的字符会落到主界面上
        overlay.isFocusableInTouchMode = true
        overlay.requestFocus()
        input.requestFocus()
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(220L).start()
    }

    private fun submitWelcome(input: EditText, error: TextView) {
        val name = input.text.toString()
        when {
            name.isEmpty() -> {
                showWelcomeError(error, getString(R.string.welcome_error_empty))
                return
            }
            name.any { it.isWhitespace() } -> {
                showWelcomeError(error, getString(R.string.welcome_error_space))
                return
            }
        }

        WelcomePrefs.complete(this, name)

        hideKeyboard()
        val overlay = welcomeOverlay ?: return
        overlay.animate().alpha(0f).setDuration(200L).withEndAction {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            welcomeOverlay = null
        }.start()
    }

    private fun showWelcomeError(error: TextView, message: String) {
        error.text = message
        error.visibility = View.VISIBLE
        error.alpha = 0f
        error.animate().alpha(1f).setDuration(120L).start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    // ------------------------------------------------------------ 启动动画

    /**
     * 冷启动动画：纯白背景上「Endless Music」虚化淡出，随后黑色音符浮现。
     *
     * 只在 savedInstanceState == null（进程重建后的首次创建）时播放；
     * 转屏、切换主题触发的 recreate() 不会重复。浮层挂在 android.R.id.content 上，
     * 覆盖状态栏区域；点击任意位置可跳过。动画结束自动移除，不影响主界面。
     */
    private fun maybeShowLaunchAnimation(savedState: Bundle?) {
        if (savedState != null || launchOverlay != null) return
        val container = findViewById<ViewGroup>(android.R.id.content) ?: return
        val overlay = layoutInflater.inflate(R.layout.view_launch_animation, container, false)
        val text = overlay.findViewById<View>(R.id.launch_text)
        val note = overlay.findViewById<View>(R.id.launch_note)
        overlay.setOnClickListener { finishLaunchAnimation() }
        container.addView(overlay)
        launchOverlay = overlay

        // 预览按 1080px 宽设计，这里按屏宽等比缩放模糊半径，保证各分辨率观感一致
        val widthScale = resources.displayMetrics.widthPixels / 1080f
        val textBlur = 46f * widthScale
        val noteBlur = 16f * widthScale

        fun render(fraction: Float) {
            val f = fraction.coerceIn(0f, 1f)

            // 文字：0–0.09 淡入，0.18 起虚化淡出，0.51 完全消失
            val appear = easeOutCubic((f / 0.09f).coerceIn(0f, 1f))
            val fade = easeInCubic(((f - 0.18f) / 0.33f).coerceIn(0f, 1f))
            text.alpha = (appear * (1f - fade)).coerceIn(0f, 1f)
            val textScale = 1f + 0.05f * fade
            text.scaleX = textScale
            text.scaleY = textScale
            applyLaunchBlur(text, textBlur * fade)

            // 音符：0.43–0.80 由虚到实浮现
            val noteProgress = easeInOutCubic(((f - 0.43f) / 0.37f).coerceIn(0f, 1f))
            note.alpha = (noteProgress * 1.1f).coerceIn(0f, 1f)
            val noteScale = 0.94f + 0.06f * noteProgress
            note.scaleX = noteScale
            note.scaleY = noteScale
            applyLaunchBlur(note, noteBlur * (1f - noteProgress))

            // 最后 8% 淡出白层，交还主界面
            overlay.alpha = 1f - easeInCubic(((f - 0.92f) / 0.08f).coerceIn(0f, 1f))

            if (f >= 1f) removeLaunchAnimation()
        }

        render(0f)
        launchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LAUNCH_ANIMATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { render(it.animatedValue as Float) }
            start()
        }
    }

    /** Android 12+ 用真高斯模糊；更低版本降级为透明度 + 缩放，不做模糊 */
    private fun applyLaunchBlur(view: View, radiusPx: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (radiusPx <= 0.6f) {
            view.setRenderEffect(null)
        } else {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun finishLaunchAnimation() {
        val overlay = launchOverlay ?: return
        launchAnimator?.cancel()
        launchAnimator = null
        overlay.animate().alpha(0f).setDuration(160L).withEndAction { removeLaunchAnimation() }.start()
    }

    private fun removeLaunchAnimation() {
        launchAnimator?.cancel()
        launchAnimator = null
        launchOverlay?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        launchOverlay = null
        maybeShowWelcome()
    }

    private fun easeOutCubic(x: Float): Float {
        val v = 1f - x
        return 1f - v * v * v
    }

    private fun easeInCubic(x: Float): Float = x * x * x

    private fun easeInOutCubic(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

    // ------------------------------------------------------------ 设置应用

    private fun applySettings() {
        // 歌词延迟变化时强制刷新当前行
        if (appliedLyricOffset != settings.lyricOffsetMs) {
            appliedLyricOffset = settings.lyricOffsetMs
            lyricIndex = -2
        }
        discView.style = if (settings.discMode == PlayerSettings.DISC_COLOR) {
            DiscView.DiscStyle.COLOR
        } else {
            DiscView.DiscStyle.BLACK
        }
        // 隐藏黑胶唱片：主界面与全屏同时隐藏唱盘容器
        discContainer.visibility = if (settings.hideDisc) View.GONE else View.VISIBLE
        exDiscContainer.visibility = if (settings.hideDisc) View.GONE else View.VISIBLE
        applyDiscLabel()
        // 歌词默认关闭，只有开关打开时才显示并联网匹配
        exLyricsBlock.visibility = if (settings.lyricsEnabled) View.VISIBLE else View.GONE

        spectrumPlayer.visibility = if (settings.spectrumEnabled) View.VISIBLE else View.GONE
        spectrumPlayer.barColor = resolvedBarColor()
        spectrumPlayer.barOpacity = settings.barOpacity
        spectrumPlayer.maxBarHeightDp = settings.maxBarHeight
        spectrumPlayer.speed = settings.barSpeed
        spectrumPlayer.sparksEnabled = !settings.particleFullscreen
        spectrumPlayer.provider = { engine.latestSpectrum }
        spectrumPlayer.onBass = { bass ->
            val scale = 1f + bass * 0.06f
            discContainer.scaleX = scale
            discContainer.scaleY = scale
        }

        // 粒子与光晕：颜色默认跟随频谱柱，也可在设置里自定义；
        // 开启「全屏粒子特效」时用铺满整屏的粒子层替代卡片内的那层
        applyParticleLayers()

        particleView.particleColor = resolvedParticleColor()
        particleView.particleSize = settings.particleSize
        particleView.glowSize = settings.glowSize
        particleView.particleCount = settings.particleCount
        particleView.provider = { engine.latestSpectrum }

        particleFullscreen.particleColor = resolvedParticleColor()
        particleFullscreen.particleSize = settings.particleSize
        particleFullscreen.glowSize = settings.glowSize
        particleFullscreen.particleCount = settings.particleCount
        particleFullscreen.provider = { engine.latestSpectrum }

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
        exSpectrum.sparksEnabled = !settings.particleFullscreen
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
        exParticle.particleCount = settings.particleCount
        exParticle.provider = { engine.latestSpectrum }

        spectrumLibrary.barColor = resolvedBarColor()
        spectrumLibrary.barOpacity = settings.barOpacity
        spectrumLibrary.speed = settings.barSpeed
        spectrumLibrary.sparksEnabled = !settings.particleFullscreen
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
            wallpaperVideoRetried = false
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
        if (wallpaperPlayer != null && wallpaperVideoPath == path && !wallpaperVideoPending) return true
        if (wallpaperVideoPending && wallpaperVideoPath == path) return true

        // 无论换源还是首次播放，都先把旧解码器和 Surface 彻底释放。
        // 连续换视频时直接复用 SurfaceTexture，会让新解码器拿到还没解绑的 Surface，
        // 表现为 MediaPlayer 报 -38 然后把壁纸层隐藏（这就是「多次上传后消失」）。
        releaseWallpaperVideo()
        releaseWallpaperSurface()

        wallpaperVideoPath = path
        wallpaperVideoPending = true
        wallpaperVideo.visibility = View.VISIBLE

        // 延迟几帧再建 Surface，给旧的解码器留出解绑时间
        wallpaperVideo.removeCallbacks(pendingVideoStart)
        wallpaperVideo.postDelayed(pendingVideoStart, 180L)
        return true
    }

    private val pendingVideoStart = Runnable {
        val path = wallpaperVideoPath
        if (path.isBlank()) return@Runnable
        if (settings.wallpaperType != "video" || settings.wallpaperPath != path) return@Runnable
        setupWallpaperVideo(path)
    }

    private fun setupWallpaperVideo(path: String) {
        val texture = wallpaperVideo.surfaceTexture ?: return // 等 onSurfaceTextureAvailable 回调

        val surface = Surface(texture)
        wallpaperSurface = surface
        wallpaperSurfaceTexture = texture

        videoAspectWidth = 0
        videoAspectHeight = 0
        readVideoSize(path)?.let { (width, height) ->
            videoAspectWidth = width
            videoAspectHeight = height
        }

        val player = MediaPlayer()
        wallpaperPlayer = player

        val setup = runCatching {
            player.setSurface(surface)
            player.setDataSource(path)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener { mp ->
                wallpaperVideoPending = false
                wallpaperVideoRetried = false
                if (videoAspectWidth == 0 || videoAspectHeight == 0) {
                    videoAspectWidth = mp.videoWidth
                    videoAspectHeight = mp.videoHeight
                }
                runCatching {
                    applyVideoTransform()
                    mp.start()
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra retried=$wallpaperVideoRetried")
                wallpaperVideoPending = false
                runCatching { releaseWallpaperVideo() }
                runCatching { releaseWallpaperSurface() }
                val canRetry = !wallpaperVideoRetried &&
                    settings.wallpaperType == "video" &&
                    settings.wallpaperPath == path
                if (canRetry) {
                    // 偶尔第一次 attach 会失败，隔久一点整条链路重建一次
                    wallpaperVideoRetried = true
                    wallpaperVideoPath = path
                    wallpaperVideoPending = true
                    wallpaperVideo.postDelayed(pendingVideoStart, 350L)
                } else {
                    hideWallpaperToDefault()
                }
                true
            }
            // prepareAsync：大视频同步 prepare 会卡住主线程
            player.prepareAsync()
        }

        if (setup.isFailure) {
            Log.w(TAG, "setup failed for $path", setup.exceptionOrNull())
            runCatching { releaseWallpaperVideo() }
            runCatching { releaseWallpaperSurface() }
            hideWallpaperToDefault()
            return
        }

        wallpaperVideoPath = path
        wallpaperVideoPending = true
        wallpaperVideo.removeOnLayoutChangeListener(videoLayoutListener)
        wallpaperVideo.addOnLayoutChangeListener(videoLayoutListener)
    }

    /** 读取视频宽高并换算旋转，避免竖拍视频被当成横屏拉伸 */
    private fun readVideoSize(path: String): Pair<Int, Int>? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (width > 0 && height > 0) {
                if (rotation % 180 == 90) height to width else width to height
            } else {
                null
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /** 视频不可用时退回默认纯色背景 */
    private fun hideWallpaperToDefault() {
        wallpaperVideo.visibility = View.GONE
        wallpaperLayer.visibility = View.GONE
        expandedScrim.setBackgroundResource(R.drawable.bg_screen)
        expandedSheet.setBackgroundResource(R.drawable.bg_card)
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

    /** 视频按取景框裁剪：把归一化矩形映射成缩放+位移矩阵（等比 cover，绝不拉伸） */
    private fun applyVideoTransform() {
        val videoWidth = videoAspectWidth
        val videoHeight = videoAspectHeight
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

        // TextureView 会先把 SurfaceTexture 非等比铺满整个 View（W/vw、H/vh），
        // 这里要把它抵消掉，才能得到真正的等比「铺满裁切」
        val scaleX = scale * videoWidth / viewWidth
        val scaleY = scale * videoHeight / viewHeight

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY)
        matrix.postTranslate(viewWidth / 2f - centerX * scale, viewHeight / 2f - centerY * scale)
        wallpaperVideo.setTransform(matrix)
    }

    /**
     * 全屏与主界面的层级切换。
     *
     * 之前是把壁纸层 removeView/addView 挪到全屏面板下面，但 TextureView 一旦被移除，
     * SurfaceTexture 就会被销毁，视频必然从头开始播，动画过程中还会闪一下。
     * 现在壁纸层永远留在原位，进入全屏时改为把主界面内容隐藏：
     * 这样壁纸在透明面板后面自然透出，视频也不会中断。
     */
    private fun setMainContentForFullscreen(expanded: Boolean) {
        scroll.visibility = if (expanded) View.INVISIBLE else View.VISIBLE
        applyParticleLayers()
    }

    /** 主界面粒子层的显隐（全屏时两层都要隐藏，全屏有自己的粒子层） */
    private fun applyParticleLayers() {
        val mainVisible = !isExpanded && settings.particlesEnabled
        particleView.visibility =
            if (mainVisible && !settings.particleFullscreen) View.VISIBLE else View.GONE
        particleFullscreen.visibility =
            if (mainVisible && settings.particleFullscreen) View.VISIBLE else View.GONE
    }

    private fun releaseWallpaperVideo() {
        wallpaperVideo.removeCallbacks(pendingVideoStart)
        runCatching { wallpaperVideo.removeOnLayoutChangeListener(videoLayoutListener) }
        runCatching { wallpaperPlayer?.release() }
        wallpaperPlayer = null
        wallpaperVideoPath = ""
        wallpaperVideoPending = false
        videoAspectWidth = 0
        videoAspectHeight = 0
    }

    private fun releaseWallpaperSurface() {
        runCatching { wallpaperSurface?.release() }
        wallpaperSurface = null
        wallpaperSurfaceTexture = null
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
        val mainParticlesActive =
            engine.isPlaying && hasTrack && settings.particlesEnabled && !isExpanded
        particleView.isPlaying = mainParticlesActive && !settings.particleFullscreen
        particleFullscreen.isPlaying = mainParticlesActive && settings.particleFullscreen
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

        syncLyrics(track)
        updateProgress()
        updateTicker()
        applyExpandedState()
    }

    // ------------------------------------------------------------ 歌词

    /** 切歌或开始播放时自动去网上匹配一次歌词，结果按曲目缓存 */
    private fun syncLyrics(track: Track?) {
        if (!settings.lyricsEnabled) {
            // 关闭时清空状态，重新打开后会重新匹配
            lyricTrackId = ""
            lyricLines = emptyList()
            lyricIndex = -1
            showLyricText("", "")
            return
        }
        val id = track?.id ?: ""
        if (id == lyricTrackId) return
        lyricTrackId = id
        lyricLines = emptyList()
        lyricIndex = -1
        val token = ++lyricRequestToken

        if (track == null) {
            showLyricText("", "")
            return
        }

        showLyricText(getString(R.string.lyrics_loading), "")
        val name = track.name
        val duration = track.durationMs
        Thread {
            val lines = runCatching { lyricsRepository.load(name, duration) }.getOrNull()
            runOnUiThread {
                if (token != lyricRequestToken || isFinishing || isDestroyed) return@runOnUiThread
                lyricLines = lines ?: emptyList()
                lyricIndex = -1
                if (lyricLines.isEmpty()) {
                    showLyricText(getString(R.string.lyrics_none), "")
                } else {
                    // 强制刷新：否则当播放位置还停在第一句之前时（index 仍是 -1）会一直卡在「匹配中」
                    lyricIndex = -2
                    updateLyricLine(engine.currentPositionMs.toLong())
                }
            }
        }.start()
    }

    private fun showLyricText(current: String, next: String, animate: Boolean = false) {
        if (!animate) {
            exLyricsCurrent.animate().cancel()
            exLyricsNext.animate().cancel()
            exLyricsCurrent.alpha = 1f
            exLyricsCurrent.translationY = 0f
            exLyricsNext.alpha = 1f
            exLyricsNext.translationY = 0f
            exLyricsCurrent.text = current
            exLyricsNext.text = next
            exLyricsNext.visibility = if (next.isEmpty()) View.GONE else View.VISIBLE
            return
        }

        val shift = dp(10f)
        // 当前行：上移淡出 → 换字 → 从下方淡入，形成滚动感
        exLyricsCurrent.animate().cancel()
        exLyricsCurrent.animate()
            .alpha(0f)
            .translationY(-shift)
            .setDuration(110L)
            .withEndAction {
                exLyricsCurrent.text = current
                exLyricsCurrent.translationY = shift
                exLyricsCurrent.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
            }
            .start()

        // 下一行只做淡入淡出，避免抢视线
        exLyricsNext.animate().cancel()
        exLyricsNext.animate()
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                exLyricsNext.text = next
                exLyricsNext.visibility = if (next.isEmpty()) View.GONE else View.VISIBLE
                exLyricsNext.animate().alpha(1f).setDuration(200L).start()
            }
            .start()
    }

    private fun updateLyricLine(positionMs: Long) {
        if (lyricLines.isEmpty()) return
        val adjusted = positionMs + settings.lyricOffsetMs
        val index = lyricLines.indexOfLast { it.timeMs <= adjusted + 100 }
        if (index == lyricIndex) return
        val animate = lyricIndex >= 0
        lyricIndex = index
        // 还没到第一句时，先把第一句显示出来，下一行取第二句
        val current = if (index >= 0) {
            lyricLines.getOrNull(index)?.text.orEmpty()
        } else {
            lyricLines.firstOrNull()?.text.orEmpty()
        }
        val next = lyricLines.getOrNull(if (index >= 0) index + 1 else 1)?.text.orEmpty()
        if (animate && exLyricsCurrent.text == current && exLyricsNext.text == next) return
        showLyricText(current, next, animate)
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
        updateLyricLine(engine.currentPositionMs.toLong())
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
        // 壁纸层始终留在原位（移动 TextureView 会销毁 Surface，视频会从头播且闪一下），
        // 改成把主界面内容临时隐藏，让全屏面板后面的壁纸透出来
        setMainContentForFullscreen(expanded = true)
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
            // 布局完成后再算一次粒子圆心，否则会停留在全屏随机漂浮模式
            expandedSheet.post { updateExpandedEffectOrigin() }
        }
    }

    private fun collapsePlayer() {
        if (!isExpanded) return
        isExpanded = false
        applyPlaybackState()

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
                // 动画结束后再把主界面放回来，过程中不会看到卡片突然出现
                setMainContentForFullscreen(expanded = false)
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
        if (!isExpanded) return
        val anchor = if (settings.hideDisc) exVisualizer else exDiscContainer
        if (anchor.width == 0 || exParticle.width == 0) {
            // 还在测量阶段，下一帧再试
            expandedSheet.post { updateExpandedEffectOrigin() }
            return
        }

        // 用布局坐标累加，而不是 getLocationInWindow：
        // 后者会带上缩放/旋转，唱盘每帧在转，算出来的圆心会漂移，粒子看起来就在乱动
        var x = anchor.x
        var y = anchor.y
        var node: ViewParent? = anchor.parent
        while (node is View) {
            val parentView: View = node
            if (parentView === exParticle) break
            x += parentView.x
            y += parentView.y
            node = parentView.parent
        }

        val centerX = x + anchor.width / 2f
        val centerY = y + anchor.height / 2f
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
        if (isExpanded) updateExpandedEffectOrigin()
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
        const val TAG = "WallpaperVideo"
        /** 虚化程度 100 时对应的最大模糊半径（dp） */
        const val MAX_BLUR_DP = 8f

        /** 弧线虚化程度 100 时对应的最大模糊半径（dp） */
        const val MAX_ARC_BLUR_DP = 10f

        /** 展开 / 收起动画时长（毫秒） */
        const val TRANSITION_MS = 380

        /** 冷启动动画时长（毫秒） */
        const val LAUNCH_ANIMATION_MS = 2000L

        /** 欢迎页只出现一次，卸载重装后才会再次出现 */
        const val WELCOME_PREFS = "welcome"
        const val KEY_WELCOME_DONE = "done"
        const val KEY_USERNAME = "username"
    }
}
