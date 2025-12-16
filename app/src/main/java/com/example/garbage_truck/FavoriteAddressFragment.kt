package com.example.garbage_truck

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.garbage_truck.data.FavoriteAddress
import com.example.garbage_truck.databinding.FragmentFavoriteAddressBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject

class FavoriteAddressFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var favoriteAddressAdapter: FavoriteAddressAdapter
    private val favoriteAddresses = mutableListOf<FavoriteAddress>()

    private var _binding: FragmentFavoriteAddressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteAddressBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        fetchFavoriteAddresses()

        // The FAB is removed, so we don't need the click listener anymore.

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        favoriteAddressAdapter = FavoriteAddressAdapter(favoriteAddresses, onItemClicked = { address ->
            val location = address.location
            if (location != null) {
                val action = FavoriteAddressFragmentDirections.actionFavoriteAddressFragmentToMapFragment(
                    location.latitude.toFloat(),
                    location.longitude.toFloat()
                )
                findNavController().navigate(action)
            } else {
                Log.w("FavoriteAddressFragment", "Address location is null: ${address.name}")
            }
        })
        binding.recyclerViewFavoriteAddresses.adapter = favoriteAddressAdapter
        binding.recyclerViewFavoriteAddresses.layoutManager = LinearLayoutManager(context)
    }

    private fun fetchFavoriteAddresses() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("favorites")
                .whereEqualTo("userId", currentUser.uid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("FavoriteAddressFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    val addresses = mutableListOf<FavoriteAddress>()
                    if (snapshots != null) {
                        for (doc in snapshots) {
                            val address = doc.toObject<FavoriteAddress>().copy(id = doc.id)
                            addresses.add(address)
                        }
                    }
                    favoriteAddressAdapter.updateData(addresses)
                }
        }
    }
}
