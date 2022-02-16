package com.example.musicplayerntt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for recycler view with pair of string values.
 *
 * @param onClick method called when one of items has been clicked.
 * @see ListAdapter
 */
class CustomAdapter(private val onClick: (Pair<String, String>) -> Unit) :
    ListAdapter<Pair<String, String>, CustomAdapter.NameAndAddressViewHolder>(NameDiffCallback) {

    /**
     * ViewHolder for name and address.
     *
     * @param itemView inflated view.
     * @param onClick method called when one of items has been clicked.
     * @constructor sets onClickListener for current name and address.
     * @see RecyclerView.ViewHolder
     */
    class NameAndAddressViewHolder(itemView: View, val onClick: (Pair<String, String>) -> Unit) : RecyclerView.ViewHolder(itemView) {

        /**
         * Text view for name.
         */
        private val nameTextView: TextView = itemView.findViewById(R.id.name)
        /**
         * Name and address values in pair.
         */
        private var currentNameAndAddress: Pair<String, String>? = null

        init {
            itemView.setOnClickListener {
                currentNameAndAddress?.let {
                    onClick(it)
                }
            }
        }

        /**
         * Sets current name and address and binds text view as a given name.
         *
         * @param nameAndAddress pair of values as which current name and address will be set.
         */
        fun bind(nameAndAddress: Pair<String, String>) {
            currentNameAndAddress = nameAndAddress
            nameTextView.text = nameAndAddress.first
        }
    }

    /**
     * Creates new view.
     * @see ListAdapter.onCreateViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NameAndAddressViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_row, parent, false)
        return NameAndAddressViewHolder(view, onClick)
    }

    /**
     * Binds name and address at the specified position.
     * @see ListAdapter.onBindViewHolder
     */
    override fun onBindViewHolder(holder: NameAndAddressViewHolder, position: Int) {
        val nameAndAddress = getItem(position)
        holder.bind(nameAndAddress)
    }

    /**
     * @see DiffUtil.ItemCallback
     */
    private object NameDiffCallback : DiffUtil.ItemCallback<Pair<String, String>>() {

        /**
         * @see DiffUtil.ItemCallback.areItemsTheSame
         */
        override fun areItemsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem == newItem
        }
        /**
         * @see DiffUtil.ItemCallback.areContentsTheSame
         */
        override fun areContentsTheSame(oldItem: Pair<String, String>, newItem: Pair<String, String>): Boolean {
            return oldItem.first == newItem.first && oldItem.second == newItem.second
        }
    }
}