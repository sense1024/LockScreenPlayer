package com.lockscreen.player

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.lockscreen.player.locale.LocalePreference
import com.lockscreen.player.playback.PlaybackSessionStore
import com.lockscreen.player.playback.PlaybackService

class LockScreenPlayerApp : Application() {

    override fun onCreate() {
        LocalePreference.applyStoredLocale(this)
        super.onCreate()
        PlaybackSessionStore.restore(this)
        createNotificationChannel()
    }

    fun recreateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .deleteNotificationChannel(PlaybackService.NOTIFICATION_CHANNEL_ID)
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            PlaybackService.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
