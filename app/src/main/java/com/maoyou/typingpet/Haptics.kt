package com.maoyou.typingpet

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 短い触覚フィードバック。強さは 0(なし)〜3(強) */
object Haptics {
    private var logs = 0

    fun bump(context: Context, level: Int) {
        if (level <= 0) return

        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        // 端末が振幅制御に対応していない場合は、長さの違いで強弱を付ける
        val amplitudeControl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            vibrator.hasAmplitudeControl()
        val duration = when (level) {
            1 -> 20L
            2 -> 45L
            else -> 75L
        }
        val amplitude = when (level) {
            1 -> 80
            2 -> 160
            else -> 255
        }

        if (logs < 3) {
            logs++
            DiagLog.log(context, "haptic level=$level ampControl=$amplitudeControl")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (amplitudeControl) {
                    VibrationEffect.createOneShot(duration, amplitude)
                } else {
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (_: Exception) {
        }
    }
}
