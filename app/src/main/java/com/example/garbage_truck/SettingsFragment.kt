package com.example.garbage_truck

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.garbage_truck.R

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 範例資料
        val items = listOf(
            SettingItem(R.drawable.ic_feature, "功能1", true),
            SettingItem(R.drawable.ic_feature, "功能2", false)
        )

        recyclerView.adapter = SettingsAdapter(items)
    }
}