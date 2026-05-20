package com.lockscreen.player.playback

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** 檢查目前程序是否仍能讀取播放用 URI。 */
object UriPlaybackAccess {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun canRead(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun canReadAsync(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        val appContext = context.applicationContext
        val mainExecutor = ContextCompat.getMainExecutor(context)
        ioExecutor.execute {
            val readable = canRead(appContext, uri)
            mainExecutor.execute { onResult(readable) }
        }
    }
}
