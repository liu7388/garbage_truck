package com.example.garbage_truck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
                    _binding?.cardNearest?.tvNearestSub?.text = street
                }
            } else {
                _binding?.cardNearest?.tvNearestSub?.text = "無法取得地址"
            }
        } catch (e: IOException) {
            e.printStackTrace()
            _binding?.cardNearest?.tvNearestSub?.text = "無法取得地址"
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

        binding.cardNearest.mapView.onCreate(savedInstanceState)
        binding.cardNearest.mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.tvCity.text = "定位中…"
        binding.tvTemperature.text = "30°C"
        binding.cardNearest.tvNearestSub.text = "..."

        binding.cardNearest.btnRemind.setOnClickListener {
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

        binding.cardNearest.btnMap.setOnClickListener {
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
                            fetchWeather(lat, lng)
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                },
                requireActivity().mainLooper
            )
        }
    }

    private fun fetchWeather(lat: Double, lng: Double) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.TAIWAN)
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val address = addresses?.firstOrNull()

                // 印出 geocoder 回傳內容
                Log.d("WeatherAPI", "adminArea=${address?.adminArea}, locality=${address?.locality}, full=${address}")

                var cityName = address?.adminArea ?: address?.locality ?: ""

                cityName = cityName.replace("台", "臺")

                withContext(Dispatchers.Main) {
                    binding.tvCity.text = cityName
                }

                // 讀取 API 金鑰
                val appInfo = requireContext().packageManager
                    .getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
                val weatherApiKey = appInfo.metaData.getString("com.example.garbage_truck.WEATHER_API_KEY")

                Log.d("WeatherAPI", "weatherApiKey=$weatherApiKey")

                // 組 URL
                val url = "https://opendata.cwa.gov.tw/api/v1/rest/datastore/F-C0032-001?Authorization=$weatherApiKey"
                Log.d("WeatherAPI", "Request URL=$url")

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch

                Log.d("WeatherAPI", "Response first 300 chars=${body.take(300)}")

                val json = JSONObject(body)
                val records = json.getJSONObject("records")
                val locations = records.getJSONArray("location")

                var found = false
                for (i in 0 until locations.length()) {
                    val locationObj = locations.getJSONObject(i)
                    val locationName = locationObj.getString("locationName")
                    if (cityName.contains(locationName)) {
                        val weatherElements = locationObj.getJSONArray("weatherElement")

                        val wxArray = weatherElements.getJSONObject(0).getJSONArray("time")
                        val minTArray = weatherElements.getJSONObject(2).getJSONArray("time")

                        val currentWx = wxArray.getJSONObject(0)
                            .getJSONObject("parameter").getString("parameterName")
                        val currentMinT = minTArray.getJSONObject(0)
                            .getJSONObject("parameter").getString("parameterName")

                        withContext(Dispatchers.Main) {
                            binding.tvCity.text = locationName
                            binding.tvTemperature.text = "${currentMinT}°C"
                            binding.tvWeather.text = currentWx

                            val wxText = currentWx.trim() // 移除多餘空白
                            val iconRes = when {
                                wxText.contains("雷") -> R.drawable.ic_weather_thunder
                                wxText.contains("雨") -> R.drawable.ic_weather_rainy
                                wxText.contains("陰") -> R.drawable.ic_weather_cloudy
                                wxText.contains("多雲") -> R.drawable.ic_day_cloudy
                                wxText.contains("晴") -> R.drawable.ic_weather_sunny
                                else -> R.drawable.ic_weather // 預設
                            }
                            binding.ivWeather.setImageResource(iconRes)
                        }
                        found = true
                        break
                    }
                }

                if (!found) {
                    Log.w("WeatherAPI", "找不到對應城市：$cityName")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "找不到對應城市的天氣資料", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherAPI", "Error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "取得天氣資料失敗", Toast.LENGTH_SHORT).show()
                }
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
                    _binding?.cardNearest?.tvArriveTime?.text = "抵達時間：${formatTime(arrive)}"
                    _binding?.cardNearest?.tvLeaveTime?.text = "離開時間：${formatTime(leave)}"
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
        if (!isAdded) return
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
        _binding?.cardNearest?.mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding?.cardNearest?.mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.cardNearest?.mapView?.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.cardNearest?.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.cardNearest?.mapView?.onSaveInstanceState(outState)
    }
}