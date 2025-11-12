package com.example.mobileproject.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
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

    private lateinit var googleMap: GoogleMap
    private val firestore = FirebaseFirestore.getInstance()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val allMarkers = mutableListOf<Marker>()
    private val allLocations = mutableListOf<LocationSpot>()

    // *** NEW: Define a list of colors to cycle through for the chips. ***
    private val chipColors by lazy {
        listOf(
            R.color.chip_color_1, R.color.chip_color_2, R.color.chip_color_3,
            R.color.chip_color_4, R.color.chip_color_5, R.color.chip_color_6
        )
    }

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

        val topPadding = (160 * resources.displayMetrics.density).toInt()
        val bottomPadding = (100 * resources.displayMetrics.density).toInt()
        googleMap.setPadding(0, topPadding, 0, bottomPadding)

        googleMap.setOnInfoWindowClickListener { marker ->
            navigateToLocationDetail(marker)
        }

        enableMyLocation()
        loadLocationMarkersFromFirestore()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        // *** MODIFIED: Pass the currently selected category when submitting text search. ***
        val selectedCategory = getSelectedCategory()
        filterMarkers(query, selectedCategory)
        binding.mapSearchView.clearFocus()
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        // *** MODIFIED: Pass the currently selected category when changing text search. ***
        val selectedCategory = getSelectedCategory()
        filterMarkers(newText, selectedCategory)
        return true
    }

    /**
     * *** NEW: Helper function to get the text of the currently selected chip. ***
     * Returns null if "All" is selected.
     */
    private fun getSelectedCategory(): String? {
        val checkedChipId = binding.categoryChipGroup.checkedChipId
        if (checkedChipId == View.NO_ID) return null

        val selectedChip = binding.categoryChipGroup.findViewById<Chip>(checkedChipId)
        val selectedCategory = selectedChip.text.toString()
        return if (selectedCategory == "All") null else selectedCategory
    }

    private fun filterMarkers(textQuery: String?, categoryQuery: String?) {
        val normalizedTextQuery = textQuery?.trim().orEmpty()

        for (marker in allMarkers) {
            val locationId = marker.tag as String
            val location = allLocations.find { it.id == locationId }

            val matchesText = location?.name.orEmpty().contains(normalizedTextQuery, ignoreCase = true)
            // *** MODIFIED: The category filter logic is now fully active. ***
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

                allMarkers.forEach { it.remove() }
                allMarkers.clear()
                allLocations.clear()

                for (document in documents) {
                    val spot = document.toObject(LocationSpot::class.java).copy(id = document.id)
                    allLocations.add(spot)

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
                setupCategoryChips()
            }
            .addOnFailureListener { exception ->
                Log.e("MapFragment", "Error getting locations from Firestore.", exception)
            }
    }

    private fun setupCategoryChips() {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews()

        val categories = allLocations.mapNotNull { it.category }.distinct()

        // *** MODIFIED: Assign a default grey color to the "All" chip. ***
        val allChip = createChip("All", android.R.color.darker_gray)
        allChip.isCloseIconVisible = false
        chipGroup.addView(allChip)

        // *** MODIFIED: Loop through categories and assign a unique color from our list. ***
        categories.forEachIndexed { index, category ->
            val colorResId = chipColors[index % chipColors.size] // Cycle through colors
            chipGroup.addView(createChip(category, colorResId))
        }

        allChip.isChecked = true

        chipGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) {
                allChip.isChecked = true
                return@setOnCheckedChangeListener
            }

            // *** MODIFIED: Get the selected category and trigger the filter. ***
            val selectedCategory = getSelectedCategory()
            filterMarkers(binding.mapSearchView.query.toString(), selectedCategory)
        }
    }

    /**
     * *** MODIFIED: This helper function now accepts a color resource ID. ***
     * It creates and styles a single Chip, setting its background and text colors to be state-aware.
     */
    private fun createChip(text: String, colorResId: Int): Chip {
        val chip = Chip(context)
        chip.text = text
        // Set the background color of the chip to change when selected.
        chip.setChipBackgroundColorResource(colorResId)
        // Set the text and icon color of the chip to change when selected.
        chip.setTextColor(resources.getColorStateList(R.color.chip_color_state_list, null))
        chip.setChipIconTintResource(R.color.chip_color_state_list) // If you add icons
        chip.isCheckable = true
        chip.isClickable = true
        return chip
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

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
