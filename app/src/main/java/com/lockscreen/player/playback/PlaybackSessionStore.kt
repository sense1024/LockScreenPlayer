package com.lockscreen.player.playback

import android.content.Context
import android.net.Uri

/**
 * 記錄播放工作階段 URI／標題（持久化），供「繼續播放」與恢復 [PlayerActivity] 使用。
 * [serviceActive] 表示 [PlaybackService] 是否正在前景播放；程序重啟後會與 [PlaybackService.isRunning] 校正。
 */
object PlaybackSessionStore {

    private const val PREFS_NAME = "playback_session"
    private const val KEY_URI = "uri"
    private const val KEY_TITLE = "title"
    private const val KEY_SERVICE_ACTIVE = "service_active"

    var currentUri: Uri? = null
        private set
    var currentTitle: String? = null
        private set

    @Volatile
    private var serviceActive: Boolean = false

    fun restore(context: Context) {
        val prefs = prefs(context)
        val uriString = prefs.getString(KEY_URI, null)
        currentUri = uriString?.let { Uri.parse(it) }
        currentTitle = prefs.getString(KEY_TITLE, null)
        serviceActive = prefs.getBoolean(KEY_SERVICE_ACTIVE, false)
        if (serviceActive && !PlaybackService.isRunning) {
            serviceActive = false
            prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, false).apply()
        }
    }

    fun setSession(context: Context, uri: Uri, title: String?) {
        currentUri = uri
        currentTitle = title
        serviceActive = true
        persist(context)
    }

    /** Service 結束時保留 URI／標題，僅標記為非前景播放（供重開 App 繼續播放）。 */
    fun markServiceInactive(context: Context) {
        serviceActive = false
        prefs(context).edit()
            .putBoolean(KEY_SERVICE_ACTIVE, false)
            .apply()
    }

    fun clear(context: Context) {
        currentUri = null
        currentTitle = null
        serviceActive = false
        prefs(context).edit().clear().apply()
    }

    fun hasActiveSession(): Boolean = serviceActive && currentUri != null

    private fun persist(context: Context) {
        prefs(context).edit()
            .putString(KEY_URI, currentUri?.toString())
            .putString(KEY_TITLE, currentTitle)
            .putBoolean(KEY_SERVICE_ACTIVE, serviceActive)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
