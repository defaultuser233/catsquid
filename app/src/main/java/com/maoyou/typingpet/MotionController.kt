package com.maoyou.typingpet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.hypot

/** 加速度センサーから端末の傾きを求め、ペットを重力方向へ動かす */
class MotionController(
    private val context: Context,
    private val onTilt: (Float, Float) -> Unit,
    private val onEdge: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())
    private val gravity = FloatArray(3)
    private val baseline = FloatArray(3)

    private var started = false
    private var hasGravity = false
    private var hasBaseline = false
    private var edgeLatched = false
    private var lastEdgeAt = 0L
    private var edgeLogs = 0
    private var tiltLogs = 0

    fun start() {
        if (started || accelerometer == null) return
        started = true
        hasGravity = false
        hasBaseline = false
        edgeLatched = false
        tiltLogs = 0
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
        DiagLog.log(context, "motion start")
    }

    /** 今の持ち方を基準に戻す。次にセンサーが来た位置がニュートラルになる */
    fun recenter() {
        hasBaseline = false
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager?.unregisterListener(this)
        DiagLog.log(context, "motion stop")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return

        if (!hasGravity) {
            event.values.copyInto(gravity)
            hasGravity = true
        } else {
            val alpha = 0.85f
            for (i in 0..2) {
                gravity[i] = alpha * gravity[i] + (1f - alpha) * event.values[i]
            }
        }

        if (!hasBaseline) {
            gravity.copyInto(baseline)
            hasBaseline = true
            onTilt(0f, 0f)
            return
        }

        // 加速度センサーは重力と逆向きの値を返すので、画面の下方向へ変換する
        val nx = (-(gravity[0] - baseline[0]) / 9.81f).coerceIn(-1f, 1f)
        val ny = ((gravity[1] - baseline[1]) / 9.81f).coerceIn(-1f, 1f)
        onTilt(nx, ny)

        val magnitude = hypot(nx, ny)
        if (tiltLogs < 5 && magnitude > 0.15f) {
            tiltLogs++
            DiagLog.log(context, "tilt nx=$nx ny=$ny mag=$magnitude")
        }

        val now = SystemClock.uptimeMillis()
        if (!edgeLatched && magnitude > 0.60f) {
            edgeLatched = true
            if (now - lastEdgeAt > 700L) {
                lastEdgeAt = now
                onEdge()
                if (edgeLogs < 5) {
                    edgeLogs++
                    DiagLog.log(context, "tilt edge nx=$nx ny=$ny mag=$magnitude")
                }
            }
        } else if (edgeLatched && magnitude < 0.40f) {
            edgeLatched = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
