package com.example.mobileproject.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.mobileproject.R
import com.example.mobileproject.databinding.FragmentMapBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

/**
 * A data class representing a single location spot retrieved from Firestore.
 * The @PropertyName annotation is crucial for mapping the "map point" field name
 * from the database (which contains a space) to the valid Kotlin property name `map_point`.
 */
data class LocationSpot(
    val id: String = "",
    val name: String? = null,
    @get:PropertyName("map point") @set:PropertyName("map point")
    var map_point: GeoPoint? = null,
    val category: String? = null,
    val description: String? = null
)

/**
 * This Fragment displays the main map, loads location markers from Firestore,
 * and handles user interaction like searching and filtering.
 */
class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback, SearchView.OnQueryTextListener {

    // The core GoogleMap object that is manipulated to display map data.
    private lateinit var googleMap: GoogleMap
    // An instance of the Firebase Firestore database to fetch location data.
    private val firestore = FirebaseFirestore.getInstance()
    // ViewBinding object for safe and easy access to the layout's views.
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    // Caches to hold the markers currently on the map and the full location data.
    // This prevents constant database reads when filtering.
    private val allMarkers = mutableListOf<Marker>()
    private val allLocations = mutableListOf<LocationSpot>()

    /**
     * Called when the fragment's view has been created.
     * This is where view binding is initialized and the map is prepared asynchronously.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        // Asynchronously get the map fragment. The result is delivered to onMapReady().
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Sets up this fragment to listen for text events from the SearchView.
        binding.mapSearchView.setOnQueryTextListener(this)
    }

    /**
     * This callback is triggered when the GoogleMap object is initialized and ready to be used.
     * All major map setup should happen here.
     */
    override fun onMapReady(map: GoogleMap) {
        // Assign the ready GoogleMap object to our class property.
        googleMap = map
        // Enable the zoom-in/zoom-out controls on the map.
        googleMap.uiSettings.isZoomControlsEnabled = true

        // --- PADDING ADJUSTMENT ---
        // This is the crucial adjustment to prevent app UI from covering map UI.
        // We calculate padding in "dp" (density-independent pixels) and convert to raw pixels.
        val topPadding = (160 * resources.displayMetrics.density).toInt() // Accounts for search bar and chips.
        val bottomPadding = (100 * resources.displayMetrics.density).toInt() // Accounts for bottom navigation bar.
        // Apply the padding. This moves the map's built-in controls (zoom, my-location button)
        // into the visible area.
        googleMap.setPadding(0, topPadding, 0, bottomPadding)

        // Set a listener that triggers when a marker's info window (its title) is tapped.
        googleMap.setOnInfoWindowClickListener { marker ->
            navigateToLocationDetail(marker)
        }

        // Begin the process of enabling user location and loading markers.
        enableMyLocation()
        loadLocationMarkersFromFirestore()
    }

    /**
     * Callback for when the user submits their search query (e.g., presses enter).
     */
    override fun onQueryTextSubmit(query: String?): Boolean {
        filterMarkers(query, null) // Filter based on the text query.
        binding.mapSearchView.clearFocus() // Hide the keyboard.
        return true // Indicate that the event was handled.
    }

    /**
     * Callback for when the text in the search view changes.
     */
    override fun onQueryTextChange(newText: String?): Boolean {
        filterMarkers(newText, null) // Filter markers live as the user types.
        return true // Indicate that the event was handled.
    }

    /**
     * Filters the visibility of markers on the map based on a text query and a category.
     * This function iterates through the cached markers and decides if each should be visible.
     */
    private fun filterMarkers(textQuery: String?, categoryQuery: String?) {
        val normalizedTextQuery = textQuery?.trim().orEmpty()

        for (marker in allMarkers) {
            val locationId = marker.tag as String
            val location = allLocations.find { it.id == locationId }

            // A marker is visible if it matches the text query (if any)
            // AND it matches the category query (if any).
            val matchesText = location?.name.orEmpty().contains(normalizedTextQuery, ignoreCase = true)
            val matchesCategory = categoryQuery == null || location?.category == categoryQuery

            marker.isVisible = matchesText && matchesCategory
        }
    }

    /**
     * Navigates to the detail screen for a specific location.
     * It retrieves the location ID stored in the marker's tag.
     */
    private fun navigateToLocationDetail(marker: Marker) {
        val locationId = marker.tag as? String ?: return // Safely get the ID.
        // Use the Navigation Component's generated directions to navigate.
        val action = MapFragmentDirections.actionMapFragmentToLocationDetailFragment(locationId)
        findNavController().navigate(action)
    }

    /**
     * Fetches the initial set of all locations from the Firestore "locations" collection.
     */
    private fun loadLocationMarkersFromFirestore() {
        firestore.collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener

                // Clear any old data before loading new data.
                allMarkers.forEach { it.remove() } // Remove markers from the map
                allMarkers.clear()
                allLocations.clear()

                // Process each document from the Firestore query result.
                for (document in documents) {
                    val spot = document.toObject(LocationSpot::class.java).copy(id = document.id)
                    allLocations.add(spot) // Add the data object to our cache.

                    val geoPoint = spot.map_point
                    val name = spot.name

                    // Only add a marker if it has valid coordinates and a name.
                    if (geoPoint != null && name != null) {
                        val locationLatLng = LatLng(geoPoint.latitude, geoPoint.longitude)
                        val marker = googleMap.addMarker(
                            MarkerOptions().position(locationLatLng).title(name)
                        )
                        // Tag the marker with its unique document ID for later retrieval.
                        marker?.tag = spot.id
                        if (marker != null) {
                            allMarkers.add(marker) // Add the marker object to our cache.
                        }
                    }
                }
                // After all markers are loaded, set up the filter chips.
                setupCategoryChips()
            }
            .addOnFailureListener { exception ->
                Log.e("MapFragment", "Error getting locations from Firestore.", exception)
            }
    }

    /**
     * Dynamically creates and configures the category filter chips based on the loaded location data.
     */
    private fun setupCategoryChips() {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews() // Clear any chips from a previous load.

        // Create a distinct list of all categories found in the location data.
        val categories = allLocations.mapNotNull { it.category }.distinct()

        // Create a default "All" chip to reset the filter.
        val allChip = createChip("All")
        allChip.isCloseIconVisible = false // The "All" chip should not be closable.
        chipGroup.addView(allChip)

        // Create a chip for each unique category.
        for (category in categories) {
            chipGroup.addView(createChip(category))
        }

        // Start with the "All" chip selected.
        allChip.isChecked = true

        // Listen for changes in the selected chip.
        chipGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) {
                // If the user unchecks a chip, default back to "All".
                allChip.isChecked = true
                return@setOnCheckedChangeListener
            }

            val selectedChip = group.findViewById<Chip>(checkedId)
            // Determine the category to filter by. If "All" is selected, the category is null.
            val selectedCategory = if (selectedChip.text.toString() == "All") null else selectedChip.text.toString()

            // Trigger the filter function with the current search text and the selected category.
            filterMarkers(binding.mapSearchView.query.toString(), selectedCategory)
        }
    }

    /**
     * A helper function to create and style a single Chip widget.
     */
    private fun createChip(text: String): Chip {
        // Inflate a chip using the custom style defined in styles.xml.
        val chip = Chip(context, null, R.style.Widget_App_Chip_Filter)
        chip.text = text
        return chip
    }

    /**
     * Checks for location permission and enables the "My Location" layer on the map.
     * This uses the older `requestPermissions` method.
     */
    private fun enableMyLocation() {
        // Check if fine location permission has been granted.
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // If not, request it from the user. The result is handled in onRequestPermissionsResult.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        // --- FINALIZED: CONSOLIDATED ENABLING ---
        // If permission is already granted, enable both the layer and the button.
        // This ensures the button is functional and the blue dot appears.
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        // Attempt to get the last known location.
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                // If a location is found, move the camera to it.
                val userLocation = LatLng(it.latitude, it.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
            } ?: run {
                // If no last location is available, move to a default location.
                val durhamOntario = LatLng(43.9164, -78.8532)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(durhamOntario, 11f))
            }
        }
    }

    /**
     * The callback for the result from requesting permissions.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // If permission was granted, try to enable the location layer again.
                // This will now correctly enable the button and the blue dot.
                enableMyLocation()
            } else {
                // If permission was denied, log it. The "My Location" button will remain disabled.
                Log.d("MapFragment", "Location permission denied by user.")
            }
        }
    }

    /**
     * Called when the fragment's view is being destroyed.
     * It's important to clear the binding here to prevent memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * A companion object to hold constants for the fragment.
     */
    companion object {
        // A unique code to identify our location permission request.
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
