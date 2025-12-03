package com.example.garbage_truck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var nearestPointLatLng: LatLng? = null
    private var nearestPointName: String = ""
    private var nearestArrive: String = ""
    private var nearestLeave: String = ""

    private var arrivalAnimationDialog: ArrivalAnimationDialog? = null

    private val handler = Handler(Looper.getMainLooper())
    private val truckCheckRunnable = object : Runnable {
        override fun run() {
            if (isAdded) {
                checkIfTruckIsArriving(nearestArrive)
                handler.postDelayed(this, 30000) // Check every 30 seconds
            }
        }
    }

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
        if (!isAdded) return
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val street = address.thoroughfare ?: address.getAddressLine(0) ?: "未知道路"
                activity?.runOnUiThread {
                    _binding?.tvNearestSub?.text = street
                }
            } else {
                activity?.runOnUiThread {
                    _binding?.tvNearestSub?.text = "無法取得地址"
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            activity?.runOnUiThread {
                _binding?.tvNearestSub?.text = "無法取得地址"
            }
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
            if (nearestPointName.isEmpty() || nearestArrive.isEmpty()) {
                Toast.makeText(requireContext(), "尚未取得清運點資訊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("新增提醒")
                .setMessage("要將 ${nearestPointName} 的垃圾車抵達時間加到行事曆嗎？")
                .setPositiveButton("確定") { _, _ ->
                    val startMillis = parseTimeToMillis(nearestArrive)
                    val endMillis = parseTimeToMillis(nearestLeave).let { if (it > startMillis) it else startMillis + 15 * 60 * 1000 }

                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, "垃圾車抵達提醒: $nearestPointName")
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
                val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=w")
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
        if (time.length != 4) return 0L
        val arrivalTime = Calendar.getInstance()
        val hour = time.substring(0, 2).toIntOrNull() ?: return 0L
        val min = time.substring(2, 4).toIntOrNull() ?: return 0L

        arrivalTime.set(Calendar.HOUR_OF_DAY, hour)
        arrivalTime.set(Calendar.MINUTE, min)
        arrivalTime.set(Calendar.SECOND, 0)
        arrivalTime.set(Calendar.MILLISECOND, 0)

        // If the parsed time is earlier than the current time, assume it's for the next day.
        if (arrivalTime.timeInMillis < System.currentTimeMillis()) {
            arrivalTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        return arrivalTime.timeInMillis
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
            return
        }
        googleMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                val currentLatLng = LatLng(lat, lng)

                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                updateCityName(lat, lng)
                fetchNearestGarbagePoint(lat, lng)
            }
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
                activity?.runOnUiThread {
                    Toast.makeText(context, "無法取得垃圾車資料", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "無法取得垃圾車資料", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val body = response.body?.string()
                if (body == null) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "無法取得垃圾車資料", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

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
                    val lat = item.getString("緯度").toDoubleOrNull() ?: continue
                    val lng = item.getString("經度").toDoubleOrNull() ?: continue
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

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    nearestPointLatLng = LatLng(nearestLat, nearestLng)
                    nearestPointName = nearestName
                    nearestArrive = arrive
                    nearestLeave = leave

                    googleMap.addMarker(
                        MarkerOptions().position(nearestPointLatLng!!)
                            .title("最近清運點：$nearestName")
                    )

                    updateAddress(nearestLat, nearestLng)

                    binding.tvArriveTime.text = "抵達時間：${formatTime(arrive)}"
                    binding.tvLeaveTime.text = "離開時間：${formatTime(leave)}"
                    // Start the periodic check after fetching the data
                    handler.post(truckCheckRunnable)
                }
            }
        })
    }

    private fun checkIfTruckIsArriving(time: String) {
        if (time.isBlank()) return
        val arrivalMillis = parseTimeToMillis(time)
        if (arrivalMillis == 0L) return

        val currentMillis = System.currentTimeMillis()
        val fiveMinutesInMillis = 5 * 60 * 1000

        // Check if the arrival time is in the future and within the next 5 minutes
        if (arrivalMillis > currentMillis && arrivalMillis - currentMillis <= fiveMinutesInMillis) {
            if (arrivalAnimationDialog == null) {
                arrivalAnimationDialog = ArrivalAnimationDialog()
            }
            if (arrivalAnimationDialog?.isAdded == false && !childFragmentManager.isStateSaved) {
                arrivalAnimationDialog?.show(childFragmentManager, ArrivalAnimationDialog.TAG)
            }
        }
    }

    private fun formatTime(time: String): String {
        return if (time.length == 4) {
            val hour = time.substring(0, 2)
            val min = time.substring(2, 4)
            "$hour:$min"
        } else time
    }

    private fun updateCityName(lat: Double, lng: Double) {
        if (!isAdded) return
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val city = address.adminArea ?: address.locality ?: "未知地點"
                activity?.runOnUiThread {
                    _binding?.tvCity?.text = city
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            activity?.runOnUiThread {
                Toast.makeText(context, "無法取得城市名稱", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        // Start checking when the fragment is resumed
        handler.post(truckCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        // Stop checking when the fragment is paused
        handler.removeCallbacks(truckCheckRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        arrivalAnimationDialog?.dismissAllowingStateLoss()
        arrivalAnimationDialog = null
        binding.mapView.onDestroy()
        if(this::googleMap.isInitialized) {
            googleMap.clear()
        }
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
