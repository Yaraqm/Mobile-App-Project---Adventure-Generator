package com.example.mobileproject.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.R
import com.example.mobileproject.adapters.ReviewAdapter
import com.example.mobileproject.databinding.FragmentLocationDetailBinding
import com.example.mobileproject.models.Review
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.PropertyName
import java.net.URLEncoder

data class LocationDetails(
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val city: String? = null,

    @get:PropertyName("map point") @set:PropertyName("map point")
    var map_point: GeoPoint? = null
)

class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!
    private val args: LocationDetailFragmentArgs by navArgs()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var currentLocationId: String

    private var currentLocationDetails: LocationDetails? = null

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
        checkIfFavorited()
        checkIfVisited()
        setupReviewsRecyclerView()


        binding.btnAddFavorite.setOnClickListener { addOrRemoveFavorite() }
        binding.btnVisited.setOnClickListener { addOrRemoveVisited() }
        binding.btnLeaveReview.setOnClickListener { showLeaveReviewDialog() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }


        binding.btnGetDirections.setOnClickListener {
            currentLocationDetails?.let { details ->
                launchDirectionsIntent(details)
            }
        }
    }


    private fun launchDirectionsIntent(details: LocationDetails) {
        val locationName = details.name
        val locationCity = details.city

        // We need the name of the location to perform a search.
        if (locationName.isNullOrEmpty()) {
            Toast.makeText(context, "Location name is not available.", Toast.LENGTH_SHORT).show()
            Log.e("Directions", "Cannot launch intent because location name is null or empty.")
            return
        }

        // Combining the name and city creates a more specific and accurate search query.
        // We also URL-encode the query to handle special characters like spaces.
        val searchQuery = URLEncoder.encode("$locationName, $locationCity", "UTF-8")
        Log.d("Directions", "Launching search for: $searchQuery")

        // The "geo:0,0?q=" URI is a search intent. It shows the query result on the map.
        // The user can then tap "Directions" within Google Maps to start navigation.
        val gmmIntentUri = Uri.parse("geo:0,0?q=$searchQuery")

        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(context, "Google Maps is not installed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchLocationDetails(locationId: String) {
        firestore.collection("locations").document(locationId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val location = document.toObject(LocationDetails::class.java)
                    if (location != null) {
                        currentLocationDetails = location
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

    private fun setupReviewsRecyclerView() {
        binding.reviewsRecyclerView.layoutManager = LinearLayoutManager(context)
        firestore.collection("locations").document(currentLocationId)
            .collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("LocationDetailFragment", "No reviews found.")
                } else {
                    val reviews = documents.toObjects(Review::class.java)
                    val adapter = ReviewAdapter(reviews)
                    binding.reviewsRecyclerView.adapter = adapter
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LocationDetailFragment", "Error getting reviews: ", exception)
            }
    }

    private fun showLeaveReviewDialog() {
        val user = auth.currentUser ?: run {
            Toast.makeText(context, "You must be logged in to leave a review.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_leave_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rating_bar)
        val editTextReview = dialogView.findViewById<EditText>(R.id.edit_text_review)

        AlertDialog.Builder(requireContext())
            .setTitle("Leave a Review")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val rating = ratingBar.rating
                val reviewText = editTextReview.text.toString().trim()
                if (reviewText.isNotEmpty() && rating > 0) submitReview(user, rating, reviewText)
                else Toast.makeText(context, "Please provide a rating and a comment.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReview(user: FirebaseUser, rating: Float, reviewText: String) {
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val userName = userDoc?.getString("name") ?: "Anonymous"
                val userPhotoUrl = userDoc?.getString("photoUrl")
                val review = Review(userId = user.uid, userName = userName, userPhotoUrl = userPhotoUrl, rating = rating, text = reviewText)
                firestore.collection("locations").document(currentLocationId)
                    .collection("reviews")
                    .add(review)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Review submitted!", Toast.LENGTH_SHORT).show()
                        setupReviewsRecyclerView() // Refresh reviews list
                    }
            }
    }

    private fun checkIfFavorited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
            val favoritedLocations = document?.get("favorited_locations") as? List<*>
            updateFavoriteButtonUI(favoritedLocations?.contains(currentLocationId) == true)
        }
    }

    private fun addOrRemoveFavorite() {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(context, "You must be logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyFavorited = (binding.btnAddFavorite as MaterialButton).text == "Favorited"
        val firestoreUpdate = if (isCurrentlyFavorited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        userDocRef.update("favorited_locations", firestoreUpdate).addOnSuccessListener {
            updateFavoriteButtonUI(!isCurrentlyFavorited)
        }
    }

    private fun updateFavoriteButtonUI(isFavorited: Boolean) {
        (binding.btnAddFavorite as MaterialButton).apply {
            if (isFavorited) {
                text = "Favorited"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_filled)
            } else {
                text = "Add to Favorites"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border)
            }
        }
    }

    private fun checkIfVisited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
            val visitedLocations = document?.get("visited_locations") as? List<*>
            updateVisitedButtonUI(visitedLocations?.contains(currentLocationId) == true)
        }
    }

    private fun addOrRemoveVisited() {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(context, "You must be logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyVisited = (binding.btnVisited as MaterialButton).text == "Visited"
        val firestoreUpdate = if (isCurrentlyVisited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        userDocRef.update("visited_locations", firestoreUpdate).addOnSuccessListener {
            updateVisitedButtonUI(!isCurrentlyVisited)
        }
    }

    private fun updateVisitedButtonUI(isVisited: Boolean) {
        (binding.btnVisited as MaterialButton).apply {
            if (isVisited) {
                text = "Visited"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
            } else {
                text = "I Visited!"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_box_outline_blank)
            }
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
