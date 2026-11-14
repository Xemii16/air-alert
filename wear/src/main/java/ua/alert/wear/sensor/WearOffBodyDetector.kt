package ua.alert.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Uses Android's low-latency off-body sensor (Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
 * to determine whether the Wear OS watch is currently worn on the user's wrist.
 * Fails open (treats as on-wrist) if the hardware sensor is absent or does not report in time.
 */
class WearOffBodyDetector(context: Context) {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val offBodySensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    suspend fun isWatchOnWrist(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        val manager = sensorManager ?: return true
        val sensor = offBodySensor ?: return true

        val deferred = CompletableDeferred<Boolean>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawValue = event.values.firstOrNull() ?: return
                if (!deferred.isCompleted) {
                    deferred.complete(isOnBodyReading(rawValue))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = try {
            manager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register off-body sensor listener: ${e.message}")
            false
        }

        if (!registered) {
            return true
        }

        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
                Log.w(TAG, "Off-body sensor timed out after ${timeoutMs}ms; defaulting to on-wrist")
                true
            }
        } finally {
            try {
                manager.unregisterListener(listener)
            } catch (_: Exception) {
            }
        }
    }

    fun observeWhileActive(onWristStateChanged: (Boolean) -> Unit): AutoCloseable {
        val manager = sensorManager ?: return AutoCloseable { }
        val sensor = offBodySensor ?: return AutoCloseable { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawValue = event.values.firstOrNull() ?: return
                onWristStateChanged(isOnBodyReading(rawValue))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = try {
            manager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to observe off-body sensor: ${e.message}")
            false
        }

        return AutoCloseable {
            if (registered) {
                try {
                    manager.unregisterListener(listener)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "WearOffBodyDetector"
        private const val DEFAULT_TIMEOUT_MS = 800L

        /**
         * Per Android Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT contract:
         * 0.0 -> off-body (not worn on wrist), non-zero (typically 1.0) -> on-body.
         */
        fun isOnBodyReading(sensorValue: Float): Boolean {
            return !sensorValue.isNaN() && sensorValue != 0.0f
        }
    }
}
