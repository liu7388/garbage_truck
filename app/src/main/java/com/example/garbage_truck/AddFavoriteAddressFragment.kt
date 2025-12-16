package com.example.garbage_truck

import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.garbage_truck.data.FavoriteAddress
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class AddFavoriteAddressFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var nameEditText: EditText
    private lateinit var addressEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_favorite_address, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        nameEditText = view.findViewById(R.id.edit_text_address_name)
        addressEditText = view.findViewById(R.id.edit_text_address)
        saveButton = view.findViewById(R.id.button_save)

        saveButton.setOnClickListener { saveFavoriteAddress() }

        return view
    }

    private fun saveFavoriteAddress() {
        val name = nameEditText.text.toString().trim()
        val address = addressEditText.text.toString().trim()
        val currentUser = auth.currentUser

        if (name.isEmpty() || address.isEmpty()) {
            Toast.makeText(context, "名稱和地址不能為空", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUser == null) {
            Toast.makeText(context, "請先登入", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext())
                val geocodeResults = geocoder.getFromLocationName(address, 1)
                if (geocodeResults != null && geocodeResults.isNotEmpty()) {
                    val location = geocodeResults[0]
                    val geoPoint = GeoPoint(location.latitude, location.longitude)

                    val favoriteAddress = FavoriteAddress(
                        name = name,
                        address = address,
                        location = geoPoint,
                        userId = currentUser.uid
                    )

                    db.collection("favorites").add(favoriteAddress)
                        .addOnSuccessListener {
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "儲存成功", Toast.LENGTH_SHORT).show()
                                findNavController().popBackStack()
                            }
                        }
                        .addOnFailureListener { e ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "儲存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "找不到該地址的座標", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "地址轉換失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}