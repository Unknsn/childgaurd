package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlertNotifier(private val context: Context, private val scope: CoroutineScope) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var soundJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 95)
        } catch (e: Exception) {
            Log.e("AlertNotifier", "Failed to init ToneGenerator", e)
        }
    }

    fun startAlert(riskLevel: RiskLevel) {
        stopAlert()

        // 1. Vibration
        try {
            val timings = when (riskLevel) {
                RiskLevel.HIGH -> longArrayOf(0, 600, 200, 600, 200, 900)
                RiskLevel.MEDIUM -> longArrayOf(0, 350, 250, 350, 250)
                else -> longArrayOf(0, 150)
            }
            val amplitudes = when (riskLevel) {
                RiskLevel.HIGH -> intArrayOf(0, 255, 0, 255, 0, 255)
                RiskLevel.MEDIUM -> intArrayOf(0, 180, 0, 180, 0)
                else -> intArrayOf(0, 120)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // repeat at index 0
                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                vibrator?.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, 0)
            }
        } catch (e: Exception) {
            Log.e("AlertNotifier", "Vibration failed", e)
        }

        // 2. Sound pulses
        soundJob = scope.launch(Dispatchers.Default) {
            val toneType = when (riskLevel) {
                RiskLevel.HIGH -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                RiskLevel.MEDIUM -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            val pulseDuration = when (riskLevel) {
                RiskLevel.HIGH -> 600
                RiskLevel.MEDIUM -> 400
                else -> 200
            }
            val pauseDuration = when (riskLevel) {
                RiskLevel.HIGH -> 350L
                RiskLevel.MEDIUM -> 500L
                else -> 1000L
            }

            while (isActive) {
                try {
                    toneGenerator?.startTone(toneType, pulseDuration)
                } catch (e: Exception) {
                    Log.e("AlertNotifier", "Error playing tone", e)
                }
                delay(pulseDuration + pauseDuration)
            }
        }
    }

    fun stopAlert() {
        soundJob?.cancel()
        soundJob = null
        try {
            toneGenerator?.stopTone()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlertNotifier", "Error stopping alert", e)
        }
    }

    fun release() {
        stopAlert()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }
}
