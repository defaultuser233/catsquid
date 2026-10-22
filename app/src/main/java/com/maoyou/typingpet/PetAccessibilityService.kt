package com.maoyou.typingpet

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class PetAccessibilityService : AccessibilityService() {

    private var loggedEvents = 0
    private var loggedNoOverlay = false
    private var reactionLogs = 0
    private var imeLogs = 0

    /** 有効な入力メソッド(キーボード)のパッケージ。拼音入力中はキーボード側の変化で反応する */
    private var imePackages: Set<String> = emptySet()
    private var imePackagesAt = 0L
    private var lastTextEventAt = 0L
    private var lastImeReactAt = 0L
    private val seenImeEvents = HashSet<String>()

    /** イベントを送ってくる入力法。直近にイベントがある間はウィンドウ走査を止める */
    private var lastEventImePkg = ""
    private var lastEventImeAt = 0L
    private var imeWatcher: ImeWindowWatcher? = null

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

        @Volatile private var inst: PetAccessibilityService? = null

        /** ログを消したときに、一度だけ記録する系のフラグも戻す */
        fun clearDiagnostics() {
            resetStats()
            inst?.let { s ->
                s.loggedEvents = 0
                s.loggedNoOverlay = false
                s.reactionLogs = 0
                s.imeLogs = 0
                s.seenImeEvents.clear()
                s.imeWatcher?.resetLogging()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        inst = this
        DiagLog.log(this, "onServiceConnected")
        DiagLog.log(this, "ime packages=${enabledImePackages().joinToString()}")
        imeWatcher = ImeWindowWatcher(
            this,
            isActive = { OverlayService.instance != null },
            onKeyActivity = { pkg -> handleImePollActivity(pkg) }
        ).also { it.start() }
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

        // キーボード自身の反応(描画変化・キー押下) = キー入力。拼音入力中は変換前の文字が
        // 入力欄に届かないことがあるため、キーを押した時点で反応させる
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleImeActivity(event)
                return
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        lastTextEventAt = SystemClock.uptimeMillis()
        loggedEvents++
        if (loggedEvents <= 3 || loggedEvents % 50 == 0) {
            DiagLog.log(this, "event #$loggedEvents type=${event.eventType} pkg=$lastPackage")
        }
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

    /**
     * キーボードウィンドウの変化をキー入力として扱う。
     * 日本語・中国語の変換中は確定するまで入力欄の文字が変わらないため、
     * キーを押した時点で内蔵の入力アニメだけを進める(登録文字の照合は確定イベントに任せる)。
     */
    private fun handleImeActivity(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val known = pkg in enabledImePackages()
        // 入力法が一覧に無い/別プロセスの場合もあるので、名前がそれらしいものも記録だけする
        if (!known && !looksLikeKeyboard(pkg)) return

        // どの種類のイベントが届くかは入力法によって違う。調査用に初出だけ記録する
        val key = "$pkg/${event.eventType}"
        if (seenImeEvents.size < 20 && seenImeEvents.add(key)) {
            DiagLog.log(this, "ime event pkg=$pkg type=${event.eventType} known=$known")
        }
        if (!known) return

        val now = SystemClock.uptimeMillis()
        lastEventImePkg = pkg
        lastEventImeAt = now

        if (OverlayService.instance == null) return

        // 文字が直接届く環境(英語入力など)では二重に反応しない
        if (now - lastTextEventAt < 150L) return
        if (now - lastImeReactAt < 120L) return
        lastImeReactAt = now

        if (imeLogs < 5) {
            imeLogs++
            DiagLog.log(this, "ime key activity pkg=$pkg")
        }
        OverlayService.reactIfRunning()
    }

    private fun looksLikeKeyboard(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("sogou") || p.contains("baidu") || p.contains("inputmethod") ||
            p.contains("keyboard") || p.contains("ime")
    }

    /**
     * キーボードウィンドウの走査で見つけたキー入力。
     * イベントを送ってくる入力法(vivo など)では二重になるので、
     * 直近5秒にイベントが来ているパッケージでは走査側を無視する。
     */
    private fun handleImePollActivity(pkg: String) {
        if (pkg !in enabledImePackages()) return
        if (OverlayService.instance == null) return
        val now = SystemClock.uptimeMillis()
        if (pkg == lastEventImePkg && now - lastEventImeAt < 5000L) return
        if (now - lastTextEventAt < 150L) return
        if (now - lastImeReactAt < 120L) return
        lastImeReactAt = now
        if (imeLogs < 5) {
            imeLogs++
            DiagLog.log(this, "ime poll key pkg=$pkg")
        }
        OverlayService.reactIfRunning()
    }

    /** 有効なキーボード(入力メソッド)のパッケージ一覧。30秒キャッシュ */
    private fun enabledImePackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (imePackages.isNotEmpty() && now - imePackagesAt < 30_000L) return imePackages
        imePackagesAt = now
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imePackages = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        return imePackages
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
        imeWatcher?.stop()
        imeWatcher = null
        if (inst === this) inst = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DiagLog.log(this, "onDestroy")
        connected = false
        imeWatcher?.stop()
        imeWatcher = null
        if (inst === this) inst = null
        super.onDestroy()
    }
}
