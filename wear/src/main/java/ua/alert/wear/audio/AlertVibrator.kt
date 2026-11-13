package ua.alert.wear.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Provides hard, maximum-amplitude emergency vibration on Wear OS (without sound).
 * Uses USAGE_ALARM attributes to ensure haptics fire even in DND / Bedtime modes.
 */
class AlertVibrator(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Starts continuous, maximum-intensity (amplitude 255) hard vibration pulses.
     * Pattern: 600ms hard buzz, 150ms pause, 600ms hard buzz, 150ms pause, 900ms long buzz, 350ms pause.
     */
    fun startRepeatingAlarm() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        stop()
        try {
            val timings = longArrayOf(0, 600, 150, 600, 150, 900, 350)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)

            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, 0)
            } else {
                VibrationEffect.createWaveform(timings, 0)
            }

            vibrateWithAlarmAttributes(v, effect)
            Log.d(TAG, "Started hard repeating alarm vibration (soundless mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hard repeating vibration", e)
        }
    }

    /**
     * Plays a distinct double confirmation vibration pulse for All Clear (Відбій).
     */
    fun playAllClearPulse() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        stop()
        try {
            val timings = longArrayOf(0, 300, 150, 450)
            val amplitudes = intArrayOf(0, 220, 0, 255)

            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }

            vibrateWithAlarmAttributes(v, effect)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play all clear vibration pulse", e)
        }
    }

    private fun vibrateWithAlarmAttributes(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
            v.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            v.vibrate(effect, audioAttrs)
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
    }

    companion object {
        private const val TAG = "AlertVibrator"
    }
}
