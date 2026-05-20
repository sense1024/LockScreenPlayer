package com.lockscreen.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.concurrent.Executors

/**
 * 前景媒體服務：鎖屏後仍持有 [ExoPlayer]，並透過 [MediaSession] 在鎖屏顯示播放控制。
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val exoPlayer = player ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    if (shouldEnrichMetadata(exoPlayer)) {
                        enrichMediaMetadata(exoPlayer)
                    }
                }
                Player.STATE_ENDED -> {
                    if (exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                        stopForegroundAndSelf()
                    }
                }
                Player.STATE_IDLE -> {
                    if (exoPlayer.mediaItemCount == 0) {
                        stopForegroundAndSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        isPlaybackReady = false
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )
    }

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_PLAYBACK -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_URI -> {
                val uri = intent.data ?: intent.getParcelableExtra(EXTRA_URI, Uri::class.java)
                val title = intent.getStringExtra(EXTRA_TITLE)
                if (uri != null) {
                    playUri(uri, title)
                }
            }
            ACTION_SET_REPEAT_MODE -> {
                val mode = intent.getIntExtra(EXTRA_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                applyRepeatMode(mode)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun applyRepeatMode(@Player.RepeatMode repeatMode: Int) {
        val mode = VideoRepeatMode.fromPlayerValue(repeatMode)
        RepeatModePreference.set(this, mode)
        ensurePlayer().repeatMode = mode.playerValue
    }

    @OptIn(UnstableApi::class)
    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = RepeatModePreference.get(this@PlaybackService).playerValue
                addListener(playerListener)
            }

        val session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(buildSessionActivityPendingIntent())
            .build()
        mediaSession = session
        player = exoPlayer
        return exoPlayer
    }

    private fun buildSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, SessionTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun playUri(uri: Uri, title: String?) {
        if (!UriPlaybackAccess.canRead(this, uri)) {
            failPlaybackUri(uri)
            return
        }

        val exoPlayer = ensurePlayer()
        val displayTitle = title
            ?: uri.lastPathSegment
            ?: getString(com.lockscreen.player.R.string.playback_notification_title)

        PlaybackSessionStore.setSession(this, uri, displayTitle)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .build(),
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isPlaybackReady = true

        loadArtworkAsync(uri, displayTitle)
    }

    private fun failPlaybackUri(uri: Uri) {
        Log.w(TAG, "Cannot read playback URI: $uri")
        isPlaybackReady = false
        player?.apply {
            stop()
            clearMediaItems()
        }
        PlaybackSessionStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        sendPlaybackUriFailed()
        notifySessionUpdated()
    }

    private fun sendPlaybackUriFailed() {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_URI_FAILED).apply {
                setPackage(packageName)
            },
        )
    }

    private fun notifySessionUpdated() {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_SESSION_UPDATED).apply {
                setPackage(packageName)
            },
        )
    }

    private fun loadArtworkAsync(uri: Uri, displayTitle: String) {
        backgroundExecutor.execute {
            val bitmap = MediaArtworkLoader.loadVideoFrame(this, uri)
            val artworkBytes = bitmap?.let { MediaArtworkLoader.compressArtwork(it) }
            bitmap?.recycle()
            ContextCompat.getMainExecutor(this).execute {
                val exoPlayer = player ?: return@execute
                if (PlaybackSessionStore.currentUri != uri) return@execute
                enrichMediaMetadata(exoPlayer, displayTitle, artworkBytes)
            }
        }
    }

    private fun shouldEnrichMetadata(exoPlayer: ExoPlayer): Boolean {
        val current = exoPlayer.currentMediaItem ?: return false
        val existing = current.mediaMetadata
        if (existing.artworkData == null) {
            return true
        }
        val duration = exoPlayer.duration
        if (duration > 0 && duration != C.TIME_UNSET) {
            val existingDuration = existing.durationMs
            if (existingDuration == null ||
                existingDuration == C.TIME_UNSET ||
                existingDuration <= 0L
            ) {
                return true
            }
        }
        return false
    }

    private fun enrichMediaMetadata(
        exoPlayer: ExoPlayer,
        titleOverride: String? = null,
        artworkBytesOverride: ByteArray? = null,
    ) {
        if (exoPlayer.mediaItemCount == 0) return
        val current = exoPlayer.currentMediaItem ?: return
        val existing = current.mediaMetadata
        val title = titleOverride
            ?: existing.title?.toString()
            ?: PlaybackSessionStore.currentTitle
            ?: getString(com.lockscreen.player.R.string.playback_notification_title)

        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(getString(com.lockscreen.player.R.string.app_name))

        val duration = exoPlayer.duration
        if (duration > 0 && duration != C.TIME_UNSET) {
            builder.setDurationMs(duration)
        }

        val artwork = artworkBytesOverride
        if (artwork != null) {
            builder.setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } else if (existing.artworkData != null) {
            builder.setArtworkData(
                existing.artworkData!!,
                MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            )
        }

        val updated = current.buildUpon()
            .setMediaMetadata(builder.build())
            .build()
        exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, updated)
    }

    private fun stopForegroundAndSelf() {
        isPlaybackReady = false
        PlaybackSessionStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        notifySessionUpdated()
    }

    override fun onDestroy() {
        isRunning = false
        isPlaybackReady = false
        val exoPlayer = player
        val session = mediaSession

        player = null
        mediaSession = null

        exoPlayer?.removeListener(playerListener)
        session?.release()
        exoPlayer?.release()

        if (PlaybackSessionStore.hasActiveSession()) {
            PlaybackSessionStore.markServiceInactive(this)
        }
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    companion object {

        @Volatile
        var isRunning: Boolean = false
            private set

        /** [playUri] 已成功載入媒體後為 true，至停止播放或 Service 銷毀前維持。 */
        @Volatile
        var isPlaybackReady: Boolean = false
            private set

        const val ACTION_PLAYBACK_URI_FAILED =
            "com.lockscreen.player.action.PLAYBACK_URI_FAILED"

        const val ACTION_PLAYBACK_SESSION_UPDATED =
            "com.lockscreen.player.action.PLAYBACK_SESSION_UPDATED"

        const val ACTION_STOP_PLAYBACK = "com.lockscreen.player.action.STOP_PLAYBACK"

        private const val TAG = "PlaybackService"
        /** v2：提高重要性以穩定顯示於鎖屏。 */
        const val NOTIFICATION_CHANNEL_ID = "media_playback_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_URI = "com.lockscreen.player.action.PLAY_URI"
        const val ACTION_SET_REPEAT_MODE = "com.lockscreen.player.action.SET_REPEAT_MODE"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REPEAT_MODE = "extra_repeat_mode"

        fun playIntent(packageContext: android.content.Context, uri: Uri, title: String?): Intent {
            return Intent(packageContext, PlaybackService::class.java).apply {
                action = ACTION_PLAY_URI
                data = uri
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        fun setRepeatModeIntent(
            packageContext: android.content.Context,
            @Player.RepeatMode repeatMode: Int,
        ): Intent {
            return Intent(packageContext, PlaybackService::class.java).apply {
                action = ACTION_SET_REPEAT_MODE
                putExtra(EXTRA_REPEAT_MODE, repeatMode)
            }
        }

        fun stopPlaybackIntent(packageContext: android.content.Context): Intent {
            return Intent(packageContext, PlaybackService::class.java).apply {
                action = ACTION_STOP_PLAYBACK
            }
        }
    }
}
