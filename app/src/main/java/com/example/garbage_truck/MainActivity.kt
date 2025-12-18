package com.example.garbage_truck

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController


class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        val rememberDarkMode = prefs.getBoolean("remember_dark_mode", true)

        if (rememberDarkMode || darkModeOn) {
            AppCompatDelegate.setDefaultNightMode(
                if (darkModeOn) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        navController = findNavController(R.id.nav_host_fragment)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val fab = findViewById<FloatingActionButton>(R.id.locationFab)

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.mapFragment, R.id.settingsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.mapFragment -> {
                    navController.navigate(R.id.mapFragment)
                    true
                }
                R.id.settingsFragment -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.mapFragment) fab.show() else fab.hide()
            // Update BottomNavigationView selected item
            bottomNavigationView.menu.findItem(destination.id)?.isChecked = true
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            100
        )

        fab.setOnClickListener {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            if (currentFragment is MapFragment) {
                currentFragment.moveToMyLocation()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
