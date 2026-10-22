package com.maoyou.typingpet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.hypot

/**
 * 端末の傾きを求めてペットへ渡す。
 *
 * 加速度センサーは重力方向=絶対的な傾きが分かるが、
 * 値をならす必要があって反応がもたつく。
 * ジャイロは回転の速さが分かるので反応は速いが、積分するとドリフトする。
 * そこで両方を相補フィルターで合成する(基準は常に加速度側、ジャイロは先読み担当)。
 * ジャイロが無い端末では加速度センサーだけで動く。
 */
class MotionController(
    private val context: Context,
    private val onTilt: (Float, Float) -> Unit,
    private val onSway: (Float, Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** 重力を除いた加速度(端末を動かしたときの揺れ)。無い端末では加速度から推定する */
    private val linearSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val handler = Handler(Looper.getMainLooper())
    private val gravity = FloatArray(3)
    private val gravitySlow = FloatArray(3)
    private val baseline = FloatArray(3)
    private val gyroRate = FloatArray(3)
    private val linear = FloatArray(3)

    private var started = false
    private var hasGravity = false
    private var hasBaseline = false
    private var hasOutput = false
    private var tiltLogs = 0
    private var swayLogs = 0
    private var lastAccelAt = 0L
    private var outX = 0f
    private var outY = 0f

    fun start() {
        if (started || accelerometer == null) return
        started = true
        hasGravity = false
        hasBaseline = false
        hasOutput = false
        tiltLogs = 0
        swayLogs = 0
        lastAccelAt = 0L
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        linearSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        DiagLog.log(context, "motion start gyro=${gyroscope != null} linear=${linearSensor != null}")
    }

    /** 今の持ち方を基準に戻す。次にセンサーが来た位置がニュートラルになる */
    fun recenter() {
        hasBaseline = false
        hasOutput = false
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager?.unregisterListener(this)
        DiagLog.log(context, "motion stop")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> onGyroscope(event)
            Sensor.TYPE_LINEAR_ACCELERATION -> onLinear(event)
            Sensor.TYPE_ACCELEROMETER -> onAccelerometer(event)
        }
    }

    private fun onGyroscope(event: SensorEvent) {
        // 角速度(rad/s)は揺れるので軽くならす
        val alpha = 0.6f
        for (i in 0..2) {
            gyroRate[i] = alpha * gyroRate[i] + (1f - alpha) * event.values[i]
        }
    }

    /** 端末を動かした勢い。ペットを反対方向へ揺らすのに使う */
    private fun onLinear(event: SensorEvent) {
        val alpha = 0.45f
        for (i in 0..1) {
            linear[i] = alpha * linear[i] + (1f - alpha) * event.values[i]
        }
        emitSway()
    }

    private fun emitSway() {
        if (!hasBaseline) return
        val mag = hypot(linear[0], linear[1])
        // 手ぶれ程度は無視する
        if (mag < 0.5f) {
            onSway(0f, 0f)
            return
        }
        if (swayLogs < 5) {
            swayLogs++
            DiagLog.log(context, "sway ax=${linear[0]} ay=${linear[1]} mag=$mag")
        }
        onSway(linear[0].coerceIn(-25f, 25f), linear[1].coerceIn(-25f, 25f))
    }

    private fun onAccelerometer(event: SensorEvent) {
        if (!hasGravity) {
            event.values.copyInto(gravity)
            event.values.copyInto(gravitySlow)
            hasGravity = true
        } else {
            val alpha = 0.8f
            for (i in 0..2) {
                gravity[i] = alpha * gravity[i] + (1f - alpha) * event.values[i]
            }
            // 揺れ検出用の重力はゆっくり追従させ、加速度を残す
            val slow = 0.95f
            for (i in 0..2) {
                gravitySlow[i] = slow * gravitySlow[i] + (1f - slow) * event.values[i]
            }
        }

        if (!hasBaseline) {
            gravity.copyInto(baseline)
            hasBaseline = true
            hasOutput = false
            outX = 0f
            outY = 0f
            onTilt(0f, 0f)
            return
        }

        // 加速度センサーは重力と逆向きの値を返すので、画面の下方向へ変換する
        val ax = (-(gravity[0] - baseline[0]) / 9.81f).coerceIn(-1f, 1f)
        val ay = ((gravity[1] - baseline[1]) / 9.81f).coerceIn(-1f, 1f)

        val dt = if (lastAccelAt == 0L) 0.02f
        else ((event.timestamp - lastAccelAt) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
        lastAccelAt = event.timestamp

        var px: Float
        var py: Float
        if (hasOutput) {
            // ジャイロの角速度から「次の加速度の値」を先読みする。
            // 回転で重力ベクトルが端末座標系でどれだけ動くか: dg/dt = -ω × g
            val gx = gravity[0]
            val gy = gravity[1]
            val gz = gravity[2]
            val dgx = -gyroRate[1] * gz + gyroRate[2] * gy
            val dgy = -gyroRate[2] * gx + gyroRate[0] * gz
            px = (outX - dgx / 9.81f * dt).coerceIn(-1.2f, 1.2f)
            py = (outY + dgy / 9.81f * dt).coerceIn(-1.2f, 1.2f)
        } else {
            px = ax
            py = ay
            hasOutput = true
        }

        // 加速度センサーの絶対値へ少しずつ寄せて、ジャイロのドリフトを消す
        val correction = 0.3f
        outX = px + (ax - px) * correction
        outY = py + (ay - py) * correction
        onTilt(outX, outY)

        // 線形加速度センサーが無い端末では、加速度から重力を引いて揺れを作る
        if (linearSensor == null) {
            val alpha = 0.3f
            linear[0] = alpha * linear[0] + (1f - alpha) * (event.values[0] - gravitySlow[0])
            linear[1] = alpha * linear[1] + (1f - alpha) * (event.values[1] - gravitySlow[1])
            emitSway()
        }

        val magnitude = hypot(outX, outY)
        if (tiltLogs < 5 && magnitude > 0.15f) {
            tiltLogs++
            DiagLog.log(context, "tilt nx=$outX ny=$outY mag=$magnitude")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
