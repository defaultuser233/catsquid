package com.maoyou.typingpet

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class PetAccessibilityService : AccessibilityService() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var loggedEvents = 0
    private var loggedNoOverlay = false
    private var reactionLogs = 0
    private var imeLogs = 0

    /** 有効な入力メソッド(キーボード)のパッケージ。拼音入力中はキーボード側の変化で反応する */
    private var imePackages: Set<String> = emptySet()
    private var imePackagesAt = 0L
    private var lastTextEventAt = 0L

    /** IME が出したキー入力パルス。直後のテキストイベントが同じキーを二重に数えないよう消費する */
    private val recentImePulses = ArrayDeque<Long>()
    private val seenImeEvents = HashSet<String>()

    /** 直近にイベントを送ってくる入力法。直近にイベントがある間はウィンドウ走査を止める */
    private var lastEventImePkg = ""
    private var lastEventImeAt = 0L
    private var imeWatcher: ImeWindowWatcher? = null
    private var lastDisabledNotifyAt = 0L

    companion object {
        private const val IME_CREDIT_WINDOW_MS = 400L
        private const val MAX_ADVANCE = 8

        @Volatile var connected = false
            private set

        /** 一度つながったあとで切られた状態(設定画面で戻す必要がある) */
        @Volatile var systemClosed = false
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
        systemClosed = false
        inst = this
        DiagLog.log(this, "onServiceConnected pid=${android.os.Process.myPid()}")
        DiagLog.log(this, "ime packages=${enabledImePackages().joinToString()}")
        imeWatcher = ImeWindowWatcher(
            this,
            isActive = {
                // イベントを送ってくる入力法では走査しない(電池と ROM 対策)。2秒沈黙したら再開
                OverlayService.instance != null &&
                    SystemClock.uptimeMillis() - lastEventImeAt > 2000L
            },
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

        // パスワード欄は中身を見ない。内蔵の !/?/空白 を出すため、トリガーがなくても直近の入力だけは読む
        val maxLen = OverlayService.maxKeyLength
        val watchDelete = OverlayService.watchDelete
        val watchSpecial = OverlayService.watchSpecial
        if (reactionLogs < 5) {
            reactionLogs++
            DiagLog.log(
                this,
                "handle text event: maxLen=$maxLen watchDelete=$watchDelete watchSpecial=$watchSpecial"
            )
        }
        if (event.isPassword || (maxLen <= 0 && !watchDelete && !watchSpecial)) {
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

        val advance = textAdvance(inserted)

        if (maxLen <= 0) {
            OverlayService.reactInserted(inserted, advance)
            return
        }

        // 入力位置の直前数文字(登録文字の最大長ぶん)だけを切り出して各キャラに渡す(保存・送信はしない)
        val winStart = maxOf(0, change.start - (maxLen - 1))
        OverlayService.reactText(after.substring(winStart, change.end), change.start - winStart, inserted, advance)
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

        if (imeLogs < 5) {
            imeLogs++
            DiagLog.log(this, "ime key activity pkg=$pkg")
        }
        onImePulse()
    }

    private fun looksLikeKeyboard(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("sogou") || p.contains("baidu") || p.contains("inputmethod") ||
            p.contains("keyboard") || p.contains("ime")
    }

    /**
     * キーボードウィンドウの走査で見つけたキー入力。
     * イベントを送ってくる入力法(vivo など)では二重になるので、
     * 直近2秒にイベントが来ているパッケージでは走査側を無視する。
     */
    private fun handleImePollActivity(pkg: String) {
        if (pkg !in enabledImePackages()) return
        if (OverlayService.instance == null) return
        val now = SystemClock.uptimeMillis()
        if (pkg == lastEventImePkg && now - lastEventImeAt < 2000L) return
        if (imeLogs < 5) {
            imeLogs++
            DiagLog.log(this, "ime poll key pkg=$pkg")
        }
        onImePulse()
    }

    /**
     * IME のキー入力を暫定の 1 回として数える。
     * 直後にテキストイベントが来る環境では、そのテキストがこのパルスを消費するので二重にならない。
     * テキストが先に来た場合は、そのキーは既に数えられているのでパルスを捨てる。
     */
    private fun onImePulse() {
        if (OverlayService.instance == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTextEventAt < 150L) return
        recentImePulses.addLast(now)
        while (recentImePulses.size > 16) recentImePulses.removeFirst()
        OverlayService.reactKeys(1)
    }

    /** テキストイベントが直近の IME パルスを何回分カバーするか。残りだけステップを進める */
    private fun textAdvance(inserted: String): Int {
        val now = SystemClock.uptimeMillis()
        while (recentImePulses.isNotEmpty() && now - recentImePulses.first() > IME_CREDIT_WINDOW_MS) {
            recentImePulses.removeFirst()
        }
        val chars = inserted.codePointCount(0, inserted.length).coerceIn(1, MAX_ADVANCE)
        var covered = 0
        while (covered < chars && recentImePulses.isNotEmpty()) {
            recentImePulses.removeLast()
            covered++
        }
        return (chars - covered).coerceIn(0, MAX_ADVANCE)
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
        val secure = secureStillEnabled()
        DiagLog.log(
            this,
            "onUnbind pid=${android.os.Process.myPid()} secureEnabled=$secure intent=$intent"
        )
        connected = false
        systemClosed = true
        notifyDisabled()
        imeWatcher?.stop()
        imeWatcher = null
        if (inst === this) inst = null
        return super.onUnbind(intent)
    }

    /** オーバーレイ表示中に切られたら、戻し忘れないよう通知する(10分に1回まで) */
    private fun notifyDisabled() {
        if (OverlayService.instance == null) return
        val now = SystemClock.uptimeMillis()
        if (lastDisabledNotifyAt != 0L && now - lastDisabledNotifyAt < 10 * 60_000L) return
        lastDisabledNotifyAt = now
        try {
            val c = LocaleHelper.wrap(this)
            val channelId = "accessibility"
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        c.getString(R.string.notif_channel_accessibility),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val open = PendingIntent.getActivity(
                this, 1,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }
            nm.notify(
                2,
                builder
                    .setSmallIcon(R.drawable.ic_notif_paw)
                    .setContentTitle(c.getString(R.string.notif_accessibility_off_title))
                    .setContentText(c.getString(R.string.notif_accessibility_off_text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        DiagLog.log(this, "onDestroy pid=${android.os.Process.myPid()}")
        connected = false
        imeWatcher?.stop()
        imeWatcher = null
        if (inst === this) inst = null
        super.onDestroy()
    }

    /** 解绑されたとき、設定上はまだ有効か(ROM が勝手に切ったのか、プロセスが殺されたのか) */
    private fun secureStillEnabled(): Boolean {
        val list = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return list.split(':').any {
            it.startsWith("$packageName/", ignoreCase = true) &&
                it.contains("PetAccessibilityService", ignoreCase = true)
        }
    }
}
