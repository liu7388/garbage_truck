package com.example.garbage_truck

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Context

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 從偏好設定取出已儲存的深色模式狀態
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val darkModeOn = prefs.getBoolean("dark_mode", false)
        val rememberDarkMode = prefs.getBoolean("remember_dark_mode", true)

        // 初始化列表，用現在的狀態填入
        val items = listOf(
            SettingItem("深色模式", darkModeOn),
            SettingItem("是否記憶深色模式", rememberDarkMode)
        )

        recyclerView.adapter = SettingsAdapter(items)
    }
}