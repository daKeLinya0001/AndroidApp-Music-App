package app.endlessmusic.player.play

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.endlessmusic.player.MainActivity
import app.endlessmusic.player.R

/**
 * 前台服务：播放时把进程提升为前台，避免退到后台后被系统回收，
 * 同时在通知栏提供 上一首 / 播放暂停 / 下一首 控制。
 *
 * 播放引擎本身仍是单例，服务只负责「保活 + 通知」，不持有播放状态。
 */
class PlaybackService : Service() {

    private lateinit var engine: PlayerEngine
    private val engineListener: () -> Unit = { refreshNotification() }

    override fun onCreate() {
        super.onCreate()
        engine = PlayerEngine.get(this)
        createChannel()
        engine.addListener(engineListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.toggle()
            ACTION_NEXT -> engine.next()
            ACTION_PREV -> engine.previous()
            ACTION_STOP -> {
                engine.toggle() // 如果正在播放，先暂停，行为与「移除通知」一致
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 必须在 startForegroundService 之后 5 秒内进入前台；
        // 系统限制（如后台启动）导致失败时直接结束服务，不要崩溃
        val started = runCatching {
            startForeground(NOTIFICATION_ID, buildNotification())
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        refreshNotification()
        return START_STICKY
    }

    private fun refreshNotification() {
        if (engine.currentTrack == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val track = engine.currentTrack
        val playing = engine.isPlaying
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(track?.name ?: getString(R.string.no_track))
            .setContentText(
                getString(if (playing) R.string.notification_playing else R.string.notification_paused)
            )
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(serviceIntent(ACTION_STOP, REQUEST_STOP))
            .addAction(R.drawable.ic_prev, getString(R.string.previous), serviceIntent(ACTION_PREV, REQUEST_PREV))
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (playing) R.string.pause else R.string.play),
                serviceIntent(ACTION_TOGGLE, REQUEST_TOGGLE)
            )
            .addAction(R.drawable.ic_next, getString(R.string.next), serviceIntent(ACTION_NEXT, REQUEST_NEXT))

        if (engine.durationMs > 0) {
            builder.setProgress(
                engine.durationMs,
                engine.currentPositionMs.coerceIn(0, engine.durationMs),
                false
            )
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        engine.removeListener(engineListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_TOGGLE = "app.endlessmusic.player.action.TOGGLE"
        private const val ACTION_NEXT = "app.endlessmusic.player.action.NEXT"
        private const val ACTION_PREV = "app.endlessmusic.player.action.PREV"
        private const val ACTION_STOP = "app.endlessmusic.player.action.STOP"

        private const val REQUEST_OPEN = 10
        private const val REQUEST_TOGGLE = 11
        private const val REQUEST_NEXT = 12
        private const val REQUEST_PREV = 13
        private const val REQUEST_STOP = 14

        /** 播放开始时调用，把服务提升为前台 */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }
    }
}
