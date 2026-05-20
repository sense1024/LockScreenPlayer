package com.lockscreen.player.playback

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lockscreen.player.MainActivity
import com.lockscreen.player.player.PlayerActivity

/**
 * 鎖屏／通知點擊時的固定入口，依目前播放工作階段導向播放頁或主畫面。
 */
class SessionTrampolineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = PlaybackSessionStore.currentUri
        val next = if (uri != null && UriPlaybackAccess.canRead(this, uri)) {
            PlayerActivity.intent(
                context = this,
                uri = uri,
                title = PlaybackSessionStore.currentTitle,
                isNewPlayback = false,
            ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else {
            if (uri != null) {
                PlaybackSessionStore.clear(this)
            }
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
