package com.maoyou.typingpet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** 画像を必要なサイズまで縮小して読み込む(枚数が増えてもメモリを食いすぎないように) */
object ImageLoader {
    fun load(context: Context, uriStr: String, reqPx: Int): Bitmap? {
        val target = reqPx.coerceAtLeast(1)
        return try {
            val uri = Uri.parse(uriStr)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            if (longest > 0) {
                while (longest / (sample * 2) >= target) sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val current = maxOf(decoded.width, decoded.height)
            if (current <= target) {
                decoded
            } else {
                val scale = target.toFloat() / current
                val w = (decoded.width * scale).toInt().coerceAtLeast(1)
                val h = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            }
        } catch (e: Exception) {
            null
        }
    }
}
