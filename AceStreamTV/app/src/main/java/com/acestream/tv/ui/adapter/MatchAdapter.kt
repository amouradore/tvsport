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
            
            // Extract phase if present (e.g. "Fase Liga", "1/8 de final")
            val phaseKeywords = listOf(
                "Fase Liga", "1/8 de final", "1/4 de final", "Semifinal", "Final", 
                "Top 16", "Jornada", "Primera fase", "Segunda fase", "Sesión", "Ronda"
            )
            
            var extractedPhase: String? = null
            var cleanHomeTeam = match.homeTeam
            
            for (keyword in phaseKeywords) {
                if (match.homeTeam.startsWith(keyword, ignoreCase = true)) {
                    extractedPhase = match.homeTeam.substring(0, keyword.length).trim()
                    cleanHomeTeam = match.homeTeam.substring(keyword.length).trim()
                    break
                }
            }
            
            // If no keyword matched but it starts with "Fase ", extract it
            if (extractedPhase == null && match.homeTeam.startsWith("Fase ", ignoreCase = true)) {
                val spaceIndex = match.homeTeam.indexOf(" ", 5)
                if (spaceIndex > 0) {
                    extractedPhase = match.homeTeam.substring(0, spaceIndex).trim()
                    cleanHomeTeam = match.homeTeam.substring(spaceIndex).trim()
                }
            }

            binding.homeTeam.text = cleanHomeTeam
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
            
            // Display phase and competition in the center container
            binding.channelsContainer.removeAllViews()
            
            // 1. Add Phase if extracted
            if (extractedPhase != null) {
                val phaseView = TextView(binding.root.context).apply {
                    text = extractedPhase
                    setTextColor(Color.parseColor("#39FF14")) // Neon Green to match LIVE
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 2)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                binding.channelsContainer.addView(phaseView)
            }

            // 2. Add competition if available
            if (match.competition.isNotEmpty()) {
                val competitionView = TextView(binding.root.context).apply {
                    text = "🏆 ${match.competition}"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(0, 2, 0, 0)
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
