package com.lockscreen.player

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.lockscreen.player.databinding.ActivityMainBinding
import com.lockscreen.player.playback.PlaybackService
import com.lockscreen.player.playback.PlaybackSessionChecker
import com.lockscreen.player.playback.PlaybackSessionStore
import com.lockscreen.player.playback.UriPlaybackAccess
import com.lockscreen.player.player.PlayerActivity
import com.lockscreen.player.ui.SettingsBottomSheetFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var resumeButtonValidationToken = 0

    private val sessionUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshResumeButton()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // 部分 provider 不支援持久化權限；單次 grant 仍可由 Intent flag 傳遞。
        }
        openPlayer(uri, isNewPlayback = true)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                R.string.permission_notifications_rationale,
                Toast.LENGTH_LONG,
            ).show()
        }
        launchPicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        setSupportActionBar(binding.toolbar)

        binding.settingsButton.setOnClickListener {
            SettingsBottomSheetFragment.show(this)
        }

        binding.pickVideoButton.setOnClickListener {
            requestNotificationPermissionThenPick()
        }

        binding.resumePlaybackButton.setOnClickListener {
            resumePlayback()
        }
    }

    override fun onStart() {
        super.onStart()
        registerSessionUpdatedReceiver()
    }

    override fun onStop() {
        unregisterSessionUpdatedReceiver()
        super.onStop()
    }

    override fun onPause() {
        resumeButtonValidationToken++
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        refreshResumeButton()
    }

    private fun registerSessionUpdatedReceiver() {
        val filter = IntentFilter(PlaybackService.ACTION_PLAYBACK_SESSION_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(sessionUpdatedReceiver, filter)
        }
    }

    private fun unregisterSessionUpdatedReceiver() {
        try {
            unregisterReceiver(sessionUpdatedReceiver)
        } catch (_: IllegalArgumentException) {
            // 尚未註冊
        }
    }

    private fun refreshResumeButton() {
        val token = ++resumeButtonValidationToken
        if (PlaybackSessionStore.currentUri == null) {
            updateResumeButton(visible = false)
            return
        }
        PlaybackSessionChecker.validateSessionAsync(this) { valid ->
            if (token != resumeButtonValidationToken || isDestroyed) return@validateSessionAsync
            updateResumeButton(visible = valid)
        }
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateResumeButton(visible: Boolean) {
        binding.resumePlaybackButton.isVisible = visible
        if (!visible) return
        val title = PlaybackSessionStore.currentTitle
            ?: getString(R.string.playback_notification_title)
        binding.resumePlaybackButton.text = getString(R.string.resume_playback, title)
    }

    private fun resumePlayback() {
        val uri = PlaybackSessionStore.currentUri ?: return
        UriPlaybackAccess.canReadAsync(this, uri) { readable ->
            if (isDestroyed) return@canReadAsync
            if (!readable) {
                PlaybackSessionStore.clear(this)
                refreshResumeButton()
                Toast.makeText(this, R.string.error_playback_unavailable, Toast.LENGTH_LONG).show()
                return@canReadAsync
            }
            openPlayer(uri, isNewPlayback = false)
        }
    }

    private fun requestNotificationPermissionThenPick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            launchPicker()
            return
        }
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED -> launchPicker()

            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                Toast.makeText(
                    this,
                    R.string.permission_notifications_rationale,
                    Toast.LENGTH_LONG,
                ).show()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun launchPicker() {
        pickVideoLauncher.launch(arrayOf("video/*"))
    }

    private fun openPlayer(uri: Uri, isNewPlayback: Boolean) {
        val title = if (isNewPlayback) {
            queryDisplayName(uri)
        } else {
            PlaybackSessionStore.currentTitle
        }
        startActivity(PlayerActivity.intent(this, uri, title, isNewPlayback))
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        return null
    }
}
