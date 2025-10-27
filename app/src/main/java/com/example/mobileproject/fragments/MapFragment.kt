package com.example.mobileproject.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.mobileproject.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

data class LocationSpot(
    val id: String = "",
    val name: String? = null,
    val map_point: GeoPoint? = null,
    val category: String? = null,
    val description: String? = null
)

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private val firestore = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        val bottomPadding = (60 * resources.displayMetrics.density).toInt()
        googleMap.setPadding(0, 0, 0, bottomPadding)

        // *** THIS IS THE NEW PART ***
        // Set a listener for when the user clicks on a marker's info window.
        googleMap.setOnInfoWindowClickListener { marker ->
            navigateToLocationDetail(marker)
        }

        enableMyLocation()
        loadLocationMarkersFromFirestore()
    }

    /**
     * *** THIS IS THE NEW FUNCTION ***
     * Handles the navigation to the detail screen.
     */
    private fun navigateToLocationDetail(marker: Marker) {
        // Retrieve the document ID we stored in the marker's tag.
        val locationId = marker.tag as? String
        if (locationId == null) {
            Log.e("MapFragment", "Marker tag (locationId) is null, cannot navigate.")
            return
        }

        Log.d("MapFragment", "Navigating to detail screen with location ID: $locationId")

        // Use the Safe Args generated action to create the navigation instruction.
        // This is type-safe and ensures we pass the correct arguments.
        val action = MapFragmentDirections.actionMapFragmentToLocationDetailFragment(locationId)

        // Use the NavController to perform the navigation.
        findNavController().navigate(action)
    }

    private fun loadLocationMarkersFromFirestore() {
        firestore.collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("MapFragment", "No locations found in Firestore.")
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val spot = document.toObject(LocationSpot::class.java).copy(id = document.id)
                    val geoPoint = spot.map_point
                    val name = spot.name

                    if (geoPoint != null && name != null) {
                        val locationLatLng = LatLng(geoPoint.latitude, geoPoint.longitude)

                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(locationLatLng)
                                .title(name)
                        )
                        // IMPORTANT: We store the Firestore Document ID in the marker's tag.
                        marker?.tag = spot.id
                    } else {
                        Log.w("MapFragment", "Skipping a location due to missing data: ${document.id}")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MapFragment", "Error getting locations from Firestore.", exception)
            }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                val durhamOntario = LatLng(43.9164, -78.8532)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(durhamOntario, 11f))
                Log.d("MapFragment", "Location permission denied by user.")
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
