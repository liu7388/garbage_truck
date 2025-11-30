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
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.widget.ProgressBar
import android.content.Intent
import android.net.Uri
import com.google.android.gms.maps.model.Marker
import android.widget.TextView
import android.widget.Button

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private val client = OkHttpClient()

    private lateinit var progressBar: ProgressBar

    // 🔹自己維護的 marker map
    private val markerMap = mutableMapOf<String, Marker>()

    private var selectedMarker: Marker? = null
    private lateinit var infoCard: View
    private lateinit var tvTitle: TextView
    private lateinit var btnNavigate: Button

    private lateinit var tvCarNo: TextView
    private lateinit var tvCarTimes: TextView
    private lateinit var tvArriveLeave: TextView

    private fun formatTime(time: String): String {
        // 只處理長度為 4 的情況
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        infoCard = view.findViewById(R.id.infoCard)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvCarNo = view.findViewById(R.id.tvCarNo)
        tvCarTimes = view.findViewById(R.id.tvCarTimes)
        tvArriveLeave = view.findViewById(R.id.tvArriveLeave)
        btnNavigate = view.findViewById(R.id.btnNavigate)

        progressBar = view.findViewById(R.id.progressBar)

        btnNavigate.setOnClickListener {
            selectedMarker?.let { marker ->
                val latLng = marker.position
                val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=w")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
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

        moveToMyLocation()

        map.setOnCameraIdleListener { 
            val center = map.cameraPosition.target
            loadGarbageCarDataAroundUser(center)
        }

        map.setOnMarkerClickListener { marker ->
            selectedMarker = marker
            val info = marker.tag as? GarbageCarInfo
            if (info != null) {
                tvTitle.text = info.title
                tvCarNo.text = "車號：${info.carNo}"
                tvCarTimes.text = "車次：${info.carTimes}"
                tvArriveLeave.text = "抵達 ${formatTime(info.arriveTime)} / 離開 ${formatTime(info.leaveTime)}"
            } else {
                tvTitle.text = marker.title
                tvCarNo.text = ""
                tvCarTimes.text = ""
                tvArriveLeave.text = ""
            }
            infoCard.visibility = View.VISIBLE
            true
        }
        map.setOnMapClickListener { 
            infoCard.visibility = View.GONE
            selectedMarker = null
        }
    }

    private fun loadGarbageCarDataAroundUser(center: LatLng) {
        progressBar.visibility = View.VISIBLE

        val url = "https://data.taipei/api/v1/dataset/a6e90031-7ec4-4089-afb5-361a4efe7202?scope=resourceAquire&limit=1000"

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
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
                        val carNo = item.getString("車號")
                        val carTimes = item.getString("車次")
                        val arrive = item.getString("抵達時間")
                        val leave = item.getString("離開時間")

                        val lat = item.getString("緯度").toDouble()
                        val lng = item.getString("經度").toDouble()
                        val title = item.getString("地點")
                        val location = LatLng(lat, lng)

                        val distance = FloatArray(1)
                        android.location.Location.distanceBetween(
                            center.latitude, center.longitude,
                            lat, lng, distance
                        )

                        if (distance[0] < 2000) {
                            // 🔹用 lat+lng 當 key
                            val key = "$lat,$lng"
                            newMarkers[key] = location

                            // 如果這個 marker 不存在，才新增
                            if (!markerMap.containsKey(key)) {
                                val marker = googleMap?.addMarker(
                                    MarkerOptions()
                                        .position(location)
                                        .title(title) // 可以還是放 title
                                )
                                if (marker != null) {
                                    marker.tag = GarbageCarInfo(title, carNo, carTimes, arrive, leave)
                                    markerMap[key] = marker
                                }
                            }
                        }
                    }

                    val iterator = markerMap.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (!newMarkers.containsKey(entry.key)) {
                            entry.value.remove() // 從地圖移除
                            iterator.remove()   // 從 map 移除
                        }
                    }

                    progressBar.visibility = View.GONE
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
}
