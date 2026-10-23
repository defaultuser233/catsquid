package com.maoyou.typingpet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** 画像を必要なサイズまで縮小して読み込む(枚数が増えてもメモリを食いすぎないように) */
object ImageLoader {
    const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
    const val MAX_IMAGE_PIXELS = 100_000_000L

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
            val result = if (current <= target) {
                decoded
            } else {
                val scale = target.toFloat() / current
                val w = (decoded.width * scale).toInt().coerceAtLeast(1)
                val h = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            }

            val orientation = try {
                orientationOf(context, uri)
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            try {
                orient(result, orientation)
            } catch (_: Exception) {
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun orientationOf(context: Context, uri: Uri): Int =
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
