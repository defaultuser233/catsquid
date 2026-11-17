package com.maoyou.typingpet.desktop

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/**
 * classpath の /animations/<name>.gif を読み込む。
 * newIcon() は毎回新しい Image を作るので、呼び出し側は先頭フレームから再生できる。
 */
class GifFrames internal constructor(
    private val bytes: ByteArray,
    val width: Int,
    val height: Int,
) {
    fun newIcon(): ImageIcon = ImageIcon(bytes)
}

/** リソースが無い場合は null。呼び出し側が待機アニメへフォールバックする。 */
fun loadGifFrames(name: String): GifFrames? = GifFramesCache.load(name)

private object GifFramesCache {
    private val cache = HashMap<String, GifFrames?>()

    fun load(name: String): GifFrames? = synchronized(cache) {
        if (cache.containsKey(name)) return@synchronized cache[name]
        val frames = decode(name)
        cache[name] = frames
        frames
    }

    private fun decode(name: String): GifFrames? {
        val bytes = GifFrames::class.java
            .getResourceAsStream("/animations/$name.gif")
            ?.use { it.readBytes() }
            ?: return null
        val probe = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: return null
        if (probe.width <= 0 || probe.height <= 0) return null
        return GifFrames(bytes, probe.width, probe.height)
    }
}
