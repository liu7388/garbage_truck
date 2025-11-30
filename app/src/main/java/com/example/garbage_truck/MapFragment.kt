package com.example.garbage_truck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMyLocationButtonEnabled = false

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            moveToMyLocation()
        } else {
            Toast.makeText(requireContext(), "尚未取得定位權限", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveToMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val fused = LocationServices.getFusedLocationProviderClient(requireActivity())
            fused.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val myLatLng = LatLng(it.latitude, it.longitude)
                    googleMap?.clear()
                    googleMap?.addMarker(MarkerOptions().position(myLatLng).title("目前位置"))
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 17f))
                }
            }
        } else {
            Toast.makeText(requireContext(), "尚未取得定位權限", Toast.LENGTH_SHORT).show()
        }
    }
}