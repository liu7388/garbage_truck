package com.example.garbage_truck

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(private val items: List<SettingItem>) :
    RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.textItem)
        val switch: SwitchCompat = view.findViewById(R.id.switchItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        holder.text.text = item.title

        val checked = when (item.title) {
            "深色模式" -> prefs.getBoolean("dark_mode", item.enabled)
            "是否記憶深色模式" -> prefs.getBoolean("remember_dark_mode", item.enabled)
            else -> item.enabled
        }

        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = checked

        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            when (item.title) {
                "深色模式" -> {
                    prefs.edit().putBoolean("dark_mode", isChecked).apply()

                    val remember = prefs.getBoolean("remember_dark_mode", false)
                    if (remember) {
                        if (isChecked) {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        } else {
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        }
                    }
                }
                "是否記憶深色模式" -> {
                    prefs.edit().putBoolean("remember_dark_mode", isChecked).apply()
                }
            }
        }
    }

    override fun getItemCount() = items.size
}

data class SettingItem(var title: String, var enabled: Boolean)