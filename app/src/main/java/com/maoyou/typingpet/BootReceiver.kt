package com.maoyou.typingpet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 再起動・アプリ更新後に、前回表示していたペットを戻す */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!Prefs.getExpectedRunning(context)) return
        if (!Prefs.getAlwaysOnTop(context)) return
        if (!Settings.canDrawOverlays(context)) return
        if (OverlayService.isRunning) return
        if (Prefs.getShownIds(context).isEmpty()) return
        try {
            OverlayService.start(context)
        } catch (e: Exception) {
            DiagLog.log(context, "boot start failed: ${e.javaClass.name}")
        }
    }
}
