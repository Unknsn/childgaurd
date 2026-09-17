package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Continuously monitors the accelerometer.
 * If acceleration spikes above a threshold and stays elevated
 * for a short confirmation window (or triggers repeat spikes),
 * it raises a "Motion Anomaly" flag.
 */
class MotionAnomalyDetector(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _currentMagnitude = MutableStateFlow(9.8f)
    val currentMagnitude: StateFlow<Float> = _currentMagnitude.asStateFlow()

    private val _isAnomalyActive = MutableStateFlow(false)
    val isAnomalyActive: StateFlow<Boolean> = _isAnomalyActive.asStateFlow()

    private var spikeCount = 0
    private var firstSpikeTime = 0L
    private var resetJob: Job? = null

    // Gravity is approx 9.8 m/s^2. A spike > 21 m/s^2 indicates severe impact or vigorous shaking.
    private val spikeThreshold = 21.0f
    private val confirmationWindowMs = 2500L
    private val requiredSpikeCount = 3

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        resetJob?.cancel()
        _isAnomalyActive.value = false
        spikeCount = 0
    }

    fun triggerSimulatedAnomaly() {
        _isAnomalyActive.value = true
        _currentMagnitude.value = 28.5f
    }

    fun clearAnomaly() {
        _isAnomalyActive.value = false
        spikeCount = 0
        resetJob?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        _currentMagnitude.value = magnitude

        val currentTime = System.currentTimeMillis()

        if (magnitude > spikeThreshold) {
            if (spikeCount == 0 || (currentTime - firstSpikeTime) > confirmationWindowMs) {
                spikeCount = 1
                firstSpikeTime = currentTime
            } else {
                spikeCount++
            }

            if (spikeCount >= requiredSpikeCount) {
                _isAnomalyActive.value = true
            }

            // Schedule auto-reset of spike count if no further motion occurs
            resetJob?.cancel()
            resetJob = scope.launch(Dispatchers.Default) {
                delay(confirmationWindowMs)
                spikeCount = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
