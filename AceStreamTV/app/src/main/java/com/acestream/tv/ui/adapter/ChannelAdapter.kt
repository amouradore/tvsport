package com.acestream.tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.acestream.tv.R
import com.acestream.tv.databinding.ItemChannelBinding
import com.acestream.tv.model.Channel

/**
 * Adapter for channel grid/list
 */
class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onChannelClick(getItem(position))
                }
            }
        }

        fun bind(channel: Channel) {
            binding.channelName.text = channel.name
            binding.channelGroup.text = channel.groupTitle
            
            // Load logo
            if (channel.hasLogo()) {
                binding.channelLogo.load(channel.logoUrl) {
                    placeholder(R.drawable.ic_channel_placeholder)
                    error(R.drawable.ic_channel_placeholder)
                    transformations(RoundedCornersTransformation(8f))
                    crossfade(true)
                }
            } else {
                binding.channelLogo.setImageResource(R.drawable.ic_channel_placeholder)
            }
            
            // Quality badge
            binding.qualityBadge.text = channel.quality.name
            binding.qualityBadge.visibility = if (channel.quality.name != "UNKNOWN") {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Adapter for group filter chips
 */
class GroupChipAdapter(
    private val onGroupClick: (String) -> Unit
) : ListAdapter<String, GroupChipAdapter.GroupViewHolder>(GroupDiffCallback()) {

    private var selectedGroup: String? = null

    fun setSelectedGroup(group: String?) {
        val oldSelected = selectedGroup
        selectedGroup = group
        
        // Notify changes
        if (oldSelected != null) {
            val oldPosition = currentList.indexOf(oldSelected)
            if (oldPosition >= 0) notifyItemChanged(oldPosition)
        }
        if (group != null) {
            val newPosition = currentList.indexOf(group)
            if (newPosition >= 0) notifyItemChanged(newPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val chip = com.google.android.material.chip.Chip(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            isCheckable = true
        }
        return GroupViewHolder(chip)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(
        private val chip: com.google.android.material.chip.Chip
    ) : RecyclerView.ViewHolder(chip) {

        init {
            chip.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onGroupClick(getItem(position))
                }
            }
        }

        fun bind(groupName: String) {
            chip.text = groupName
            chip.isChecked = groupName == selectedGroup
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
