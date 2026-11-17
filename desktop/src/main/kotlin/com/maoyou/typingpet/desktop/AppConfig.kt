package com.maoyou.typingpet.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/** デスクトップ版の設定。保存先は OS ごとのユーザー設定ディレクトリ。 */
class AppConfig(val path: Path = defaultPath()) {
    var sizeKey: String = "large"
    var alwaysOnTop: Boolean = true
    var opacity: Float = 1f
    var posX: Int = -1
    var posY: Int = -1
    var locked: Boolean = false

    init {
        load()
    }

    fun sizeHeight(): Int = when (sizeKey) {
        "small" -> 96
        "medium" -> 140
        else -> 200
    }

    fun load() {
        if (!Files.isRegularFile(path)) return
        runCatching {
            val props = Properties()
            Files.newInputStream(path).use { props.load(it) }
            sizeKey = props.getProperty("size", sizeKey).let {
                if (it == "small" || it == "medium" || it == "large") it else "large"
            }
            alwaysOnTop = props.getProperty("alwaysOnTop", alwaysOnTop.toString()).toBoolean()
            opacity = props.getProperty("opacity", opacity.toString()).toFloat().coerceIn(0.3f, 1f)
            posX = props.getProperty("posX", posX.toString()).toIntOrNull() ?: posX
            posY = props.getProperty("posY", posY.toString()).toIntOrNull() ?: posY
            locked = props.getProperty("locked", locked.toString()).toBoolean()
        }
    }

    fun save() {
        runCatching {
            path.parent?.let { Files.createDirectories(it) }
            val props = Properties()
            props.setProperty("size", sizeKey)
            props.setProperty("alwaysOnTop", alwaysOnTop.toString())
            props.setProperty("opacity", opacity.toString())
            props.setProperty("posX", posX.toString())
            props.setProperty("posY", posY.toString())
            props.setProperty("locked", locked.toString())
            Files.newOutputStream(path).use { props.store(it, "CatSquid desktop") }
        }
    }

    companion object {
        fun defaultPath(): Path {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val base = if (os.contains("win")) {
                System.getenv("APPDATA")?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"))
            } else {
                Paths.get(System.getProperty("user.home"), ".config")
            }
            return base.resolve("catsquid").resolve("desktop.properties")
        }
    }
}
