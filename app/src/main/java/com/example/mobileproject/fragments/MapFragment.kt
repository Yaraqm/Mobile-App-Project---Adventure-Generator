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
import com.google.android.material.chip.Chip // <<< --- NEW IMPORT
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

data class LocationSpot(
    val id: String = "",
    val name: String? = null,
    val map_point: GeoPoint? = null,
    val category: String? = null,
    val description: String? = null
)

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback, SearchView.OnQueryTextListener {

    private lateinit var googleMap: GoogleMap
    private val firestore = FirebaseFirestore.getInstance()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    // A list to hold all the markers placed on the map
    private val allMarkers = mutableListOf<Marker>()
    // A list to hold all location data from Firestore
    private val allLocations = mutableListOf<LocationSpot>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        binding.mapSearchView.setOnQueryTextListener(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        // Adjust padding to account for the search bar AND the new chip group
        val topPadding = (120 * resources.displayMetrics.density).toInt()
        val bottomPadding = (60 * resources.displayMetrics.density).toInt()
        googleMap.setPadding(0, topPadding, 0, bottomPadding)

        googleMap.setOnInfoWindowClickListener { marker ->
            navigateToLocationDetail(marker)
        }

        enableMyLocation()
        loadLocationMarkersFromFirestore()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        filterMarkers(query, null) // Pass null for category when searching
        binding.mapSearchView.clearFocus()
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        filterMarkers(newText, null) // Pass null for category when searching
        return true
    }

    // *** MODIFIED: This function now handles both search and category filtering ***
    private fun filterMarkers(textQuery: String?, categoryQuery: String?) {
        val normalizedTextQuery = textQuery?.trim().orEmpty()

        for (marker in allMarkers) {
            val locationId = marker.tag as String
            val location = allLocations.find { it.id == locationId }

            val matchesText = location?.name.orEmpty().contains(normalizedTextQuery, ignoreCase = true)
            val matchesCategory = categoryQuery == null || location?.category == categoryQuery

            marker.isVisible = matchesText && matchesCategory
        }
    }

    private fun navigateToLocationDetail(marker: Marker) {
        val locationId = marker.tag as? String ?: return
        val action = MapFragmentDirections.actionMapFragmentToLocationDetailFragment(locationId)
        findNavController().navigate(action)
    }

    private fun loadLocationMarkersFromFirestore() {
        firestore.collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener

                googleMap.clear()
                allMarkers.clear()
                allLocations.clear()

                for (document in documents) {
                    val spot = document.toObject(LocationSpot::class.java).copy(id = document.id)
                    allLocations.add(spot) // Store the full location data

                    val geoPoint = spot.map_point
                    val name = spot.name

                    if (geoPoint != null && name != null) {
                        val locationLatLng = LatLng(geoPoint.latitude, geoPoint.longitude)
                        val marker = googleMap.addMarker(
                            MarkerOptions().position(locationLatLng).title(name)
                        )
                        marker?.tag = spot.id
                        if (marker != null) {
                            allMarkers.add(marker)
                        }
                    }
                }
                // *** NEW: Once locations are loaded, set up the category chips ***
                setupCategoryChips()
            }
            .addOnFailureListener { exception ->
                Log.e("MapFragment", "Error getting locations from Firestore.", exception)
            }
    }

    // *** NEW FUNCTION: Dynamically creates and configures the category filter chips ***
    private fun setupCategoryChips() {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews() // Clear any old chips

        // Get a distinct list of non-null categories
        val categories = allLocations.mapNotNull { it.category }.distinct()

        // Create a chip for "All" to reset the filter
        val allChip = createChip("All")
        allChip.isCloseIconVisible = false // The "All" chip cannot be closed
        chipGroup.addView(allChip)

        // Create a chip for each unique category
        for (category in categories) {
            chipGroup.addView(createChip(category))
        }

        // Set the initial selection to "All"
        allChip.isChecked = true

        // Set a listener for when a chip is checked
        chipGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) {
                // If nothing is checked (e.g., user deselects a chip), default to "All"
                allChip.isChecked = true
                return@setOnCheckedChangeListener
            }

            val selectedChip = group.findViewById<Chip>(checkedId)
            val selectedCategory = if (selectedChip.text.toString() == "All") null else selectedChip.text.toString()

            // Filter the markers based on the selected category
            filterMarkers(binding.mapSearchView.query.toString(), selectedCategory)
        }
    }

    // *** NEW FUNCTION: Helper to create a single Chip widget ***
    private fun createChip(text: String): Chip {
        // MODIFIED: Create the Chip directly using the style from styles.xml
        val chip = Chip(context, null, R.style.Widget_App_Chip_Filter)
        chip.text = text
        return chip
    }


    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        googleMap.isMyLocationEnabled = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLocation = LatLng(it.latitude, it.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
            } ?: run {
                val durhamOntario = LatLng(43.9164, -78.8532)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(durhamOntario, 11f))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Log.d("MapFragment", "Location permission denied by user.")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
