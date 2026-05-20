package com.lockscreen.player.playback

import android.content.Context
import androidx.media3.common.Player

/**
 * 播放重複模式：單次播放或單曲無限重複。
 */
enum class VideoRepeatMode(@param:Player.RepeatMode val playerValue: Int) {
    PLAY_ONCE(Player.REPEAT_MODE_OFF),
    REPEAT_ONE(Player.REPEAT_MODE_ONE),
    ;

    companion object {
        fun fromPlayerValue(@Player.RepeatMode value: Int): VideoRepeatMode {
            return entries.firstOrNull { it.playerValue == value } ?: PLAY_ONCE
        }
    }
}

object RepeatModePreference {

    private const val PREFS_NAME = "playback_prefs"
    private const val KEY_REPEAT_MODE = "repeat_mode"

    fun get(context: Context): VideoRepeatMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_REPEAT_MODE, VideoRepeatMode.PLAY_ONCE.playerValue)
        return VideoRepeatMode.fromPlayerValue(stored)
    }

    fun set(context: Context, mode: VideoRepeatMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REPEAT_MODE, mode.playerValue)
            .apply()
    }
}
