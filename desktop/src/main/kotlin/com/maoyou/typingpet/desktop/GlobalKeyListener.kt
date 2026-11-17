package com.maoyou.typingpet.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/**
 * JNativeHook によるグローバルキー入力。Linux は X11 のみ対応で、Wayland では登録に失敗する。
 * 失敗しても例外は投げず、start() が false を返して理由を failureReason に入れる。
 */
class GlobalKeyListener(private val onAction: (KeyAction) -> Unit) : AutoCloseable {
    var failureReason: String? = null
        private set

    private var hook: NativeKeyListener? = null

    fun start(): Boolean {
        if (hook != null) return true
        Logger.getLogger(GlobalScreen::class.java.name).level = Level.WARNING
        return try {
            GlobalScreen.registerNativeHook()
            val listener = object : NativeKeyListener {
                override fun nativeKeyPressed(event: NativeKeyEvent) {
                    onAction(translate(event))
                }
            }
            GlobalScreen.addNativeKeyListener(listener)
            hook = listener
            true
        } catch (t: Throwable) {
            runCatching { GlobalScreen.unregisterNativeHook() }
            failureReason = buildString {
                append(t.message ?: t.javaClass.simpleName)
                if (System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) {
                    append("（Linux 需要 X11；Wayland 会话不支持全局按键）")
                }
            }
            false
        }
    }

    override fun close() {
        val listener = hook ?: return
        hook = null
        runCatching { GlobalScreen.removeNativeKeyListener(listener) }
        runCatching { GlobalScreen.unregisterNativeHook() }
    }

    private fun translate(event: NativeKeyEvent): KeyAction {
        val code = event.keyCode
        if (code in MODIFIER_KEYS) return KeyAction.Ignore
        return KeyClassifier.classify(
            char = event.keyChar,
            backspace = code == NativeKeyEvent.VC_BACKSPACE,
            delete = code == NativeKeyEvent.VC_DELETE,
        )
    }

    private companion object {
        val MODIFIER_KEYS = setOf(
            NativeKeyEvent.VC_SHIFT,
            NativeKeyEvent.VC_CONTROL,
            NativeKeyEvent.VC_ALT,
            NativeKeyEvent.VC_META,
            NativeKeyEvent.VC_CAPS_LOCK,
            NativeKeyEvent.VC_NUM_LOCK,
            NativeKeyEvent.VC_SCROLL_LOCK,
        )
    }
}
