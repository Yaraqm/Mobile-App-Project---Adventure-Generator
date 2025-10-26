package com.example.mobileproject.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.adapters.AdventureAdapter
import com.example.mobileproject.databinding.FragmentHomeBinding
import com.example.mobileproject.models.Adventure
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mobileproject.adapters.AdventureListItem

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var categories: List<String> = emptyList()

    private val adventureAdapter = AdventureAdapter { adventure ->
        // Handle a click on any adventure item
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

        // Add listener to hide the popup when the background is clicked
        binding.scrim.setOnClickListener {
            binding.adventurePopupContainer.isVisible = false
        }

        if (categories.isNotEmpty()) {
            Log.d("HomeFragment", "View recreated. Repopulating wheel with existing data.")
            binding.luckyWheelView.setData(categories)
        }
    }

    override fun onResume() {
        super.onResume()
        if (categories.isEmpty()) {
            Log.d("HomeFragment", "Categories are empty. Fetching from network.")
            fetchCategoriesAndSetupWheel()
        }
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
                if (documents.isEmpty) {
                    Toast.makeText(context, "No locations found in database.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val uniqueCategories = documents.mapNotNull { it.getString("category") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                this.categories = uniqueCategories.sorted()

                if (categories.isNotEmpty()) {
                    Log.d("HomeFragment", "Final categories loaded for wheel: $categories")
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
            // --- Hide the main popup container to clear previous results ---
            binding.adventurePopupContainer.isVisible = false
            adventureAdapter.submitList(emptyList())

            val targetIndex = (0 until categories.size).random()

            binding.luckyWheelView.rotateWheelTo(targetIndex) {
                // This block runs AFTER the wheel stops
                val selectedCategory = categories[targetIndex]

                // Set the text for the result card (which is now inside the popup)
                binding.resultText.text = "✨ Adventure awaits in: $selectedCategory ✨"

                // Fetch the location, which will show the recycler view part of the popup
                fetchLocationsForCategory(selectedCategory)

                // The buttons are re-enabled inside fetchLocationsForCategory
            }
        }
    }

    private fun fetchLocationsForCategory(category: String) {
        firestore.collection("locations")
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { documents ->
                // This card with the text should only show up for the spin result
                binding.resultContainer.isVisible = true

                if (documents.isEmpty) {
                    Toast.makeText(context, "No locations found for '$category'", Toast.LENGTH_SHORT).show()
                } else {
                    val locationsList = documents.toObjects(Adventure::class.java)
                    val randomLocation = locationsList.random()
                    val singleItem = AdventureListItem.SingleLocation(randomLocation)

                    // Populate the adapter with the single random location
                    adventureAdapter.submitList(listOf(singleItem))
                }
                // Show the entire popup container now
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
        // --- Hide the main popup container to clear previous results ---
        binding.adventurePopupContainer.isVisible = false
        adventureAdapter.submitList(emptyList())

        // Make sure the "Adventure awaits in..." text card is hidden for this case
        binding.resultContainer.isVisible = false

        val locationsCollection = firestore.collection("locations")

        locationsCollection.whereEqualTo("category", "Food & Drink").get()
            .addOnSuccessListener { foodDocuments ->
                if (foodDocuments.isEmpty) {
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
                        if (activityDocuments.isEmpty) {
                            Toast.makeText(context, "Could not find a location for the activity.", Toast.LENGTH_SHORT).show()
                            enableButtons()
                            return@addOnSuccessListener
                        }
                        val randomActivityLocation = activityDocuments.toObjects(Adventure::class.java).random()

                        val adventureDayItem = AdventureListItem.AdventureDay(randomFoodLocation, randomActivityLocation)
                        adventureAdapter.submitList(listOf(adventureDayItem))

                        // Show the popup container
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
}

