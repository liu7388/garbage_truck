package com.example.garbage_truck

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garbage_truck.data.FavoriteAddress
import com.example.garbage_truck.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
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
            if (isAdded && nearestArrive.isNotBlank()) {
                // App 在前景時，持續檢查是否進入「前 5 分鐘」→ 播動畫
                checkIfTruckIsArriving(nearestArrive)
                handler.postDelayed(this, 30_000) // 每 30 秒檢查一次
            }
        }
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var favoriteAddressAdapter: FavoriteAddressAdapter
    private val favoriteAddresses = mutableListOf<FavoriteAddress>()

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

    // 用於處理從「精準鬧鐘」權限設定頁面返回後的結果
    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 從設定頁面回來後，再次檢查權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                // 如果使用者授予了權限，就重新安排一次鬧鐘
                Toast.makeText(requireContext(), "已取得精準鬧鐘權限，將設定提醒", Toast.LENGTH_SHORT).show()
                scheduleArrivalAlarm(nearestArrive)
            } else {
                Toast.makeText(requireContext(), "未授予精準鬧鐘權限，提醒可能延遲", Toast.LENGTH_SHORT).show()
            }
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

        _binding?.mapView?.onCreate(savedInstanceState)
        _binding?.mapView?.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.tvCity.text = "定位中…"
        binding.tvTemperature.text = "30°C"
        binding.tvNearestSub.text = "..."

        setupRecyclerView()
        fetchFavoriteAddresses()

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
                    val endMillis =
                        parseTimeToMillis(nearestLeave).let { if (it > startMillis) it else startMillis + 15 * 60 * 1000 }

                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(
                            CalendarContract.Events.TITLE,
                            "垃圾車抵達提醒: $nearestPointName"
                        )
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
                val uri =
                    Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=w")
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

    private fun setupRecyclerView() {
        favoriteAddressAdapter = FavoriteAddressAdapter(
            favoriteAddresses,
            onItemClicked = { address ->
                val location = address.location
                if (location != null) {
                    val action = HomeFragmentDirections.actionHomeFragmentToMapFragment(
                        location.latitude.toFloat(),
                        location.longitude.toFloat()
                    )
                    findNavController().navigate(action)
                } else {
                    Log.w("HomeFragment", "Address location is null: ${address.name}")
                }
            },
            onDeleteClicked = { address ->
                deleteFavorite(address)
            }
        )
        binding.recyclerViewFavoriteAddresses.adapter = favoriteAddressAdapter
        binding.recyclerViewFavoriteAddresses.layoutManager = LinearLayoutManager(context)
    }

    private fun deleteFavorite(address: FavoriteAddress) {
        if (address.id.isEmpty()) {
            Log.w("HomeFragment", "Cannot delete favorite with empty ID")
            Toast.makeText(context, "無法移除此項目", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("favorites").document(address.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "已從最愛移除", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "移除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchFavoriteAddresses() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("favorites")
                .whereEqualTo("userId", currentUser.uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("HomeFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    val addresses = mutableListOf<FavoriteAddress>()
                    if (snapshots != null) {
                        for (doc in snapshots) {
                            val address = doc.toObject<FavoriteAddress>().copy(id = doc.id)
                            addresses.add(address)
                        }
                    }
                    favoriteAddressAdapter.updateData(addresses)
                }
        }
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

                    // 🔔 安排「抵達前五分鐘」的鬧鐘（即使 App 關掉也會收到通知）
                    scheduleArrivalAlarm(nearestArrive)

                    // App 在前景時，同時用 Handler 做 30 秒輪詢，只負責顯示動畫
                    handler.removeCallbacks(truckCheckRunnable)
                    handler.post(truckCheckRunnable)
                }
            }
        })
    }

    /**
     * 使用 AlarmManager 安排一次性鬧鐘，在抵達前 5 分鐘發出本機通知。
     * 真正送通知的是 ArrivalAlarmReceiver。
     */
    private fun scheduleArrivalAlarm(arriveTime: String) {
        if (!isAdded || arriveTime.length != 4) return

        val arrivalMillis = parseTimeToMillis(arriveTime)
        if (arrivalMillis == 0L) return

        val fiveMinutesInMillis = 5 * 60 * 1000
        val triggerAtMillis = arrivalMillis - fiveMinutesInMillis

        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) return  // 避免時間已過還排鬧鐘

        val context = requireContext().applicationContext
        val intent = Intent(context, ArrivalAlarmReceiver::class.java).apply {
            putExtra("pointName", nearestPointName)
        }

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            pendingFlags
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Toast.makeText(requireContext(), "已設定抵達前 5 分鐘提醒", Toast.LENGTH_SHORT).show()
                } else {
                    // 沒有權限，引導使用者去設定
                    showExactAlarmPermissionDialog()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Toast.makeText(requireContext(), "已設定抵達前 5 分鐘提醒", Toast.LENGTH_SHORT).show()
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Toast.makeText(requireContext(), "已設定抵達前 5 分鐘提醒", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "無法設定提醒，請檢查權限", Toast.LENGTH_SHORT).show()
            // 萬一還是被擋，退回一般 set，避免整個 App crash
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    // 顯示對話框，向使用者解釋為何需要權限，並引導至設定頁
    private fun showExactAlarmPermissionDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("需要「鬧鐘與提醒」權限")
            .setMessage("為了準時在垃圾車抵達前 5 分鐘提醒您，應用程式需要「鬧鐘與提醒」權限。\n\n若未授予，提醒通知可能會延遲送達。")
            .setPositiveButton("前往設定") { _, _ ->
                requestExactAlarmPermission()
            }
            .setNegativeButton("暫不設定", null)
            .show()
    }

    // 建立 Intent，開啟「鬧鐘與提醒」的設定頁面
    private fun requestExactAlarmPermission() {
        if (!isAdded) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                // 將使用者直接導向自己 App 的設定頁
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            exactAlarmPermissionLauncher.launch(intent)
        }
    }

    private fun checkIfTruckIsArriving(time: String) {
        if (time.isBlank()) return
        val arrivalMillis = parseTimeToMillis(time)
        if (arrivalMillis == 0L) return

        val currentMillis = System.currentTimeMillis()
        val fiveMinutesInMillis = 5 * 60 * 1000

        if (arrivalMillis > currentMillis && arrivalMillis - currentMillis <= fiveMinutesInMillis) {
            if (arrivalAnimationDialog == null) {
                arrivalAnimationDialog = ArrivalAnimationDialog()
            }
            if (arrivalAnimationDialog?.isAdded == false && !childFragmentManager.isStateSaved) {
                arrivalAnimationDialog?.show(childFragmentManager, ArrivalAnimationDialog.TAG)
            }
        }
    }

    // 給 MainActivity 從通知點進來時呼叫，強迫顯示動畫
    fun showArrivalAnimationFromNotification() {
        if (!isAdded) return
        if (arrivalAnimationDialog == null) {
            arrivalAnimationDialog = ArrivalAnimationDialog()
        }
        if (arrivalAnimationDialog?.isAdded == false && !childFragmentManager.isStateSaved) {
            arrivalAnimationDialog?.show(childFragmentManager, ArrivalAnimationDialog.TAG)
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

    override fun onStart() {
        super.onStart()
        _binding?.mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        _binding?.mapView?.onStop()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
        // 回到畫面時，再開始 30 秒輪詢（只為了顯示動畫）
        handler.post(truckCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        _binding?.mapView?.onPause()
        handler.removeCallbacks(truckCheckRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        arrivalAnimationDialog?.dismissAllowingStateLoss()
        arrivalAnimationDialog = null
        _binding?.mapView?.onDestroy()
        if (this::googleMap.isInitialized) {
            googleMap.clear()
        }
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_binding != null) {
            _binding!!.mapView.onSaveInstanceState(outState)
        }
    }
}
