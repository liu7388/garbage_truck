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

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SWITCH = 0
        private const val TYPE_CLICKABLE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position].type) {
            ItemType.SWITCH -> TYPE_SWITCH
            ItemType.CLICKABLE -> TYPE_CLICKABLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SWITCH -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
                SwitchViewHolder(view)
            }
            TYPE_CLICKABLE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_clickable, parent, false)
                ClickableViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is SwitchViewHolder -> holder.bind(item)
            is ClickableViewHolder -> holder.bind(item, onItemClick)
        }
    }

    override fun getItemCount() = items.size

    class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textItem: TextView = view.findViewById(R.id.textItem)
        private val switchItem: SwitchCompat = view.findViewById(R.id.switchItem)

        fun bind(item: SettingItem) {
            val context = itemView.context
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

            textItem.text = item.title

            val checked = when (item.title) {
                "深色模式" -> prefs.getBoolean("dark_mode", item.isChecked)
                "是否記憶深色模式" -> prefs.getBoolean("remember_dark_mode", item.isChecked)
                else -> item.isChecked
            }

            switchItem.setOnCheckedChangeListener(null)
            switchItem.isChecked = checked

            switchItem.setOnCheckedChangeListener { _, isChecked ->
                when (item.title) {
                    "深色模式" -> {
                        prefs.edit().putBoolean("dark_mode", isChecked).apply()
                        val activity = (context as? Activity)
                        activity?.recreate()
                    }
                    "是否記憶深色模式" -> {
                        prefs.edit().putBoolean("remember_dark_mode", isChecked).apply()
                    }
                }
            }
        }
    }

    class ClickableViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textItem: TextView = view.findViewById(R.id.textItem)

        fun bind(item: SettingItem, onItemClick: (SettingItem) -> Unit) {
            textItem.text = item.title
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}