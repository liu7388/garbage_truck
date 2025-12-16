package com.example.garbage_truck

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.garbage_truck.data.FavoriteAddress
import com.example.garbage_truck.databinding.ItemFavoriteAddressBinding

class FavoriteAddressAdapter(
    private val favoriteAddresses: MutableList<FavoriteAddress>,
    private val onItemClicked: (FavoriteAddress) -> Unit
) : RecyclerView.Adapter<FavoriteAddressAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteAddressBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val address = favoriteAddresses[position]
        holder.bind(address)
        holder.itemView.setOnClickListener { onItemClicked(address) }
    }

    override fun getItemCount(): Int = favoriteAddresses.size

    fun updateData(newAddresses: List<FavoriteAddress>) {
        favoriteAddresses.clear()
        favoriteAddresses.addAll(newAddresses)
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ItemFavoriteAddressBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(address: FavoriteAddress) {
            binding.textViewAddressName.text = address.name
        }
    }
}
