package com.example.garbage_truck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.garbage_truck.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var tvArriveTime: TextView
    private lateinit var tvLeaveTime: TextView

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var nearestPointLatLng: LatLng? = null
    private var nearestPointName: String = ""
    private var nearestArrive: String = ""
    private var nearestLeave: String = ""

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(requireContext(), "未授予定位權限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAddress(lat: Double, lng: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.thoroughfare ?: address.getAddressLine(0) ?: "未知道路"
                requireActivity().runOnUiThread {
                    _binding?.tvNearestSub?.text = street
                }
            } else {
                _binding?.tvNearestSub?.text = "無法取得地址"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            _binding?.tvNearestSub?.text = "無法取得地址"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.tvCity.text = "定位中…"
        binding.tvTemperature.text = "30°C"
        binding.tvNearestSub.text = "..."

        binding.btnRemind.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("新增提醒")
                .setMessage("要將 ${nearestPointName} 的垃圾車抵達時間加到行事曆嗎？")
                .setPositiveButton("確定") { _, _ ->
                    val startMillis = parseTimeToMillis(nearestArrive)
                    val endMillis = startMillis + 15 * 60 * 1000
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, "垃圾車抵達提醒")
                        putExtra(CalendarContract.Events.EVENT_LOCATION, nearestPointName)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnMap.setOnClickListener {
            nearestPointLatLng?.let { latLng ->
                val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&walk=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } ?: run {
                Toast.makeText(requireContext(), "尚未取得最近清運點", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun parseTimeToMillis(time: String): Long {
        val now = Calendar.getInstance()
        if (time.length == 4) {
            val hour = time.substring(0, 2).toInt()
            val min = time.substring(2, 4).toInt()
            now.set(Calendar.HOUR_OF_DAY, hour)
            now.set(Calendar.MINUTE, min)
            now.set(Calendar.SECOND, 0)
        }
        return now.timeInMillis
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true

            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 2000
            ).setMaxUpdates(1).build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        val location = result.lastLocation
                        if (location != null) {
                            val lat = location.latitude
                            val lng = location.longitude
                            val currentLatLng = LatLng(lat, lng)
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                            googleMap.clear()
                            updateCityName(lat, lng)
                            fetchNearestGarbagePoint(lat, lng)
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                },
                requireActivity().mainLooper
            )
        }
    }

    private fun fetchNearestGarbagePoint(myLat: Double, myLng: Double) {
        val client = OkHttpClient()
        val url =
            "https://data.taipei/api/v1/dataset/a6e90031-7ec4-4089-afb5-361a4efe7202?scope=resourceAquire&limit=1000"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val results = json.getJSONObject("result").getJSONArray("results")

                var minDistance = Float.MAX_VALUE
                var nearestLat = 0.0
                var nearestLng = 0.0
                var nearestName = ""
                var arrive = ""
                var leave = ""

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val lat = item.getString("緯度").toDouble()
                    val lng = item.getString("經度").toDouble()
                    val title = item.getString("地點")
                    val arriveTime = item.optString("抵達時間", "")
                    val leaveTime = item.optString("離開時間", "")
                    val distance = FloatArray(1)
                    android.location.Location.distanceBetween(myLat, myLng, lat, lng, distance)
                    if (distance[0] < minDistance) {
                        minDistance = distance[0]
                        nearestLat = lat
                        nearestLng = lng
                        nearestName = title
                        arrive = arriveTime
                        leave = leaveTime
                    }
                }

                requireActivity().runOnUiThread {
                    nearestPointLatLng = LatLng(nearestLat, nearestLng)
                    nearestPointName = nearestName
                    nearestArrive = arrive
                    nearestLeave = leave
                    googleMap.addMarker(
                        MarkerOptions().position(nearestPointLatLng!!)
                            .title("最近清運點：$nearestName")
                    )
                    updateAddress(nearestLat, nearestLng)
                    _binding?.tvArriveTime?.text = "抵達時間：${formatTime(arrive)}"
                    _binding?.tvLeaveTime?.text = "離開時間：${formatTime(leave)}"
                }
            }
        })
    }

    private fun formatTime(time: String): String {
        return if (time.length == 4) {
            val hour = time.substring(0, 2)
            val min = time.substring(2, 4)
            "$hour:$min"
        } else time
    }

    private fun updateCityName(lat: Double, lng: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.adminArea ?: address.locality ?: "未知地點"
                _binding?.tvCity?.text = city
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "無法取得城市名稱", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding?.mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.mapView?.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }
}