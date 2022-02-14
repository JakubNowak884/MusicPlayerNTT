package com.example.musicplayerntt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class CustomAdapter(private val onClick: (Pair<String, String>) -> Unit) :
    ListAdapter<Pair<String, String>, CustomAdapter.NameAndAddressViewHolder>(NameDiffCallback) {

    class NameAndAddressViewHolder(itemView: View, val onClick: (Pair<String, String>) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.name)
        private var currentNameAndAddress: Pair<String, String>? = null

        init {
            itemView.setOnClickListener {
                currentNameAndAddress?.let {
                    onClick(it)
                }
            }
        }

        fun bind(nameAndAddress: Pair<String, String>) {
            currentNameAndAddress = nameAndAddress
            nameTextView.text = nameAndAddress.first
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NameAndAddressViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_row, parent, false)
        return NameAndAddressViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: NameAndAddressViewHolder, position: Int) {
        val nameAndAddress = getItem(position)
        holder.bind(nameAndAddress)
    }

    private object NameDiffCallback : DiffUtil.ItemCallback<Pair<String, String>>() {
        override fun areItemsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem.first == newItem.first && oldItem.second == newItem.second
        }
    }
}