package com.example.garbage_truck

enum class ItemType {
    SWITCH,
    CLICKABLE
}

data class SettingItem(
    val title: String,
    val isChecked: Boolean = false, // Default to false, only used by SWITCH
    val type: ItemType = ItemType.SWITCH // Default to SWITCH for backward compatibility
)