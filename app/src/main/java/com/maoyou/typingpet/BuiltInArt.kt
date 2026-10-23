package com.maoyou.typingpet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Movie
import android.graphics.Paint
import android.util.LruCache

/** 内蔵 GIF を全 PetView で共有する。Movie は main thread からのみ触る */
object BuiltInArt {

    private val idleNames = listOf("idle_1", "idle_2", "idle_3", "idle_4")
    private val typingNames = listOf("typing_1", "typing_2", "typing_3")
    private val specialNames = listOf("delete", "exclamation", "question", "space")
    private val tintColors = listOf("#000000", "#6CA8FF", "#FF8FC7", "#8FD9A8")

    private val movies = HashMap<String, Movie?>()
    private val previews = LruCache<String, Bitmap>(16)

    fun idle(context: Context): List<Movie> = idleNames.mapNotNull { movie(context, it) }

    fun typing(context: Context): List<Movie> = typingNames.mapNotNull { movie(context, it) }

    fun special(context: Context, name: String): Movie? = movie(context, name)

    fun hasSpecials(context: Context): Boolean = specialNames.any { movie(context, it) != null }

    fun movie(context: Context, name: String): Movie? {
        if (movies.containsKey(name)) return movies[name]
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        val movie = if (id != 0) {
            try {
                context.resources.openRawResource(id).buffered().use { Movie.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        movies[name] = movie
        return movie
    }

    /** 設定画面用に、GIF の 1 フレームを Bitmap に描く。PetView は作らない */
    fun previewBitmap(context: Context, name: String, sizePx: Int, preset: Int): Bitmap? {
        val size = sizePx.coerceAtLeast(1)
        val key = "$name|$size|$preset"
        previews.get(key)?.let { return it }
        val movie = movie(context, name) ?: return null
        val mw = movie.width()
        val mh = movie.height()
        if (mw <= 0 || mh <= 0) return null

        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = size.toFloat() / maxOf(mw, mh)
        val w = mw * scale
        val h = mh * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = tintFilter(preset)
        movie.setTime(0)
        canvas.save()
        canvas.translate((size - w) / 2f, (size - h) / 2f)
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f, paint)
        canvas.restore()
        previews.put(key, bmp)
        return bmp
    }

    /** 黒いネコをプリセットカラーに染める。白(目)は白のまま残す */
    fun tintFilter(preset: Int): ColorMatrixColorFilter {
        val base = Color.parseColor(tintColors[preset.coerceIn(0, tintColors.size - 1)])
        val r = Color.red(base) / 255f
        val g = Color.green(base) / 255f
        val b = Color.blue(base) / 255f
        val lr = 0.299f
        val lg = 0.587f
        val lb = 0.114f
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    lr * (1 - r), lg * (1 - r), lb * (1 - r), 0f, r * 255f,
                    lr * (1 - g), lg * (1 - g), lb * (1 - g), 0f, g * 255f,
                    lr * (1 - b), lg * (1 - b), lb * (1 - b), 0f, b * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    fun clearPreviews() = previews.evictAll()
}
