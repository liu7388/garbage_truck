package com.example.garbage_truck.data

import com.google.firebase.firestore.GeoPoint

data class FavoriteAddress(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val location: GeoPoint? = null,
    val userId: String = ""
)