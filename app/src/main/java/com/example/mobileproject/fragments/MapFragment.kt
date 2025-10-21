package com.example.mobileproject.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
        val bottomPadding = (60 * resources.displayMetrics.density).toInt()
        googleMap.setPadding(0, 0, 0, bottomPadding)

        enableLocation()
        loadAdventureMarkers()
    }

    private fun loadAdventureMarkers() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snapshot = db.collection("adventures").get().await()
                val adventures = snapshot.documents.mapNotNull { it.toObject(Adventure::class.java) }

                for (adventure in adventures) {
                    if (adventure.latitude != null && adventure.longitude != null) {
                        val adventureLocation = LatLng(adventure.latitude, adventure.longitude)
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(adventureLocation)
                                .title(adventure.title)
                        )
                        // I have removed the line that was causing the issue.
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    private fun enableLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        googleMap.isMyLocationEnabled = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val myLocation = LatLng(it.latitude, it.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 12f))
            }
        }
    }
}
