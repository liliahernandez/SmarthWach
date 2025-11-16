package com.example.smarthwach

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*

class OtraVentana : ComponentActivity(), SensorEventListener {

    private lateinit var textoHeart: TextView
    private lateinit var textoAccel: TextView
    private lateinit var botonConectar: Button
    private lateinit var botonEnviar: Button

    private lateinit var sensorManager: SensorManager
    private var heartSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    private var heartRate: Int? = null
    private var accelMagnitude: Float? = null

    private var nodeID: String = ""
    private var deviceConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.otra_ventana)

        textoHeart = findViewById(R.id.textoHeart)
        textoAccel = findViewById(R.id.textoAccel)
        botonConectar = findViewById(R.id.botonConectar)
        botonEnviar = findViewById(R.id.botonEnviar)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (heartSensor == null) textoHeart.text = "Sensor de ritmo cardiaco no disponible"
        if (accelSensor == null) textoAccel.text = "Acelerómetro no disponible"

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 1001)
        }

        botonConectar.setOnClickListener {
            Log.d("WearOS", "Intentando conectar con el móvil...")
            obtenerConexionConTelefono()
        }

        botonEnviar.setOnClickListener {
            if (!deviceConnected) {
                Toast.makeText(this, "Conéctate primero al móvil", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (heartRate == null || accelMagnitude == null) {
                Toast.makeText(this, "Esperando datos del sensor...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mensaje =
                "Ritmo: ${heartRate} bpm | Aceleración: ${String.format("%.2f", accelMagnitude)} m/s²"

            Log.d("WearOS", "Enviando mensaje: $mensaje")
            sendMessageToPhone(mensaje)
        }
    }

    private fun obtenerConexionConTelefono() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes.first().id
                    deviceConnected = true
                    Log.d("WearOS", "Conectado al móvil: ${nodes.first().displayName}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Conectado al móvil", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "No hay móviles conectados", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("WearOS", "Error al conectar: ${e.message}")
            }
        }
    }

    private fun sendMessageToPhone(message: String) {
        if (nodeID.isEmpty()) {
            Toast.makeText(this, "Sin conexión con el móvil", Toast.LENGTH_SHORT).show()
            return
        }

        Wearable.getMessageClient(this)
            .sendMessage(nodeID, "/SENSOR_DATA", message.toByteArray())
            .addOnSuccessListener {
                Toast.makeText(this, "Datos enviados al móvil", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show()
                Log.e("WearOS", "Error al enviar mensaje: ${it.message}")
            }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                val value = event.values[0].toInt()
                if (value > 0) {
                    heartRate = value
                    textoHeart.text = "Ritmo cardiaco: $value bpm"
                    Log.d("WearOS", "Ritmo cardiaco: $value bpm")
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val mag = kotlin.math.sqrt(x * x + y * y + z * z)
                accelMagnitude = mag
                textoAccel.text = "Acelerómetro: ${String.format("%.2f", mag)} m/s²"
                Log.d("WearOS", "Acelerómetro (mag): $mag m/s²")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        heartSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
