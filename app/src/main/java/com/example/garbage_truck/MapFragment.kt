package com.example.garbage_truck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.example.garbage_truck.databinding.FragmentMapBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private val client = OkHttpClient()
    private val markerMap = mutableMapOf<String, Marker>()
    private var selectedMarker: Marker? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val args: MapFragmentArgs by navArgs()
    private var targetLocationToShow: LatLng? = null // For showing info window automatically

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.map.onCreate(savedInstanceState)
        binding.map.getMapAsync(this)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnNavigate.setOnClickListener {
            selectedMarker?.let { marker ->
                val latLng = marker.position
                val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=w")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
            }
        }

        binding.btnAddFavorite.setOnClickListener {
            addSelectedMarkerToFavorites()
        }
    }

    private fun addSelectedMarkerToFavorites() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "請先登入", Toast.LENGTH_SHORT).show()
            return
        }

        selectedMarker?.let { marker ->
            val info = marker.tag as? GarbageCarInfo
            val favoriteName = info?.title ?: marker.title

            if (favoriteName == null) {
                Toast.makeText(requireContext(), "無法儲存此地點", Toast.LENGTH_SHORT).show()
                return
            }

            val newLocation = GeoPoint(marker.position.latitude, marker.position.longitude)

            db.collection("favorites")
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("location", newLocation)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        val favorite = hashMapOf(
                            "userId" to user.uid,
                            "name" to favoriteName,
                            "location" to newLocation
                        )

                        db.collection("favorites")
                            .add(favorite)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "已新增至最愛清運點", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "新增失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(requireContext(), "此清運點已在最愛清單中", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "檢查重複時發生錯誤: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
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
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }

        if (args.latitude != 0f && args.longitude != 0f) {
            targetLocationToShow = LatLng(args.latitude.toDouble(), args.longitude.toDouble())
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocationToShow!!, 17f))
        } else {
            moveToMyLocation()
        }

        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            loadGarbageCarDataAroundUser(center)
        }

        map.setOnMarkerClickListener { marker ->
            showMarkerInfo(marker)
            true
        }
        map.setOnMapClickListener {
            binding.infoCard.visibility = View.GONE
            selectedMarker = null
        }
    }

    private fun showMarkerInfo(marker: Marker) {
        selectedMarker = marker
        val info = marker.tag as? GarbageCarInfo
        if (info != null) {
            binding.tvTitle.text = info.title
            binding.tvCarNo.text = "車號：${info.carNo}"
            binding.tvCarTimes.text = "車次：${info.carTimes}"
            binding.tvArriveLeave.text = "抵達 ${formatTime(info.arriveTime)} / 離開 ${formatTime(info.leaveTime)}"
        } else {
            binding.tvTitle.text = marker.title
            binding.tvCarNo.text = ""
            binding.tvCarTimes.text = ""
            binding.tvArriveLeave.text = ""
        }
        binding.infoCard.visibility = View.VISIBLE
    }

    private fun loadGarbageCarDataAroundUser(center: LatLng) {
        binding.progressBar.visibility = View.VISIBLE

        val url = "https://data.taipei/api/v1/dataset/a6e90031-7ec4-4089-afb5-361a4efe7202?scope=resourceAquire&limit=1000"

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                activity?.runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val results = json.getJSONObject("result").getJSONArray("results")

                activity?.runOnUiThread {
                    val newMarkers = mutableMapOf<String, LatLng>()

                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val lat = item.getString("緯度").toDouble()
                        val lng = item.getString("經度").toDouble()
                        val location = LatLng(lat, lng)

                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            center.latitude, center.longitude,
                            lat, lng, distance
                        )

                        if (distance[0] < 2000) {
                            val key = "$lat,$lng"
                            newMarkers[key] = location

                            if (!markerMap.containsKey(key)) {
                                val marker = googleMap?.addMarker(
                                    MarkerOptions()
                                        .position(location)
                                        .title(item.getString("地點"))
                                )
                                if (marker != null) {
                                    marker.tag = GarbageCarInfo(
                                        item.getString("地點"),
                                        item.getString("車號"),
                                        item.getString("車次"),
                                        item.getString("抵達時間"),
                                        item.getString("離開時間")
                                    )
                                    markerMap[key] = marker
                                }
                            }
                        }
                    }

                    val iterator = markerMap.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (!newMarkers.containsKey(entry.key)) {
                            entry.value.remove()
                            iterator.remove()
                        }
                    }

                    targetLocationToShow?.let { target ->
                        var closestMarker: Marker? = null
                        var minDistance = Float.MAX_VALUE

                        for (marker in markerMap.values) {
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                target.latitude, target.longitude,
                                marker.position.latitude, marker.position.longitude,
                                distance
                            )
                            if (distance[0] < minDistance) {
                                minDistance = distance[0]
                                closestMarker = marker
                            }
                        }

                        if (minDistance < 10.0f) { // 10-meter tolerance
                            closestMarker?.let { showMarkerInfo(it) }
                        }

                        targetLocationToShow = null // Reset regardless of whether a marker was found
                    }

                    binding.progressBar.visibility = View.GONE
                }
            }
        })
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
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f))
                }
            }
        } else {
            Toast.makeText(requireContext(), "尚未取得定位權限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroyView() {
        googleMap?.clear()
        binding.map.onDestroy()
        super.onDestroyView()
        _binding = null
        googleMap = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.map.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }

    private fun formatTime(time: String): String {
        return if (time.length == 4) {
            val hour = time.substring(0, 2)
            val min = time.substring(2, 4)
            "$hour:$min"
        } else {
            time
        }
    }

    data class GarbageCarInfo(
        val title: String,
        val carNo: String,
        val carTimes: String,
        val arriveTime: String,
        val leaveTime: String
    )
}
