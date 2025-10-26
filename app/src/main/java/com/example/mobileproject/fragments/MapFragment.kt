package com.example.mobileproject.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.mobileproject.R
import com.example.mobileproject.models.Adventure
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = true
        // Set bottom padding to prevent the Google logo from overlapping the bottom navigation bar
        val bottomPadding = (60 * resources.displayMetrics.density).toInt()
        googleMap.setPadding(0, 0, 0, bottomPadding)

        enableLocation()
        loadAdventureMarkers()
    }

    private fun loadAdventureMarkers() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // FIX 1: Query the correct "locations" collection
                val snapshot = db.collection("locations").get().await()
                val adventures = snapshot.documents.mapNotNull { it.toObject(Adventure::class.java) }

                for (adventure in adventures) {
                    // FIX 2: Access latitude and longitude through the 'map_point' object
                    val lat = adventure.map_point?.latitude
                    val lng = adventure.map_point?.longitude

                    if (lat != null && lng != null) {
                        val adventureLocation = LatLng(lat, lng)
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(adventureLocation)
                                // FIX 3: Use 'adventure.name' for the title, not 'adventure.title'
                                .title(adventure.name)
                        )
                    }
                }
            } catch (e: Exception) {
                // It's good practice to log the exception to see what went wrong
                Log.e("MapFragment", "Error loading adventure markers", e)
            }
        }
    }

    private fun enableLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission from the user
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        googleMap.isMyLocationEnabled = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val myLocation = LatLng(it.latitude, it.longitude)
                // Move the camera to the user's current location
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12f))
            }
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, enable the location features
                enableLocation()
            }
        }
    }
}

