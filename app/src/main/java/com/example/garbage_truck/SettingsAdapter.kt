package com.example.garbage_truck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(private val items: List<SettingItem>) :
    RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon) // 給 item_setting.xml 裡的 ImageView 設 id="icon"
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
        holder.icon.setImageResource(item.iconRes)
        holder.text.text = item.title
        holder.switch.isChecked = item.enabled
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
        }
    }

    override fun getItemCount() = items.size
}

data class SettingItem(val iconRes: Int, val title: String, val enabled: Boolean)