package com.example.gpsphotapp.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2

/**
 * Overlay 3x3 Rule-of-Thirds grid, equivalente di GridOverlayView su iOS.
 */
@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = 0.5f)
        // Vertical
        drawLine(color, androidx.compose.ui.geometry.Offset(w / 3, 0f),
            androidx.compose.ui.geometry.Offset(w / 3, h), strokeWidth = 1.5f)
        drawLine(color, androidx.compose.ui.geometry.Offset(2 * w / 3, 0f),
            androidx.compose.ui.geometry.Offset(2 * w / 3, h), strokeWidth = 1.5f)
        // Horizontal
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, h / 3),
            androidx.compose.ui.geometry.Offset(w, h / 3), strokeWidth = 1.5f)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 2 * h / 3),
            androidx.compose.ui.geometry.Offset(w, 2 * h / 3), strokeWidth = 1.5f)
    }
}

/**
 * Overlay livella che ruota in base ad accelerometro, equivalente di LevelOverlayView.
 */
@Composable
fun LevelOverlay() {
    val context = LocalContext.current
    var rollDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val x = event.values[0]
                val y = event.values[1]
                rollDeg = (atan2(x, -y) * 180f / Math.PI).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val level = (kotlin.math.abs(rollDeg) < 2f) ||
            (kotlin.math.abs(kotlin.math.abs(rollDeg) - 180f) < 2f)
    val color = if (level) Color(0xFF00C853) else Color.White

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val halfLen = size.width * 0.25f
        val angleRad = (-rollDeg * Math.PI / 180.0).toFloat()
        val dx = halfLen * kotlin.math.cos(angleRad)
        val dy = halfLen * kotlin.math.sin(angleRad)
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(cx - dx, cy - dy),
            androidx.compose.ui.geometry.Offset(cx + dx, cy + dy),
            strokeWidth = 3f
        )
        drawCircle(color, radius = 4f,
            center = androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
