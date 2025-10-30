package com.example.mobileproject

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.mobileproject.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var tempSensor: Sensor? = null

    lateinit var temperatureCard: CardView
    private lateinit var temperatureMessage: TextView
    private lateinit var musicIcon: ImageView
    private val myApp by lazy { application as MyApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        temperatureCard = findViewById(R.id.temperatureCard)
        temperatureMessage = findViewById(R.id.temperatureMessage)
        musicIcon = findViewById(R.id.musicIcon)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        temperatureCard.visibility = View.GONE

        musicIcon.setOnClickListener {
            myApp.toggleMusic()
            updateMusicIcon()
        }
        updateMusicIcon()
    }

    override fun onResume() {
        super.onResume()
        tempSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun updateMusicIcon() {
        val iconRes = if (myApp.isMusicOn) R.drawable.ic_music else R.drawable.ic_music_off
        musicIcon.setImageResource(iconRes)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val temperature = event.values[0]
            if (temperature != 0.0f) {
                updateTemperatureMessage(temperature)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun updateTemperatureMessage(temp: Float) {
        val message = when {
            temp < 10 -> "It's a bit chilly, take a jacket!"
            temp in 10.0..20.0 -> "The weather is mild and pleasant."
            temp in 20.1..30.0 -> "It's a beautiful day outside!"
            else -> "It's quite hot out there, stay hydrated!"
        }
        temperatureMessage.text = message
        if (tempSensor == null) { // Ensure it's hidden if sensor is not found
            temperatureCard.visibility = View.GONE
        }
    }
}
