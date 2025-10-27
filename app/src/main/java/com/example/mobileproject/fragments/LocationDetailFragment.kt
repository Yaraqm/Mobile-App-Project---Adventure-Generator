package com.example.mobileproject.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.example.mobileproject.R
import com.example.mobileproject.databinding.FragmentLocationDetailBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

data class LocationDetails(
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val map_point: GeoPoint? = null
)

class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!
    private val args: LocationDetailFragmentArgs by navArgs()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var currentLocationId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentLocationId = args.locationId
        Log.d("LocationDetailFragment", "Received location ID: $currentLocationId")

        fetchLocationDetails(currentLocationId)

        // Check status for BOTH buttons
        checkIfFavorited()
        checkIfVisited() // <<< --- NEW

        // Set up click listeners for BOTH buttons
        binding.btnAddFavorite.setOnClickListener {
            addOrRemoveFavorite()
        }
        binding.btnVisited.setOnClickListener { // <<< --- NEW
            addOrRemoveVisited()
        }
    }

    private fun fetchLocationDetails(locationId: String) {
        firestore.collection("locations").document(locationId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val location = document.toObject(LocationDetails::class.java)
                    if (location != null) {
                        updateUi(location)
                    } else {
                        Log.e("LocationDetailFragment", "Failed to parse document.")
                    }
                } else {
                    Log.e("LocationDetailFragment", "No such document with ID: $locationId")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LocationDetailFragment", "Error getting location details", exception)
            }
    }

    // --- FAVORITES LOGIC (NO CHANGES) ---
    private fun checkIfFavorited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val favoritedLocations = document.get("favorited_locations") as? List<*>
                    val isFavorited = favoritedLocations?.contains(currentLocationId) == true
                    updateFavoriteButtonUI(isFavorited)
                } else {
                    updateFavoriteButtonUI(false)
                }
            }
    }

    private fun addOrRemoveFavorite() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to manage favorites", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyFavorited = (binding.btnAddFavorite as MaterialButton).text == "Favorited"
        val firestoreUpdate = if (isCurrentlyFavorited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        val toastMessage = if (isCurrentlyFavorited) "Removed from favorites" else "Added to favorites!"
        val newFavoriteState = !isCurrentlyFavorited

        userDocRef.update("favorited_locations", firestoreUpdate)
            .addOnSuccessListener {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                updateFavoriteButtonUI(newFavoriteState)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error updating favorites", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateFavoriteButtonUI(isFavorited: Boolean) {
        val button = binding.btnAddFavorite as MaterialButton
        if (isFavorited) {
            button.text = "Favorited"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_filled)
        } else {
            button.text = "Add to Favorites"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border)
        }
    }

    // --- VISITED LOGIC (ALL NEW) ---

    /**
     * <<< --- NEW FUNCTION --- >>>
     * Checks if the current location is already in the user's visited list.
     */
    private fun checkIfVisited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val visitedLocations = document.get("visited_locations") as? List<*>
                    val isVisited = visitedLocations?.contains(currentLocationId) == true
                    updateVisitedButtonUI(isVisited)
                } else {
                    updateVisitedButtonUI(false)
                }
            }
    }

    /**
     * <<< --- NEW FUNCTION --- >>>
     * Adds or removes the location from the user's visited list.
     */
    private fun addOrRemoveVisited() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to log visits", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyVisited = (binding.btnVisited as MaterialButton).text == "Visited"
        val firestoreUpdate = if (isCurrentlyVisited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        val toastMessage = if (isCurrentlyVisited) "Removed from visited log" else "Added to visited!"
        val newVisitedState = !isCurrentlyVisited

        userDocRef.update("visited_locations", firestoreUpdate)
            .addOnSuccessListener {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                updateVisitedButtonUI(newVisitedState)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error updating visited log", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * <<< --- NEW FUNCTION --- >>>
     * Updates the "I Visited!" button's appearance.
     */
    private fun updateVisitedButtonUI(isVisited: Boolean) {
        val button = binding.btnVisited as MaterialButton
        if (isVisited) {
            button.text = "Visited"
            // You might want a different icon for visited, like 'ic_check'
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
        } else {
            button.text = "I Visited!"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_box_outline_blank)
        }
    }

    private fun updateUi(location: LocationDetails) {
        binding.textLocationName.text = location.name
        binding.textLocationCategory.text = location.category
        binding.textLocationDescription.text = location.description
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
