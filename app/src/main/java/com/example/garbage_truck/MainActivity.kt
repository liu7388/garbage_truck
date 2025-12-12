package com.example.garbage_truck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_CODE_NOTIFICATIONS = 100
        private const val REQ_CODE_LOCATION = 101
        private const val TAG = "NOTI_TEST"
    }

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "🚀 MainActivity onCreate() 執行了")

        setupTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToolbarAndNavigation()

        fetchFcmToken()

        requestPermissionsInOrder()

        // ✅ 檢查是否是從通知點擊進來，如果是就處理
        handleArrivalIntentIfNeeded(intent)
    }

    private fun setupTheme() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        val rememberDarkMode = prefs.getBoolean("remember_dark_mode", true)

        AppCompatDelegate.setDefaultNightMode(
            if (rememberDarkMode || darkModeOn) {
                if (darkModeOn) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun setupToolbarAndNavigation() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        navController = findNavController(R.id.nav_host_fragment)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fab = findViewById<FloatingActionButton>(R.id.locationFab)

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.mapFragment, R.id.settingsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNavigationView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.mapFragment) fab.show() else fab.hide()
        }

        fab.setOnClickListener {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            if (currentFragment is MapFragment) {
                currentFragment.moveToMyLocation()
            }
        }
    }

    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            Log.d("FCM_TOKEN", "🔥 Token: ${task.result}")
        }
    }

    private fun requestPermissionsInOrder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_CODE_NOTIFICATIONS)
                return
            }
        }
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_CODE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQ_CODE_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)){
                        AlertDialog.Builder(this)
                            .setTitle("權限提示")
                            .setMessage("您已關閉通知權限，將無法收到垃圾車抵達提醒。您可以隨時在系統設定中重新開啟。")
                            .setPositiveButton("我知道了", null)
                            .setNeutralButton("前往設定") { _, _ -> openNotificationSettings() }
                            .show()
                    }
                }
                requestLocationPermission()
            }
            REQ_CODE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                     Log.w(TAG, "使用者拒絕了定位權限。")
                }
            }
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "無法開啟通知設定頁", e)
        }
    }

    // ✅ 修改後的方法，直接顯示 Dialog
    private fun handleArrivalIntentIfNeeded(intent: Intent?) {
        val shouldShowDialog = intent?.getBooleanExtra(ArrivalAlarmReceiver.EXTRA_SHOW_DIALOG, false) == true

        if (shouldShowDialog) {
            Log.d(TAG, "接收到指令，準備顯示 ArrivalAnimationDialog")
            // 確保沒有舊的 Dialog 正在顯示
            supportFragmentManager.findFragmentByTag(ArrivalAnimationDialog.TAG)?.let {
                (it as? ArrivalAnimationDialog)?.dismiss()
            }
            // 顯示新的 Dialog
            ArrivalAnimationDialog().show(supportFragmentManager, ArrivalAnimationDialog.TAG)
            // ✅ 用完就清除便條，避免 Activity 重建時重複觸發
            intent?.removeExtra(ArrivalAlarmReceiver.EXTRA_SHOW_DIALOG)
        }
    }

    // ✅ 如果 App 已經在前景，從通知點擊會觸發這個
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleArrivalIntentIfNeeded(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}