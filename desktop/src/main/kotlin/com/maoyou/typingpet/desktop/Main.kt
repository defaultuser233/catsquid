package com.maoyou.typingpet.desktop

import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main() {
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println("CatSquid desktop needs a graphical desktop session (X11 or Windows).")
        exitProcess(1)
    }
    SwingUtilities.invokeLater {
        val config = AppConfig()
        val window = PetWindow(config)
        window.show()

        val hook = GlobalKeyListener { action -> window.postAction(action) }
        if (!hook.start()) {
            window.showHookUnavailable(hook.failureReason)
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                hook.close()
                runCatching { window.close() }
            },
        )
    }
}
