package com.acestream.tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.acestream.tv.R
import com.acestream.tv.databinding.ItemMatchBinding
import com.acestream.tv.model.Match
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MatchAdapter(
    private val onMatchClick: (Match) -> Unit
) : ListAdapter<Match, MatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MatchViewHolder(
        private val binding: ItemMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMatchClick(getItem(position))
                }
            }
        }

        fun bind(match: Match) {
            // Convert UTC time to user's timezone
            binding.matchTime.text = convertToUserTimezone(match.time, match.date)
            binding.homeTeam.text = match.homeTeam
            binding.awayTeam.text = match.awayTeam

            // Load team logos
            binding.homeLogo.load(match.homeLogo) {
                crossfade(true)
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
            }
            
            binding.awayLogo.load(match.awayLogo) {
                crossfade(true)
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
            }
            
            // Display competition and channels
            binding.channelsContainer.removeAllViews()
            
            // Add competition if available
            if (match.competition.isNotEmpty()) {
                val competitionView = TextView(binding.root.context).apply {
                    text = "🏆 ${match.competition}"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, 8, 0, 8)
                }
                binding.channelsContainer.addView(competitionView)
            }
            
            // Add channels with icons
            match.channels.forEach { channelName ->
                val channelView = TextView(binding.root.context).apply {
                    text = "📺 $channelName"
                    setTextColor(Color.LTGRAY)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(0, 4, 0, 4)
                }
                binding.channelsContainer.addView(channelView)
            }
        }
        
        private fun convertToUserTimezone(time: String, date: String): String {
            return try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                
                val outputFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                outputFormat.timeZone = java.util.TimeZone.getDefault()
                
                val dateTime = inputFormat.parse("$date $time")
                dateTime?.let { outputFormat.format(it) } ?: time
            } catch (e: Exception) {
                time // Return original time if conversion fails
            }
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem.homeTeam == newItem.homeTeam && oldItem.awayTeam == newItem.awayTeam
        }

        override fun areContentsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem == newItem
        }
    }
}
