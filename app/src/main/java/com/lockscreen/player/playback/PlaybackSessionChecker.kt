package com.lockscreen.player.playback

import android.content.Context

/**
 * 在背景執行緒驗證 URI 是否仍可讀，避免主畫面 [onResume] 阻塞。
 */
object PlaybackSessionChecker {

    fun validateSessionAsync(context: Context, onResult: (Boolean) -> Unit) {
        val uri = PlaybackSessionStore.currentUri
        if (uri == null) {
            onResult(false)
            return
        }
        UriPlaybackAccess.canReadAsync(context, uri) { readable ->
            if (!readable) {
                if (PlaybackSessionStore.currentUri != null) {
                    PlaybackSessionStore.clear(context.applicationContext)
                }
                onResult(false)
            } else {
                onResult(true)
            }
        }
    }
}
