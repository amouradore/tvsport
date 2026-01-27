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
            // Display pre-converted 24h local time
            binding.matchTime.text = match.time
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
            
            // Display competition only (NO channels visible in the list)
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
            
            // DO NOT display channels here - they will be shown in dialog when user clicks
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
