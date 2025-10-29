package com.example.mobileproject.fragments

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.MainActivity
import com.example.mobileproject.adapters.AdventureAdapter
import com.example.mobileproject.databinding.FragmentHomeBinding
import com.example.mobileproject.models.Adventure
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mobileproject.adapters.AdventureListItem

class HomeFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var categories: List<String> = emptyList()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime: Long = 0
    private var lastX: Float = 0.0f
    private var lastY: Float = 0.0f
    private var lastZ: Float = 0.0f
    private var isFirstReading = true

    private val adventureAdapter = AdventureAdapter { adventure ->
        Toast.makeText(context, "${adventure.name} clicked!", Toast.LENGTH_SHORT).show()
    }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSpinButton()
        setupAdventureGeneratorButton()

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        binding.scrim.setOnClickListener {
            binding.adventurePopupContainer.isVisible = false
        }

        if (categories.isNotEmpty()) {
            binding.luckyWheelView.setData(categories)
        }
    }

    override fun onResume() {
        super.onResume()
        // --- My change: Show temperature card ---
        (requireActivity() as MainActivity).temperatureCard.visibility = View.VISIBLE

        if (categories.isEmpty()) {
            fetchCategoriesAndSetupWheel()
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isFirstReading = true
    }

    override fun onPause() {
        super.onPause()
        // --- My change: Hide temperature card ---
        (requireActivity() as MainActivity).temperatureCard.visibility = View.GONE

        sensorManager.unregisterListener(this)
    }

    private fun setupRecyclerView() {
        binding.adventureRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adventureAdapter
        }
    }

    private fun fetchCategoriesAndSetupWheel() {
        binding.wheelProgressBar.isVisible = true
        disableButtons()

        firestore.collection("locations")
            .get().addOnSuccessListener { documents ->
                binding.wheelProgressBar.isVisible = false
                if (documents.isEmpty()) {
                    Toast.makeText(context, "No locations found in database.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val uniqueCategories = documents.mapNotNull { it.getString("category") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                this.categories = uniqueCategories.sorted()

                if (categories.isNotEmpty()) {
                    binding.luckyWheelView.setData(categories)
                    enableButtons()
                } else {
                    Toast.makeText(context, "Could not find any categories in database.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                binding.wheelProgressBar.isVisible = false
                Log.e("HomeFragment", "Error fetching categories: ", exception)
                Toast.makeText(context, "Failed to load categories from database.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSpinButton() {
        binding.spinButton.setOnClickListener {
            if (categories.isEmpty()) {
                Toast.makeText(context, "Categories are still loading.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            disableButtons()
            binding.adventurePopupContainer.isVisible = false
            adventureAdapter.submitList(emptyList())

            val targetIndex = (0 until categories.size).random()

            binding.luckyWheelView.rotateWheelTo(targetIndex) {
                val selectedCategory = categories[targetIndex]
                binding.resultText.text = "✨ Adventure awaits in: $selectedCategory ✨"
                fetchLocationsForCategory(selectedCategory)
            }
        }
    }

    private fun fetchLocationsForCategory(category: String) {
        firestore.collection("locations")
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { documents ->
                binding.resultContainer.isVisible = true

                if (documents.isEmpty()) {
                    Toast.makeText(context, "No locations found for '$category'", Toast.LENGTH_SHORT).show()
                } else {
                    val locationsList = documents.toObjects(Adventure::class.java)
                    val randomLocation = locationsList.random()
                    val singleItem = AdventureListItem.SingleLocation(randomLocation)
                    adventureAdapter.submitList(listOf(singleItem))
                }
                binding.adventurePopupContainer.isVisible = true
                enableButtons()
            }
            .addOnFailureListener { exception ->
                Log.e("HomeFragment", "Error getting documents for category: ", exception)
                Toast.makeText(context, "Failed to load locations.", Toast.LENGTH_SHORT).show()
                enableButtons()
            }
    }

    private fun setupAdventureGeneratorButton() {
        binding.generateAdventureButton.setOnClickListener {
            generateAdventureDay()
        }
    }

    private fun generateAdventureDay() {
        disableButtons()
        binding.adventurePopupContainer.isVisible = false
        adventureAdapter.submitList(emptyList())

        binding.resultContainer.isVisible = false

        val locationsCollection = firestore.collection("locations")

        locationsCollection.whereEqualTo("category", "Food & Drink").get()
            .addOnSuccessListener { foodDocuments ->
                if (foodDocuments.isEmpty()) {
                    Toast.makeText(context, "No 'Food & Drink' locations found.", Toast.LENGTH_SHORT).show()
                    enableButtons()
                    return@addOnSuccessListener
                }
                val randomFoodLocation = foodDocuments.toObjects(Adventure::class.java).random()

                val otherCategories = categories.filter { it.equals("Food & Drink", ignoreCase = true).not() }
                if (otherCategories.isEmpty()) {
                    Toast.makeText(context, "No other activity categories found.", Toast.LENGTH_SHORT).show()
                    enableButtons()
                    return@addOnSuccessListener
                }
                val randomActivityCategory = otherCategories.random()

                locationsCollection.whereEqualTo("category", randomActivityCategory).get()
                    .addOnSuccessListener { activityDocuments ->
                        if (activityDocuments.isEmpty()) {
                            Toast.makeText(context, "Could not find a location for the activity.", Toast.LENGTH_SHORT).show()
                            enableButtons()
                            return@addOnSuccessListener
                        }
                        val randomActivityLocation = activityDocuments.toObjects(Adventure::class.java).random()

                        val adventureDayItem = AdventureListItem.AdventureDay(randomFoodLocation, randomActivityLocation)
                        adventureAdapter.submitList(listOf(adventureDayItem))

                        binding.adventurePopupContainer.isVisible = true
                        enableButtons()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to load activity.", Toast.LENGTH_SHORT).show()
                        enableButtons()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load food location.", Toast.LENGTH_SHORT).show()
                enableButtons()
            }
    }

    private fun enableButtons() {
        binding.spinButton.isEnabled = true
        binding.generateAdventureButton.isEnabled = true
    }

    private fun disableButtons() {
        binding.spinButton.isEnabled = false
        binding.generateAdventureButton.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            handleShake(event)
        }
    }

    private fun handleShake(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastShakeTime) > 500) { // Debounce shakes
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (!isFirstReading) {
                val deltaX = Math.abs(lastX - x)
                val deltaY = Math.abs(lastY - y)
                val deltaZ = Math.abs(lastZ - z)

                val shakeThreshold = 10f

                if (deltaX > shakeThreshold || deltaY > shakeThreshold || deltaZ > shakeThreshold) {
                    if (binding.spinButton.isEnabled) {
                        lastShakeTime = currentTime
                        Log.d("HomeFragment", "Shake detected! Spinning the wheel.")
                        binding.spinButton.performClick()
                    }
                }
            }

            lastX = x
            lastY = y
            lastZ = z
            isFirstReading = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}
