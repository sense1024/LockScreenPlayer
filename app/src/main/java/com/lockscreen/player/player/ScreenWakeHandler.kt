package com.lockscreen.player.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * 監聽螢幕開關：優先使用 [DisplayManager]（較可靠），並以 [Intent.ACTION_SCREEN_OFF] 作為補強。
 */
class ScreenWakeHandler(
    context: Context,
    private val onScreenOn: () -> Unit,
    private val onScreenOff: () -> Unit,
) {

    private val appContext = context.applicationContext
    private var registered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                onScreenOff()
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            if (isDefaultDisplayOn()) {
                onScreenOn()
            }
        }
    }

    fun register() {
        if (registered) return

        val offFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(screenOffReceiver, offFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(screenOffReceiver, offFilter)
        }

        val displayManager = appContext.getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, null)

        registered = true
    }

    fun unregister() {
        if (!registered) return
        appContext.unregisterReceiver(screenOffReceiver)
        appContext.getSystemService(DisplayManager::class.java)
            .unregisterDisplayListener(displayListener)
        registered = false
    }

    private fun isDefaultDisplayOn(): Boolean {
        val displayManager = appContext.getSystemService(DisplayManager::class.java)
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return display.state == Display.STATE_ON
    }
}
