package com.maoyou.typingpet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** 表示中のキャラを1体ずつ別ウィンドウで画面に浮かせる(それぞれドラッグで移動できる) */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var motion: MotionController
    private var screenOn = true
    private var lastTiltX = 0f
    private var lastTiltY = 0f
    private var lastWindowMoveAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    updateMotion()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    updateMotion()
                }
            }
        }
    }

    private class Pet(
        val id: String,
        val view: PetView,
        val params: WindowManager.LayoutParams,
        var keys: List<List<String>> = emptyList(),
        /** 「⌫ 消したとき」の項目の番号(なければ -1) */
        var deleteIdx: Int = -1,
        /** 傾き移動の基準位置(ドラッグで保存される位置) */
        var baseX: Int = 0,
        var baseY: Int = 0,
        /** 可動範囲に対する比率(0..1)。画面サイズ・密度が変わっても位置を保つ */
        var fx: Double = 0.0,
        var fy: Double = 0.0,
        var dragging: Boolean = false
    )

    /** キャラid → 表示中のペット */
    private val pets = LinkedHashMap<String, Pet>()

    /** 読み込み済み画像のキャッシュ。全画像を常駐させず、バイト数で上限を設ける */
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private fun cacheSizeBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()

    @Volatile private var maxKeyLen = 0
    @Volatile private var watchDel = false
    @Volatile private var watchSpecial = false

    companion object {
        const val ACTION_START = "com.maoyou.typingpet.action.START"
        const val ACTION_STOP = "com.maoyou.typingpet.action.STOP"

        @Volatile var instance: OverlayService? = null

        val isRunning: Boolean get() = instance != null

        /** 前面サービスとして通知を出せているか(診断用) */
        @Volatile var foregroundActive = false
            private set

        /** 直近の照合結果。入力本文は含めない(診断用) */
        @Volatile var lastReaction = "-"
            private set

        /** ユーザー操作で表示を開始する。再起動後の復元対象として記録する */
        fun start(context: Context) {
            Prefs.setExpectedRunning(context, true)
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 言語変更などで通知の文言を作り直す */
        fun refreshNotification() {
            instance?.let { s -> s.handler.post { s.goForeground() } }
        }

        /** 表示中キャラに登録された文字のうち最長の長さ(0なら文字の反応なし) */
        val maxKeyLength: Int get() = instance?.maxKeyLen ?: 0

        /** 表示中キャラの誰かが「⌫ 消したとき」を登録しているか(内蔵アニメを使うキャラも含む) */
        val watchDelete: Boolean get() = instance?.watchDel ?: false

        /** 内蔵の ! / ? / 空白アニメを使えるキャラが表示中か */
        val watchSpecial: Boolean get() = instance?.watchSpecial ?: false

        /** 文字が消されただけのときに呼ぶ */
        fun reactDelete() {
            instance?.let { s ->
                s.handler.post {
                    s.pets.values.forEach { p ->
                        if (p.deleteIdx >= 0) p.view.react(p.deleteIdx) else p.view.reactDelete()
                    }
                }
            }
        }

        fun reactIfRunning() {
            instance?.reactAll(null, 0, null, 1)
        }

        /** 文字を持たない IME のキー入力を count 回ぶん進める */
        fun reactKeys(count: Int) {
            instance?.reactAll(null, 0, null, count)
        }

        /** 登録文字がない場合でも、入力された文字を渡して内蔵の専用アニメを出せるようにする */
        fun reactInserted(inserted: String, advance: Int = 1) {
            instance?.reactAll(null, 0, inserted, advance)
        }

        /** 入力直後の数文字を渡して、各キャラが自分の登録文字で反応する */
        fun reactText(window: String, newFrom: Int, inserted: String? = null, advance: Int = 1) {
            instance?.reactAll(window, newFrom, inserted, advance)
        }

        /** 設定画面での変更を、動作中のオーバーレイに即反映する */
        fun refreshIfRunning() {
            instance?.let { s -> s.handler.post { s.sync() } }
        }

        /** 今の持ち方を傾きのニュートラル位置にする */
        fun recenterTilt() {
            instance?.let { s -> s.handler.post { s.motion.recenter() } }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        DiagLog.log(this, "OverlayService onCreate")
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenOn = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        motion = MotionController(
            this,
            onTilt = { nx, ny ->
                lastTiltX = nx
                lastTiltY = ny
                pets.values.forEach { it.view.setTilt(nx, ny) }
                updateWindowPositions()
            },
            onSway = { ax, ay ->
                pets.values.forEach { it.view.setSway(ax, ay) }
            }
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (_: Exception) {
        }
        // 大量の画像を読む前に前面通知を出し、5秒の startForeground 期限を守る
        if (Prefs.getExpectedRunning(this)) goForeground()
        sync()
    }

    private fun updateMotion() {
        val active = screenOn && Prefs.getTiltEnabled(this) && pets.isNotEmpty()
        if (active) {
            motion.start()
        } else {
            motion.stop()
            lastTiltX = 0f
            lastTiltY = 0f
            pets.values.forEach {
                it.view.setTilt(0f, 0f)
                it.view.setSway(0f, 0f)
            }
            updateWindowPositions(force = true)
        }
    }

    private fun updateWindowPositions(force: Boolean = false) {
        if (pets.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastWindowMoveAt < 33L) return
        lastWindowMoveAt = now

        // 画面移動は感度と切り離し、小さめの固定範囲にする
        val windowX = if (abs(lastTiltX) < 0.03f) 0f else (lastTiltX * 0.7f).coerceIn(-1f, 1f)
        val windowY = if (abs(lastTiltY) < 0.03f) 0f else (lastTiltY * 0.7f).coerceIn(-1f, 1f)
        val area = availableArea()
        val travel = minOf(area.width(), area.height()) * 0.10f
        val dx = (windowX * travel).toInt()
        val dy = (windowY * travel).toInt()

        pets.values.forEach { p ->
            if (p.dragging) return@forEach
            val maxX = maxOf(0, area.width() - p.params.width)
            val maxY = maxOf(0, area.height() - p.params.height)
            val newX = (p.baseX + dx).coerceIn(0, maxX)
            val newY = (p.baseY + dy).coerceIn(0, maxY)
            if (newX != p.params.x || newY != p.params.y) {
                p.params.x = newX
                p.params.y = newY
                try { windowManager.updateViewLayout(p.view, p.params) } catch (_: Exception) {}
            }
        }
    }

    private var reactLogs = 0

    private fun reactAll(window: String?, newFrom: Int, inserted: String? = null, advance: Int = 1) {
        handler.post {
            if (reactLogs < 10) {
                reactLogs++
                DiagLog.log(this, "reactAll pets=${pets.size} advance=$advance")
            }
            pets.values.forEach { p ->
                val idx = if (window == null) -1 else TriggerMatcher.find(window, newFrom, p.keys)
                lastReaction = "match=${if (idx >= 0) idx.toString() else "-"} advance=$advance"
                p.view.react(idx, inserted, advance)
            }
        }
    }

    private fun baseFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        flags = if (Prefs.getPositionLocked(this)) {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return flags
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** ペットを置ける画面領域。回転・折りたたみ・密度変更後の clamp に使う */
    private fun availableArea(): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val wm = getSystemService(WindowManager::class.java)
                val bounds = wm.currentWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return Rect(0, 0, bounds.width(), bounds.height())
                }
            } catch (_: Exception) {
            }
        }
        val dm = resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun pxForAnchor(anchor: Double, max: Int): Int = PositionMath.pxForAnchor(anchor, max)

    private fun anchorForPx(px: Int, max: Int): Double = PositionMath.anchorForPx(px, max)

    /** 設定や画面構成が変わったとき、画像一式は触らずに位置とサイズだけ合わせ直す */
    private fun relayoutWindows() {
        if (pets.isEmpty()) return
        val area = availableArea()
        val sizePx = dpToPx(Prefs.getSizeDp(this))
        pets.values.forEach { p ->
            val maxX = maxOf(0, area.width() - sizePx)
            val maxY = maxOf(0, area.height() - sizePx)
            p.params.width = sizePx
            p.params.height = sizePx
            p.baseX = pxForAnchor(p.fx, maxX)
            p.baseY = pxForAnchor(p.fy, maxY)
            p.params.x = p.baseX
            p.params.y = p.baseY
            try { windowManager.updateViewLayout(p.view, p.params) } catch (_: Exception) {}
        }
        updateMotion()
        updateWindowPositions(force = true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        relayoutWindows()
    }

    /** 表示中キャラの設定に合わせて、ペットを追加・更新・削除する */
    private fun sync() {
        val chars = Prefs.getCharacters(this)
        val shown = Prefs.getShownIds(this)
        val targets = chars.filter { it.id in shown }
        val targetIds = targets.map { it.id }.toSet()

        // 非表示になったキャラを消す
        pets.keys.filter { it !in targetIds }.forEach { id ->
            pets.remove(id)?.let { p -> try { windowManager.removeView(p.view) } catch (e: Exception) {} }
        }

        val sizePx = dpToPx(Prefs.getSizeDp(this))
        val req = sizePx

        // 使われなくなった画像はキャッシュから外す
        val inUse = HashSet<String>()
        targets.forEach { c ->
            inUse.addAll(c.idle); c.steps.forEach { inUse.addAll(it) }; c.triggers.forEach { inUse.addAll(it.images) }
        }
        bitmapCache.snapshot().keys
            .filter { it.substringBefore('|') !in inUse }
            .forEach { bitmapCache.remove(it) }

        fun get(uri: String): Bitmap? {
            val key = "$uri|$req"
            bitmapCache.get(key)?.let { return it }
            val bmp = ImageLoader.load(this, uri, req) ?: return null
            bitmapCache.put(key, bmp)
            return bmp
        }
        val area = availableArea()
        val maxX = maxOf(0, area.width() - sizePx)
        val maxY = maxOf(0, area.height() - sizePx)
        val flags = baseFlags()

        targets.forEach { c ->
            val pet = pets[c.id] ?: createPet(c.id) ?: return@forEach
            pet.params.width = sizePx
            pet.params.height = sizePx
            pet.params.flags = flags
            pet.fx = c.fx
            pet.fy = c.fy
            pet.baseX = pxForAnchor(c.fx, maxX)
            pet.baseY = pxForAnchor(c.fy, maxY)
            pet.params.x = pet.baseX
            pet.params.y = pet.baseY
            try { windowManager.updateViewLayout(pet.view, pet.params) } catch (e: Exception) {}

            pet.view.preset = c.preset
            pet.view.shakeLevel = Prefs.getShakeLevel(this)
            pet.view.showShadow = Prefs.getShowShadow(this)
            pet.view.tiltSensitivityPct = Prefs.getTiltSensitivityPct(this)
            pet.view.setImageSources(
                idle = c.idle,
                steps = c.steps,
                triggers = c.triggers.map { it.images },
                loader = { uri -> get(uri) }
            )
            // 画像が1枚もない項目は照合しない
            val keysAll = c.triggers.map { if (it.images.isEmpty()) emptyList() else it.keys.toList() }
            pet.deleteIdx = keysAll.indexOfFirst { Prefs.DELETE_KEY in it }
            pet.keys = keysAll.map { l -> l.filter { it != Prefs.DELETE_KEY } }
        }

        maxKeyLen = pets.values.flatMap { it.keys.flatten() }.maxOfOrNull { it.length } ?: 0
        watchDel = pets.values.any { it.deleteIdx >= 0 || it.view.usesBuiltInArt }
        watchSpecial = pets.values.any { it.view.hasBuiltInSpecials }
        updateMotion()
        updateWindowPositions(force = true)
    }

    private fun createPet(id: String): Pet? {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val sizePx = dpToPx(Prefs.getSizeDp(this))
        val params = WindowManager.LayoutParams(sizePx, sizePx, type, baseFlags(), PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START

        val pet = Pet(id, PetView(this), params)
        setupTouch(pet)
        return try {
            windowManager.addView(pet.view, params)
            pets[id] = pet
            pet
        } catch (e: Exception) {
            null // オーバーレイ権限が未許可の場合はここに来る
        }
    }

    /** キャラごとにドラッグで移動。離した位置をそのキャラに保存する */
    private fun setupTouch(pet: Pet) {
        var startParamsX = 0
        var startParamsY = 0
        var startBaseX = 0
        var startBaseY = 0
        var touchX = 0f
        var touchY = 0f

        pet.view.setOnTouchListener { _, event ->
            if (Prefs.getPositionLocked(this)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pet.dragging = true
                    startParamsX = pet.params.x
                    startParamsY = pet.params.y
                    startBaseX = pet.baseX
                    startBaseY = pet.baseY
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    pet.baseX = startBaseX + dx
                    pet.baseY = startBaseY + dy
                    pet.params.x = startParamsX + dx
                    pet.params.y = startParamsY + dy
                    try { windowManager.updateViewLayout(pet.view, pet.params) } catch (e: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pet.dragging = false
                    val area = availableArea()
                    val maxX = maxOf(0, area.width() - pet.params.width)
                    val maxY = maxOf(0, area.height() - pet.params.height)
                    pet.baseX = pet.baseX.coerceIn(0, maxX)
                    pet.baseY = pet.baseY.coerceIn(0, maxY)
                    pet.fx = anchorForPx(pet.baseX, maxX)
                    pet.fy = anchorForPx(pet.baseY, maxY)
                    Prefs.setCharAnchor(this, pet.id, pet.fx, pet.fy)
                    updateWindowPositions(force = true)
                    if (event.action == MotionEvent.ACTION_UP) pet.view.react()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        DiagLog.log(this, "OverlayService onDestroy")
        if (::motion.isInitialized) motion.stop()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
        foregroundActive = false
        instance = null
        pets.values.forEach { p -> try { windowManager.removeView(p.view) } catch (e: Exception) {} }
        pets.clear()
        bitmapCache.evictAll()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> bitmapCache.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                bitmapCache.trimToSize(bitmapCache.maxSize() / 2)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        bitmapCache.evictAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setExpectedRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> Prefs.setExpectedRunning(this, true)
            else -> if (!Prefs.getExpectedRunning(this)) {
                // 明示的に止めたあと、STICKY で勝手に戻らないようにする
                stopSelf()
                return START_NOT_STICKY
            }
        }
        goForeground()
        return START_STICKY
    }

    /**
     * 「前面で動いているサービス」にする。
     * 裏にいるアプリはスマホの省電力機能で一時停止されることがあり、
     * そうなるとキャラが触っても動かなくなる(アプリを開くと一気に動く)ので、それを防ぐ。
     * 通知の許可をしていなければ通知は表示されない。前面にできなかった場合もそのまま普通に動く。
     */
    private fun goForeground() {
        try {
            val c = LocaleHelper.wrap(this)
            val channelId = "pet"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(channelId, c.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
                )
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val stop = PendingIntent.getService(
                this, 1,
                Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_notif_paw)
                .setContentTitle(c.getString(R.string.app_name))
                .setContentText(c.getString(R.string.notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_notif_paw,
                        c.getString(R.string.notif_action_stop),
                        stop
                    ).build()
                )
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
            foregroundActive = true
        } catch (e: Exception) {
            // 前面にできない状況でも、ペットはそのまま表示する
            foregroundActive = false
            DiagLog.log(this, "foreground failed: ${e.javaClass.name}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
