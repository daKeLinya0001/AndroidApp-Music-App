package com.example.musicapp.play

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import com.example.musicapp.data.Track
import com.example.musicapp.data.TrackStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 全局唯一的播放引擎。
 *
 * - 使用 MediaPlayer 播放应用私有目录中的音频
 * - 通过 Visualizer 读取真实 FFT 数据，驱动 32 根频谱柱
 * - 状态保存在内存中，Activity 重建时音乐不中断（对应插件「切换面板音乐继续播放」）
 */
class PlayerEngine private constructor(private val context: Context) {

    /** 播放顺序：列表循环 / 列表随机 / 单曲循环 */
    enum class RepeatMode { LOOP_ALL, SHUFFLE, LOOP_ONE }

    private val store = TrackStore(context)
    private val prefs = context.getSharedPreferences("music_player", Context.MODE_PRIVATE)

    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null

    var tracks: List<Track> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var isPlaying: Boolean = false
        private set

    var isPreparing: Boolean = false
        private set

    var repeatMode: RepeatMode = RepeatMode.LOOP_ALL
        private set

    /** 非空表示最近一次播放失败 */
    var errorMessage: String? = null
        private set

    var durationMs: Int = 0
        private set

    var currentPositionMs: Int = 0
        private set

    /** Visualizer 回调线程写入，UI 线程读取 */
    @Volatile
    var latestSpectrum: FloatArray? = null
        private set

    /** 状态变化监听（总在主线程）；界面与通知栏各注册一个，互不覆盖 */
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun clearError() {
        errorMessage = null
    }

    init {
        repeatMode = runCatching {
            when (val saved = prefs.getString(KEY_REPEAT, null)) {
                null -> RepeatMode.LOOP_ALL
                // 兼容旧版本保存的 OFF / ALL
                "OFF", "ALL" -> RepeatMode.LOOP_ALL
                "ONE" -> RepeatMode.LOOP_ONE
                else -> RepeatMode.valueOf(saved)
            }
        }.getOrDefault(RepeatMode.LOOP_ALL)
    }

    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)

    // ---------------------------------------------------------------- 音乐库

    /** 首次进入时恢复上次曲目（不自动播放），对应插件的 LS_LAST 行为。 */
    fun restore(tracks: List<Track>) {
        this.tracks = tracks
        val lastId = prefs.getString(KEY_LAST, null)
        val index = tracks.indexOfFirst { it.id == lastId }
        currentIndex = if (index >= 0) index else -1
        durationMs = tracks.getOrNull(currentIndex)?.durationMs?.toInt() ?: 0
        currentPositionMs = 0
        notifyState()
    }

    fun setTracks(tracks: List<Track>) {
        this.tracks = tracks
        if (currentIndex !in tracks.indices) {
            stopPlayback()
            currentIndex = -1
            durationMs = 0
            currentPositionMs = 0
        }
        notifyState()
    }

    // ---------------------------------------------------------------- 播放控制

    fun playIndex(index: Int) {
        if (index !in tracks.indices) return
        currentIndex = index
        errorMessage = null
        prefs.edit().putString(KEY_LAST, tracks[index].id).commit()
        startPlayer(tracks[index])
    }

    fun toggle() {
        val current = player
        if (current == null) {
            if (currentIndex >= 0) playIndex(currentIndex) else if (tracks.isNotEmpty()) playIndex(0)
            return
        }
        try {
            if (current.isPlaying) {
                current.pause()
                isPlaying = false
            } else {
                current.start()
                isPlaying = true
            }
        } catch (_: IllegalStateException) {
            /* 准备中或已释放，忽略 */
        }
        notifyState()
    }

    fun next(userInitiated: Boolean = true) {
        if (tracks.isEmpty()) return
        if (repeatMode == RepeatMode.SHUFFLE) {
            playIndex(randomIndex())
            return
        }
        var target = currentIndex + 1
        if (target > tracks.lastIndex) target = 0
        playIndex(target)
    }

    fun previous() {
        if (tracks.isEmpty()) return
        pollPosition()
        if (player != null && currentPositionMs > RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }
        if (repeatMode == RepeatMode.SHUFFLE) {
            playIndex(randomIndex())
            return
        }
        val target = if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1
        playIndex(target)
    }

    /** 随机挑一首与当前不同的曲目 */
    private fun randomIndex(): Int {
        if (tracks.size <= 1) return 0
        var index = currentIndex
        while (index == currentIndex) index = Random.nextInt(tracks.size)
        return index
    }

    fun seekTo(positionMs: Int) {
        val clamped = positionMs.coerceIn(0, max(0, durationMs))
        runCatching { player?.seekTo(clamped) }
        currentPositionMs = clamped
        notifyState()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.LOOP_ALL -> RepeatMode.SHUFFLE
            RepeatMode.SHUFFLE -> RepeatMode.LOOP_ONE
            RepeatMode.LOOP_ONE -> RepeatMode.LOOP_ALL
        }
        prefs.edit().putString(KEY_REPEAT, repeatMode.name).commit()
        notifyState()
    }

    /** 由 UI 定时器调用，刷新当前位置。 */
    fun pollPosition() {
        val current = player ?: return
        if (durationMs <= 0) return
        currentPositionMs = runCatching { current.currentPosition }.getOrDefault(currentPositionMs)
    }

    // ---------------------------------------------------------------- 频谱

    fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun enableVisualizerIfPossible() {
        val current = player ?: return
        if (player != null && visualizer == null) attachVisualizer(current)
    }

    // ---------------------------------------------------------------- 内部实现

    private fun startPlayer(track: Track) {
        releasePlayer()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        isPreparing = true
        isPlaying = false
        currentPositionMs = 0
        durationMs = track.durationMs.toInt()

        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        // 不调用 setVolume：音量完全由系统媒体音量（STREAM_MUSIC）决定，
        // 按压机身音量键调整的就是它。

        mediaPlayer.setOnPreparedListener { prepared ->
            isPreparing = false
            durationMs = prepared.duration.coerceAtLeast(0)
            prepared.start()
            isPlaying = true
            attachVisualizer(prepared)
            notifyState()
        }
        mediaPlayer.setOnCompletionListener { handleCompletion() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            isPreparing = false
            isPlaying = false
            errorMessage = "playback"
            notifyState()
            true
        }

        try {
            mediaPlayer.setDataSource(store.fileFor(track).absolutePath)
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            // 数据源无效时把播放器彻底释放，避免残留一个不可用的实例
            runCatching { mediaPlayer.release() }
            player = null
            isPreparing = false
            isPlaying = false
            errorMessage = "playback"
        }
        notifyState()
    }

    private fun handleCompletion() {
        when (repeatMode) {
            RepeatMode.LOOP_ONE -> {
                runCatching {
                    player?.seekTo(0)
                    player?.start()
                }
                currentPositionMs = 0
                isPlaying = true
                notifyState()
            }
            else -> next(userInitiated = false)
        }
    }

    private fun stopPlayback() {
        releasePlayer()
        isPlaying = false
    }

    private fun releasePlayer() {
        releaseVisualizer()
        latestSpectrum = null
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        isPlaying = false
        isPreparing = false
    }

    private fun notifyState() {
        listeners.forEach { listener -> runCatching { listener() } }
    }

    // ---------------------------------------------------------------- Visualizer

    private fun attachVisualizer(mediaPlayer: MediaPlayer) {
        releaseVisualizer()
        latestSpectrum = null
        if (!hasAudioPermission()) return
        try {
            @Suppress("DEPRECATION")
            val fx = Visualizer(mediaPlayer.audioSessionId)
            fx.captureSize = Visualizer.getCaptureSizeRange()[1]
            fx.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < BAR_COUNT) return
                        latestSpectrum = fftToBars(fft)
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )
            @Suppress("DEPRECATION")
            fx.enabled = true
            visualizer = fx
        } catch (_: Throwable) {
            visualizer = null
            latestSpectrum = null
        }
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    /** 把 Visualizer 的 FFT 数据压成 32 根柱子的 0..1 能量值。 */
    private fun fftToBars(fft: ByteArray): FloatArray {
        val bins = fft.size / 2
        val result = FloatArray(BAR_COUNT)
        val top = max(2, bins / 3)
        for (i in 0 until BAR_COUNT) {
            val start = 1 + i * top / BAR_COUNT
            val end = max(start + 1, 1 + (i + 1) * top / BAR_COUNT)
            var sum = 0.0
            for (bin in start until min(end, bins)) {
                val real = fft[bin * 2].toInt()
                val imag = fft[bin * 2 + 1].toInt()
                sum += sqrt((real * real + imag * imag).toDouble())
            }
            val magnitude = sum / (end - start) / 128.0
            // 不做硬截断：低频能量常常远高于此，截断会让那几根柱子变成常数
            result[i] = magnitude.pow(0.62).toFloat()
        }
        return result
    }

    companion object {
        private const val KEY_REPEAT = "repeat"
        private const val KEY_LAST = "last"
        private const val RESTART_THRESHOLD_MS = 3000
        private const val BAR_COUNT = 32

        @Volatile
        private var instance: PlayerEngine? = null

        fun get(context: Context): PlayerEngine = instance ?: synchronized(this) {
            instance ?: PlayerEngine(context.applicationContext).also { instance = it }
        }
    }
}
