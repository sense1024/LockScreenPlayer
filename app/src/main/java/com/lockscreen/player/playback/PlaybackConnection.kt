package com.lockscreen.player.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 透過 [MediaController] 連接 [PlaybackService] 的 [MediaSession]。
 */
@UnstableApi
class PlaybackConnection(private val context: Context) {

    interface Listener {
        fun onConnected(player: Player)
        fun onDisconnected()
        fun onConnectionFailed() {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    fun addListener(listener: Listener) {
        listeners.add(listener)
        controller?.let { listener.onConnected(it) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    controllerFuture = null
                    listeners.forEach { it.onConnected(mediaController) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController", e)
                    controllerFuture = null
                    listeners.forEach {
                        it.onConnectionFailed()
                        it.onDisconnected()
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun disconnect() {
        controllerFuture?.cancel(true)
        controllerFuture = null
        controller?.release()
        controller = null
        listeners.forEach { it.onDisconnected() }
    }

    val isConnected: Boolean get() = controller != null

    companion object {
        private const val TAG = "PlaybackConnection"
    }
}
