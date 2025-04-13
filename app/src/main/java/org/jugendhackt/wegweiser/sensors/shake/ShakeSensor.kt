package org.jugendhackt.wegweiser.sensors.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeSensor(private val context: Context) {
    private val listenerList = mutableListOf<() -> Unit>()

    fun add(eventListener: () -> Unit) = listenerList.add(eventListener)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val a =
                    it.values[0] * it.values[0] + it.values[1] * it.values[1] + it.values[2] * it.values[2]
                if (a > 500)
                    Log.d("ACC", a.toString())
                if (a > 800)
                    listenerList.forEach { listener -> listener.invoke() }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorShake = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(listener, sensorShake, SensorManager.SENSOR_DELAY_NORMAL)
    }
}