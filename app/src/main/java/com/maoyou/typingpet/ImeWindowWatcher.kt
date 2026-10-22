package com.maoyou.typingpet

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * キーボードウィンドウの中身を定期的に読み、候補欄や入力中の文字が変わったら
 * 「キーが押された」とみなす。イベントを一切送らない入力法(搜狗・百度など)向け。
 *
 * 読むのはキーボード自身のウィンドウだけで、入力欄の本文や保存は一切しない。
 * 読めない(secure ウィンドウ)場合は何もしない。
 */
class ImeWindowWatcher(
    private val service: AccessibilityService,
    private val isActive: () -> Boolean,
    private val onKeyActivity: (String) -> Unit
) {

    private companion object {
        const val INTERVAL_MS = 150L
        const val MAX_NODES = 80
        const val MAX_DEPTH = 8
        const val CHANGE_MIN_GAP_MS = 120L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastSignature = ""
    private var lastPkg = ""
    private var lastChangeAt = 0L
    private val loggedWindows = HashSet<String>()
    private var loggedNoRoot = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                poll()
            } catch (_: Throwable) {
            }
            if (running) handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.postDelayed(tick, INTERVAL_MS)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        lastSignature = ""
        lastPkg = ""
    }

    /** 診断ログを消したあと、初出記録をもう一度出せるようにする */
    fun resetLogging() {
        loggedWindows.clear()
        loggedNoRoot = false
    }

    private fun poll() {
        if (!isActive()) {
            // 止まっている間の変化は数えない。再開時は今の状態を基準にし直す
            lastSignature = ""
            lastPkg = ""
            return
        }
        val windows = service.windows ?: return
        val win = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.isActive
        } ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return

        val root = win.root
        if (root == null) {
            if (!loggedNoRoot) {
                loggedNoRoot = true
                DiagLog.log(service, "ime window root=null")
            }
            return
        }
        val pkg = root.packageName?.toString() ?: return

        val sig = StringBuilder()
        val nodes = collect(root, sig)
        if (loggedWindows.add(pkg)) {
            DiagLog.log(service, "ime window pkg=$pkg nodes=$nodes textLen=${sig.length}")
        }
        if (sig.isEmpty()) return

        val text = sig.toString()
        if (pkg != lastPkg) {
            lastPkg = pkg
            lastSignature = text
            return
        }
        if (text == lastSignature) return
        lastSignature = text

        val now = SystemClock.uptimeMillis()
        if (now - lastChangeAt < CHANGE_MIN_GAP_MS) return
        lastChangeAt = now
        onKeyActivity(pkg)
    }

    /** ウィンドウ内の text / contentDescription を集める(中身は保存も記録もしない) */
    private fun collect(root: AccessibilityNodeInfo, sb: StringBuilder): Int {
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            node.text?.let { sb.append(it).append('\u0001') }
            node.contentDescription?.let { sb.append(it).append('\u0001') }
            if (depth >= MAX_DEPTH) continue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it to depth + 1) }
            }
        }
        return visited
    }
}
