package com.maoyou.typingpet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * タイピングに反応するペット。
 * - 待機中: 待機イラストからランダムで1枚
 * - 入力中: 1番目→2番目→…の順に切り替え。各番号では登録された別パターンからランダムで1枚
 * - 文字で切り替え: 登録文字が入力されたら、その画像からランダムで1枚
 * 入力が IDLE_REVERT_DELAY_MS 止まると待機に戻り、次は1番目から始まる。
 * 画像が1枚もなければ内蔵のネコGIFアニメ(待機4種 / 入力3種 / 削除1種)で動く。
 */
class PetView(context: Context) : View(context) {

    companion object {
        private const val IDLE_REVERT_DELAY_MS = 700L
        private const val BOUNCE_DURATION_MS = 420L

        /** 傾き感度 0〜100% をゲイン 0.6〜5.0 に変換する */
        fun tiltGain(pct: Int): Float = 0.6f + (pct.coerceIn(0, 100) / 100f) * 4.4f
    }

    var preset = 0
        set(value) { field = value; invalidate() }

    /** 揺れの強さ 0(なし・画像だけ切り替え)〜3(大きく弾む) */
    var shakeLevel = 3

    /** 足元の影を表示するか */
    var showShadow = true
        set(value) { field = value; invalidate() }

    /** 傾きの感度 0〜100% */
    var tiltSensitivityPct = 25

    /** 設定画面のプレビューでは false にして、GIFの1枚目だけを静止表示する */
    var animateBuiltIn = true

    /** 画像は URI だけ保持し、表示する 1 枚だけを loader で読み込む */
    private var idleSources: List<String> = emptyList()
    private var stepSources: List<List<String>> = emptyList()
    private var triggerSources: List<List<String>> = emptyList()
    private var sourceLoader: ((String) -> Bitmap?)? = null

    private var stepIndex = -1
    private var builtInStep = 0
    private var shown: Bitmap? = null
    private var builtInFace = 0

    private var shownMovie: Movie? = null
    private var movieStart = 0L
    private var hasCustomImages = false
    private var reactLogs = 0
    private var activeState = false
    private var lastReactAt = 0L
    private val tiltX = TiltSpring()
    private val tiltY = TiltSpring()
    private val tiltAngle = TiltSpring()

    /** 端末を動かした勢いで揺れる分。傾きとは別に、少し弾むバネで戻る */
    private val swayX = TiltSpring(stiffness = 144f, damping = 21.6f, settleValue = 0.1f, settleVelocity = 1.2f)
    private val swayY = TiltSpring(stiffness = 144f, damping = 21.6f, settleValue = 0.1f, settleVelocity = 1.2f)
    private var lastTiltFrameAt = 0L
    private var lastSwayAt = 0L

    /** 画像が1枚も登録されていなければ、内蔵のネコアニメで動く */
    val usesBuiltInArt: Boolean get() = !hasCustomImages

    /** ! / ? / 空白 / 削除 の内蔵アニメを持っているか */
    val hasBuiltInSpecials: Boolean get() = specialMovies.values.any { it != null }

    private val idleMovies: List<Movie> = BuiltInArt.idle(context)
    private val typingMovies: List<Movie> = BuiltInArt.typing(context)
    private val specialMovies: Map<String, Movie?> = mapOf(
        "delete" to BuiltInArt.special(context, "delete"),
        "exclamation" to BuiltInArt.special(context, "exclamation"),
        "question" to BuiltInArt.special(context, "question"),
        "space" to BuiltInArt.special(context, "space")
    )

    private var scaleXAnim = 1f
    private var scaleYAnim = 1f
    private var bounceAnimator: ValueAnimator? = null

    private val revertHandler = Handler(Looper.getMainLooper())
    private val revertRunnable = Runnable { goIdle() }

    private val presetColors = listOf(
        "#FF9D6C" to "#7CC9A9",
        "#6CA8FF" to "#FFD76C",
        "#FF8FC7" to "#B98FFF",
        "#8FD9A8" to "#FFB26C"
    )

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cheekPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A2E2C"); style = Paint.Style.STROKE
        strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val inkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A2E2C") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14000000") }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59FFFFFF") }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val moviePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var tintPreset = Int.MIN_VALUE

    init { goIdle() }

    /**
     * 画像一式を URI で差し替える(設定変更時にOverlayServiceから呼ばれる)。
     * triggers は Prefs.getTriggers と同じ並びにすること(番号で対応づけるため)。
     * loader は表示する 1 枚だけを読み込む。
     */
    fun setImageSources(
        idle: List<String>,
        steps: List<List<String>>,
        triggers: List<List<String>>,
        loader: (String) -> Bitmap?
    ) {
        idleSources = idle
        stepSources = steps.filter { it.isNotEmpty() }
        triggerSources = triggers
        sourceLoader = loader
        hasCustomImages = idle.isNotEmpty() || stepSources.isNotEmpty() || triggers.any { it.isNotEmpty() }
        goIdle()
    }

    /** 端末の傾き(-1〜1)に合わせて、窓の中で少し滑らせて首をかしげる */
    fun setTilt(nx: Float, ny: Float) {
        // 感度 0〜100% をゲイン 0.6〜5.0 に変換する
        val gain = tiltGain(tiltSensitivityPct)
        val vx = (nx * gain).coerceIn(-1f, 1f)
        val vy = (ny * gain).coerceIn(-1f, 1f)
        val maxShift = if (hasCustomImages) 0.04f else 0.07f
        val maxAngle = if (hasCustomImages) 7f else 14f
        tiltX.target = vx * width * maxShift
        tiltY.target = vy * height * maxShift
        tiltAngle.target = vx * maxAngle
        invalidate()
    }

    /**
     * 端末を動かした勢い(m/s²)。位移ではなく「揺れの速さ」に加えることで、
     * 加速度で振り子を押す動きになる(押し終わっても慣性で揺れて戻る)。
     */
    fun setSway(ax: Float, ay: Float) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastSwayAt == 0L) 0.02f
        else ((now - lastSwayAt) / 1000f).coerceIn(0.005f, 0.05f)
        lastSwayAt = now

        val maxShift = if (hasCustomImages) 0.08f else 0.12f
        swayX.limit = width * maxShift
        swayY.limit = height * maxShift * 0.8f
        swayX.kick(-ax * width * 12.0f * dt)
        swayY.kick(-ay * height * 8.0f * dt)
        invalidate()
    }

    /** 傾きはバネで追従させ、センサーの更新間隔を描画フレームで埋める */
    private fun stepTilt(): Boolean {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastTiltFrameAt == 0L) 0.016f
        else ((now - lastTiltFrameAt) / 1000f).coerceIn(0.001f, 0.033f)
        lastTiltFrameAt = now
        val movingX = tiltX.step(dt)
        val movingY = tiltY.step(dt)
        val movingAngle = tiltAngle.step(dt)
        val movingSwayX = swayX.step(dt)
        val movingSwayY = swayY.step(dt)
        val moving = movingX || movingY || movingAngle || movingSwayX || movingSwayY
        if (!moving) lastTiltFrameAt = 0L
        return moving
    }

    private class TiltSpring(
        private val stiffness: Float = 256f,
        private val damping: Float = 25.6f,
        private val settleValue: Float = 0.08f,
        private val settleVelocity: Float = 0.8f
    ) {
        var target = 0f
        var value = 0f
        var limit = Float.MAX_VALUE
        private var velocity = 0f

        /** 外から勢いを加える(加速度 × 時間) */
        fun kick(deltaVelocity: Float) {
            velocity += deltaVelocity
        }

        /** 減衰付きバネを1フレーム進める。まだ動いていれば true */
        fun step(dt: Float): Boolean {
            val accel = (target - value) * stiffness - velocity * damping
            velocity += accel * dt
            value = (value + velocity * dt).coerceIn(-limit, limit)
            if (abs(target - value) < settleValue && abs(velocity) < settleVelocity) {
                value = target
                velocity = 0f
                return false
            }
            return true
        }
    }

    private fun goIdle() {
        revertHandler.removeCallbacks(revertRunnable)
        activeState = false
        stepIndex = -1
        builtInStep = -1
        builtInFace = 0
        val uri = idleSources.randomOrNull() ?: stepSources.firstOrNull()?.randomOrNull()
        val idleBmp = uri?.let { sourceLoader?.invoke(it) }
        if (idleBmp != null) {
            shown = idleBmp
            shownMovie = null
        } else {
            shown = null
            showMovie(idleMovies.randomOrNull())
        }
        invalidate()
    }

    /**
     * 打鍵イベントごとに呼ぶ。trigger に「文字で切り替え」の番号を渡すと、その画像からランダムで表示する。
     */
    fun react(trigger: Int = -1, inserted: String? = null, advance: Int = 1) {
        revertHandler.removeCallbacks(revertRunnable)
        activeState = true
        lastReactAt = SystemClock.uptimeMillis()
        val step = advance.coerceAtLeast(0)

        if (step > 0) {
            if (stepSources.isNotEmpty()) stepIndex = (stepIndex + step) % stepSources.size
            builtInStep = (builtInStep + step) % maxOf(typingMovies.size, 1)
            builtInFace = (builtInFace + step - 1) % 3 + 1 // 内蔵イラストは入力中 表情1〜3を循環
        }

        val triggerUri = if (trigger >= 0) triggerSources.getOrNull(trigger)?.randomOrNull() else null
        val stepUri = if (step > 0) stepSources.getOrNull(stepIndex)?.randomOrNull() else null
        val triggerBmp = triggerUri?.let { sourceLoader?.invoke(it) }
        val stepBmp = stepUri?.let { sourceLoader?.invoke(it) }
        val bmp = triggerBmp ?: stepBmp
        val special = specialKeyName(inserted)
        val specialMovie = if (special != null) specialMovies[special] else null
        val changed = bmp != null || special != null || step > 0

        if (bmp != null) {
            shown = bmp
            shownMovie = null
        } else if (special != null) {
            if (specialMovie != null) {
                shown = null
                shownMovie = specialMovie
                movieStart = SystemClock.uptimeMillis()
                specialMovie.setTime(0)
            } else {
                // question.gif などが無い場合は待機に戻す
                showIdleFallback()
            }
        } else if (step > 0) {
            shown = null
            val typing = typingMovies.getOrNull(builtInStep)
            if (typing != null) showMovie(typing) else showIdleFallback()
        }
        if (reactLogs < 10) {
            reactLogs++
            DiagLog.log(
                context,
                "PetView.react trigger=$trigger step=$builtInStep advance=$step typing=${typingMovies.size} idle=${idleMovies.size} bmp=${bmp != null} movie=${shownMovie != null} special=$special"
            )
        }
        if (changed) {
            invalidate()
            if (shakeLevel > 0) playBounce()
        }

        revertHandler.postDelayed(revertRunnable, IDLE_REVERT_DELAY_MS)
    }

    /** ! / ? / 空白に対応する内蔵アニメの名前。なければ null */
    private fun specialKeyName(inserted: String?): String? {
        val ch = inserted?.lastOrNull() ?: return null
        return when (ch) {
            '!', '！' -> "exclamation"
            '?', '？' -> "question"
            ' ', '　' -> "space"
            else -> null
        }
    }

    private fun showIdleFallback() {
        shown = null
        showMovie(idleMovies.randomOrNull())
    }

    /** 「⌫ 消したとき」の内蔵アニメ。画像を登録していない時だけ使う */
    fun reactDelete() {
        val movie = specialMovies["delete"]
        if (!usesBuiltInArt) {
            react()
            return
        }
        if (movie == null) {
            showIdleFallback()
            return
        }
        revertHandler.removeCallbacks(revertRunnable)
        activeState = true
        lastReactAt = SystemClock.uptimeMillis()
        shown = null
        shownMovie = movie
        movieStart = SystemClock.uptimeMillis()
        movie.setTime(0)
        invalidate()
        revertHandler.postDelayed(revertRunnable, IDLE_REVERT_DELAY_MS)
        if (shakeLevel > 0) playBounce()
    }

    /** 同じアニメが表示中なら最初から再生し直さない(連打で固まって見えないように) */
    private fun showMovie(movie: Movie?) {
        if (movie == null) {
            shownMovie = null
            return
        }
        if (shownMovie !== movie) {
            shownMovie = movie
            movieStart = SystemClock.uptimeMillis()
            movie.setTime(0)
        }
    }

    /** 縦に潰れて横に伸び、オーバーシュートしながら戻る「もちもち」アニメ */
    private fun playBounce() {
        bounceAnimator?.cancel()

        val squish = when (shakeLevel) { 1 -> 0.95f; 2 -> 0.90f; else -> 0.82f }
        val stretch = 1f + (1f - squish) * 0.7f
        val tension = when (shakeLevel) { 1 -> 1.4f; 2 -> 1.8f; else -> 2.4f }

        bounceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BOUNCE_DURATION_MS
            interpolator = OvershootInterpolator(tension)
            addUpdateListener {
                val t = it.animatedValue as Float
                scaleYAnim = squish + (1f - squish) * t
                scaleXAnim = stretch + (1f - stretch) * t
                invalidate()
            }
        }
        bounceAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        revertHandler.removeCallbacks(revertRunnable)
        bounceAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeState && SystemClock.uptimeMillis() - lastReactAt > IDLE_REVERT_DELAY_MS + 500) {
            activeState = false
            revertHandler.post(revertRunnable)
        }
        val cx = width / 2f
        val cy = height / 2f

        val tilting = stepTilt()
        drawShadow(canvas, cx, cy)

        canvas.save()
        canvas.translate(tiltX.value + swayX.value, tiltY.value + swayY.value)
        canvas.rotate(tiltAngle.value, cx, cy)
        canvas.scale(scaleXAnim, scaleYAnim, cx, cy + height * 0.16f)

        val bmp = shown
        val movie = shownMovie
        if (bmp != null) drawBitmapFrame(canvas, cx, cy, bmp)
        else if (movie != null) drawMovieFrame(canvas, cx, cy, movie)
        else drawBuiltIn(canvas, cx, cy)

        canvas.restore()

        if (tilting || (movie != null && animateBuiltIn)) postInvalidateOnAnimation()
    }

    /** 影だけは傾きの回転をかけず、ペットの移動と弾みにだけ合わせる */
    private fun drawShadow(canvas: Canvas, cx: Float, cy: Float) {
        if (!showShadow) return
        canvas.save()
        canvas.translate(tiltX.value + swayX.value, tiltY.value + swayY.value)
        canvas.scale(scaleXAnim, scaleYAnim, cx, cy + height * 0.16f)

        val bmp = shown
        val movie = shownMovie
        if (bmp != null) {
            val size = minOf(width, height).toFloat()
            val scale = size / maxOf(bmp.width, bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            canvas.drawOval(RectF(cx - w * 0.4f, cy + h * 0.42f, cx + w * 0.4f, cy + h * 0.5f), shadowPaint)
        } else if (movie != null) {
            val mw = movie.width().coerceAtLeast(1)
            val mh = movie.height().coerceAtLeast(1)
            val scale = minOf(width, height).toFloat() / maxOf(mw, mh)
            val w = mw * scale
            val h = mh * scale
            canvas.drawOval(RectF(cx - w * 0.4f, cy + h * 0.42f, cx + w * 0.4f, cy + h * 0.5f), shadowPaint)
        } else {
            val r = minOf(width, height) * 0.34f
            canvas.drawOval(RectF(cx - r * 0.8f, cy + r * 1.05f, cx + r * 0.8f, cy + r * 1.3f), shadowPaint)
        }
        canvas.restore()
    }

    private fun drawMovieFrame(canvas: Canvas, cx: Float, cy: Float, movie: Movie) {
        val mw = movie.width().coerceAtLeast(1)
        val mh = movie.height().coerceAtLeast(1)
        val scale = minOf(width, height).toFloat() / maxOf(mw, mh)
        val w = mw * scale
        val h = mh * scale

        val duration = movie.duration().coerceAtLeast(1)
        movie.setTime(if (animateBuiltIn) ((SystemClock.uptimeMillis() - movieStart) % duration).toInt() else 0)

        canvas.save()
        canvas.translate(cx - w / 2f, cy - h / 2f)
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f, tintedMoviePaint())
        canvas.restore()
    }

    /** 黒いネコをプリセットカラーに染める。白(目)は白のまま残す */
    private fun tintedMoviePaint(): Paint {
        if (tintPreset == preset) return moviePaint
        tintPreset = preset
        moviePaint.colorFilter = BuiltInArt.tintFilter(preset)
        return moviePaint
    }

    private fun drawBitmapFrame(canvas: Canvas, cx: Float, cy: Float, bmp: Bitmap) {
        val size = minOf(width, height).toFloat()
        val scale = size / maxOf(bmp.width, bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        canvas.drawBitmap(bmp, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), bitmapPaint)
    }

    private fun drawBuiltIn(canvas: Canvas, cx: Float, cy: Float) {
        val r = minOf(width, height) * 0.34f
        val colors = presetColors[preset.coerceIn(0, presetColors.size - 1)]
        bodyPaint.color = Color.parseColor(colors.first)
        cheekPaint.color = Color.parseColor(colors.second)

        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.28f, bodyPaint)
        canvas.drawCircle(cx - r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)
        canvas.drawCircle(cx + r * 0.75f, cy - r * 0.85f, r * 0.14f, cheekPaint)

        canvas.drawCircle(cx, cy, r, bodyPaint)

        canvas.drawCircle(cx - r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)
        canvas.drawCircle(cx + r * 0.62f, cy + r * 0.18f, r * 0.14f, highlightPaint)

        drawFace(canvas, cx, cy, r)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val eyeY = cy - r * 0.12f
        val eyeDx = r * 0.45f
        val eyeR = r * 0.12f
        val mouthY = cy + r * 0.42f

        when (builtInFace % 4) {
            0 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR, inkFill)
                canvas.drawArc(RectF(cx - r * 0.28f, mouthY - r * 0.1f, cx + r * 0.28f, mouthY + r * 0.18f), 20f, 140f, false, inkPaint)
            }
            1 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawCircle(cx + eyeDx, eyeY, eyeR * 1.3f, inkFill)
                canvas.drawOval(RectF(cx - r * 0.16f, mouthY - r * 0.14f, cx + r * 0.16f, mouthY + r * 0.14f), inkFill)
            }
            2 -> {
                canvas.drawArc(RectF(cx - eyeDx - eyeR, eyeY - eyeR, cx - eyeDx + eyeR, eyeY + eyeR), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx + eyeDx - eyeR, eyeY - eyeR, cx + eyeDx + eyeR, eyeY + eyeR), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx - r * 0.3f, mouthY - r * 0.16f, cx + r * 0.3f, mouthY + r * 0.24f), 15f, 150f, false, inkPaint)
            }
            3 -> {
                canvas.drawCircle(cx - eyeDx, eyeY, eyeR, inkFill)
                canvas.drawArc(RectF(cx + eyeDx - eyeR, eyeY - eyeR * 0.4f, cx + eyeDx + eyeR, eyeY + eyeR * 0.4f), 200f, 140f, false, inkPaint)
                canvas.drawArc(RectF(cx - r * 0.26f, mouthY - r * 0.08f, cx + r * 0.26f, mouthY + r * 0.2f), 20f, 140f, false, inkPaint)
            }
        }
    }
}
