package com.lockscreen.player.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.lockscreen.player.R
import com.lockscreen.player.databinding.ActivityPlayerBinding
import com.lockscreen.player.playback.PlaybackConnection
import com.lockscreen.player.playback.PlaybackService
import com.lockscreen.player.playback.PlaybackSessionStore
import com.lockscreen.player.playback.RepeatModePreference
import com.lockscreen.player.playback.UriPlaybackAccess
import com.lockscreen.player.playback.VideoRepeatMode

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity(), PlaybackConnection.Listener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playbackConnection: PlaybackConnection
    private var screenWakeHandler: ScreenWakeHandler? = null
    private var player: Player? = null
    private var syncingRepeatUi = false
    private var playbackUri: Uri? = null
    private var playbackTitle: String? = null
    private var startedServiceForPlayback = false
    private var connectionRetryCount = 0
    private var uriValidationToken = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionRetryRunnable = Runnable {
        if (isDestroyed || isFinishing) return@Runnable
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@Runnable
        playbackConnection.connect()
    }

    private val playbackUriFailedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Toast.makeText(
                this@PlayerActivity,
                R.string.error_playback_unavailable,
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            syncRepeatModeUi(VideoRepeatMode.fromPlayerValue(repeatMode))
        }

        override fun onPlayerError(error: PlaybackException) {
            handleUnrecoverablePlaybackError()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && (player?.mediaItemCount ?: 0) > 0) {
                connectionRetryCount = 0
                cancelConnectionRetries()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenDisplay()
        enableEdgeToEdge()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        setupRepeatModeToggle()

        playbackConnection = PlaybackConnection(this)
        playbackConnection.addListener(this)

        screenWakeHandler = ScreenWakeHandler(
            context = this,
            onScreenOn = { attachPlayerToView() },
            onScreenOff = { detachPlayerFromView() },
        )

        validateUriAndApplyIntent(intent, isInitialCreate = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        validateUriAndApplyIntent(intent, isInitialCreate = false)
    }

    private fun validateUriAndApplyIntent(intent: Intent, isInitialCreate: Boolean) {
        val uri = intent.data ?: intent.getParcelableExtra(EXTRA_URI, Uri::class.java)
        if (uri == null) {
            if (isInitialCreate) finish()
            return
        }

        val token = ++uriValidationToken
        binding.uriCheckOverlay.isVisible = true
        UriPlaybackAccess.canReadAsync(this, uri) { readable ->
            if (isDestroyed || token != uriValidationToken) return@canReadAsync
            binding.uriCheckOverlay.isVisible = false
            if (!readable) {
                Toast.makeText(this, R.string.error_playback_unavailable, Toast.LENGTH_LONG).show()
                if (isInitialCreate) finish()
                return@canReadAsync
            }
            applyPlaybackIntent(intent, isInitialCreate)
        }
    }

    private fun applyPlaybackIntent(intent: Intent, isInitialCreate: Boolean) {
        val uri = intent.data ?: intent.getParcelableExtra(EXTRA_URI, Uri::class.java) ?: return

        val title = intent.getStringExtra(EXTRA_TITLE)
        val isNewPlayback = intent.getBooleanExtra(EXTRA_NEW_PLAYBACK, true)
        val isSameUri = uri == playbackUri

        if (!isInitialCreate && !isSameUri) {
            connectionRetryCount = 0
            cancelConnectionRetries()
        }

        playbackUri = uri
        playbackTitle = title

        val shouldStartService = when {
            isNewPlayback -> true
            !PlaybackService.isRunning -> true
            !PlaybackService.isPlaybackReady -> true
            !isInitialCreate && !isSameUri -> true
            else -> false
        }

        if (shouldStartService) {
            startForegroundPlayback(uri, title)
            startedServiceForPlayback = true
        }
    }

    private fun setupRepeatModeToggle() {
        syncRepeatModeUi(
            player?.let { VideoRepeatMode.fromPlayerValue(it.repeatMode) }
                ?: RepeatModePreference.get(this),
        )
        binding.repeatModeToggle.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (!isChecked || syncingRepeatUi) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.repeatInfiniteButton -> VideoRepeatMode.REPEAT_ONE
                else -> VideoRepeatMode.PLAY_ONCE
            }
            applyRepeatMode(mode)
        }
    }

    private fun applyRepeatMode(mode: VideoRepeatMode) {
        if (player == null || !playbackConnection.isConnected) {
            syncRepeatModeUi(mode)
        }
        val serviceIntent = PlaybackService.setRepeatModeIntent(this, mode.playerValue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun syncRepeatModeUi(mode: VideoRepeatMode) {
        syncingRepeatUi = true
        val buttonId = when (mode) {
            VideoRepeatMode.REPEAT_ONE -> R.id.repeatInfiniteButton
            VideoRepeatMode.PLAY_ONCE -> R.id.repeatPlayOnceButton
        }
        if (binding.repeatModeToggle.checkedButtonId != buttonId) {
            binding.repeatModeToggle.check(buttonId)
        }
        syncingRepeatUi = false
    }

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            )
        }
    }

    private fun startForegroundPlayback(uri: Uri, title: String?) {
        val intent = PlaybackService.playIntent(this, uri, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPlaybackService() {
        val intent = PlaybackService.stopPlaybackIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun handleUnrecoverablePlaybackError() {
        PlaybackSessionStore.clear(this)
        stopPlaybackService()
        showPlaybackError(R.string.error_playback)
    }

    override fun onStart() {
        super.onStart()
        registerPlaybackReceivers()
        playbackConnection.connect()
        screenWakeHandler?.register()
    }

    override fun onResume() {
        super.onResume()
        attachPlayerToView()
    }

    override fun onStop() {
        cancelConnectionRetries()
        unregisterPlaybackReceivers()
        screenWakeHandler?.unregister()
        detachPlayerFromView()
        playbackConnection.disconnect()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            attachPlayerToView()
        }
    }

    override fun onConnected(player: Player) {
        connectionRetryCount = 0
        cancelConnectionRetries()
        this.player = player
        player.addListener(playerListener)
        syncRepeatModeUi(VideoRepeatMode.fromPlayerValue(player.repeatMode))
        ensurePlaybackServiceStarted(player)
        if (player.mediaItemCount == 0) {
            scheduleConnectionRetry()
        } else {
            attachPlayerToView()
        }
    }

    override fun onDisconnected() {
        player?.removeListener(playerListener)
        detachPlayerFromView()
        player = null
    }

    override fun onConnectionFailed() {
        val uri = playbackUri
        if (uri != null && !startedServiceForPlayback) {
            startForegroundPlayback(uri, playbackTitle)
            startedServiceForPlayback = true
        }
        if (scheduleConnectionRetry()) {
            return
        }
        showPlaybackError(R.string.error_service)
    }

    private fun ensurePlaybackServiceStarted(player: Player) {
        if (player.mediaItemCount > 0) return
        val uri = playbackUri ?: return
        if (startedServiceForPlayback) return
        startForegroundPlayback(uri, playbackTitle)
        startedServiceForPlayback = true
    }

    private fun scheduleConnectionRetry(): Boolean {
        if (connectionRetryCount >= MAX_CONNECTION_RETRIES) {
            return false
        }
        connectionRetryCount++
        val delayMs = CONNECTION_RETRY_BASE_MS * connectionRetryCount
        cancelConnectionRetries()
        mainHandler.postDelayed(connectionRetryRunnable, delayMs)
        return true
    }

    private fun cancelConnectionRetries() {
        mainHandler.removeCallbacks(connectionRetryRunnable)
    }

    private fun registerPlaybackReceivers() {
        val failedFilter = IntentFilter(PlaybackService.ACTION_PLAYBACK_URI_FAILED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackUriFailedReceiver, failedFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(playbackUriFailedReceiver, failedFilter)
        }
    }

    private fun unregisterPlaybackReceivers() {
        try {
            unregisterReceiver(playbackUriFailedReceiver)
        } catch (_: IllegalArgumentException) {
            // 尚未註冊
        }
    }

    private fun showPlaybackError(@StringRes messageResId: Int) {
        Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_LONG).show()
    }

    private fun attachPlayerToView() {
        val p = player ?: return
        binding.playerView.player = p
        updateKeepScreenOn(true)
    }

    private fun detachPlayerFromView() {
        binding.playerView.player = null
        updateKeepScreenOn(false)
    }

    private fun updateKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        uriValidationToken++
        cancelConnectionRetries()
        playbackConnection.removeListener(this)
        super.onDestroy()
    }

    companion object {
        private const val MAX_CONNECTION_RETRIES = 4
        private const val CONNECTION_RETRY_BASE_MS = 300L

        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_NEW_PLAYBACK = "extra_new_playback"

        fun intent(
            context: Context,
            uri: Uri,
            title: String?,
            isNewPlayback: Boolean,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                data = uri
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NEW_PLAYBACK, isNewPlayback)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
