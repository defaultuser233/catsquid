package com.maoyou.typingpet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PetAccessibilityService : AccessibilityService() {

    private var loggedEvents = 0
    private var loggedNoOverlay = false
    private var reactionLogs = 0

    companion object {
        @Volatile var connected = false
            private set
        @Volatile var eventCount = 0
            private set
        @Volatile var lastPackage = "-"
            private set
        @Volatile var lastEventType = 0
            private set

        fun resetStats() {
            eventCount = 0
            lastPackage = "-"
            lastEventType = 0
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        DiagLog.log(this, "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            DiagLog.log(this, "event error: ${Log.getStackTraceString(t)}")
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null) return
        eventCount++
        lastEventType = event.eventType
        lastPackage = event.packageName?.toString() ?: "-"
        loggedEvents++
        if (loggedEvents <= 3 || loggedEvents % 50 == 0) {
            DiagLog.log(this, "event #$loggedEvents type=${event.eventType} pkg=$lastPackage")
        }

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (OverlayService.instance == null) {
            if (!loggedNoOverlay) {
                loggedNoOverlay = true
                DiagLog.log(this, "overlay not running; text events ignored")
            }
            return
        }

        // 表示中のキャラに文字の反応も「消したとき」もない / パスワード欄 → 中身は一切見ない
        val maxLen = OverlayService.maxKeyLength
        val watchDelete = OverlayService.watchDelete
        if (reactionLogs < 5) {
            reactionLogs++
            DiagLog.log(this, "handle text event: maxLen=$maxLen watchDelete=$watchDelete")
        }
        if (event.isPassword || (maxLen <= 0 && !watchDelete)) {
            OverlayService.reactIfRunning()
            return
        }

        val after = event.text?.firstOrNull()?.toString()
        val change = after?.let { findChange(event, it) }
        if (after == null) {
            OverlayService.reactIfRunning()
            return
        }
        if (change == null) {
            // beforeText があり差分なし = 実際には変化していないイベント。
            // ここで反応すると待機タイマーが延び続けるので無視する。
            if (event.beforeText == null) OverlayService.reactIfRunning()
            return
        }

        val inserted = after.substring(change.start, change.end)

        // 新しい文字は何も入らず、文字が消えただけ → 「消したとき」
        if (change.end == change.start) {
            if (change.removed > 0 && watchDelete) OverlayService.reactDelete() else OverlayService.reactIfRunning()
            return
        }

        if (maxLen <= 0) {
            OverlayService.reactInserted(inserted)
            return
        }

        // 入力位置の直前数文字(登録文字の最大長ぶん)だけを切り出して各キャラに渡す(保存・送信はしない)
        val winStart = maxOf(0, change.start - (maxLen - 1))
        OverlayService.reactText(after.substring(winStart, change.end), change.start - winStart, inserted)
    }

    /** 今回の変更: 入力された範囲 [start, end) と、消えた文字数 */
    private class Change(val start: Int, val end: Int, val removed: Int)

    /**
     * 今回の変更を求める。
     * 変換中や予測変換でもズレにくいよう、まずは変更前後の文字列の差分から求め、
     * 取れない場合だけ fromIndex/addedCount/removedCount を使う。
     * (「ねむい」→「眠い」のような変換は、新しい文字が入るので「消した」にはならない)
     */
    private fun findChange(event: AccessibilityEvent, after: String): Change? {
        val before = event.beforeText?.toString()
        if (before != null) {
            var prefix = 0
            val maxPrefix = minOf(before.length, after.length)
            while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++

            var suffix = 0
            val maxSuffix = minOf(before.length - prefix, after.length - prefix)
            while (suffix < maxSuffix &&
                before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
            ) suffix++

            val end = after.length - suffix
            val removed = before.length - prefix - suffix
            if (end > prefix || removed > 0) return Change(prefix, end, removed)
        }

        val from = event.fromIndex
        val count = event.addedCount
        val removed = event.removedCount
        if (from >= 0 && count > 0 && from + count <= after.length) return Change(from, from + count, removed)
        if (from >= 0 && count == 0 && removed > 0) return Change(from, from, removed)
        return null
    }

    override fun onInterrupt() {
        DiagLog.log(this, "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagLog.log(this, "onUnbind: $intent")
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DiagLog.log(this, "onDestroy")
        connected = false
        super.onDestroy()
    }
}
