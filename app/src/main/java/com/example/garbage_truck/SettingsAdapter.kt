package com.example.garbage_truck

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(private val items: List<SettingItem>) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        holder.textItem.text = item.title

        val checked = when (item.title) {
            "深色模式" -> prefs.getBoolean("dark_mode", item.isChecked)
            "是否記憶深色模式" -> prefs.getBoolean("remember_dark_mode", item.isChecked)
            else -> item.isChecked
        }

        holder.switchItem.setOnCheckedChangeListener(null)
        holder.switchItem.isChecked = checked

        holder.switchItem.setOnCheckedChangeListener { _, isChecked ->
            when (item.title) {
                "深色模式" -> {
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()

                    AppCompatDelegate.setDefaultNightMode(
                        if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )

                    val activity = (context as? Activity)
                    activity?.recreate()
                }

                "是否記憶深色模式" -> {
                    prefs.edit().putBoolean("remember_dark_mode", isChecked).apply()
                }
            }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textItem: TextView = view.findViewById(R.id.textItem)
        val switchItem: SwitchCompat = view.findViewById(R.id.switchItem)
    }
}