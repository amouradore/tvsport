package com.acestream.tv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.acestream.tv.R

/**
 * Data class representing a competition with its logo and match count
 */
data class CompetitionItem(
    val name: String,
    val logoUrl: String,
    val matchCount: Int
)

/**
 * Adapter for displaying competitions in a BottomSheet picker
 */
class CompetitionAdapter(
    private val onCompetitionClick: (CompetitionItem) -> Unit
) : ListAdapter<CompetitionItem, CompetitionAdapter.CompetitionViewHolder>(CompetitionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompetitionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_competition, parent, false)
        return CompetitionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompetitionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CompetitionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logo: ImageView = itemView.findViewById(R.id.competitionLogo)
        private val name: TextView = itemView.findViewById(R.id.competitionName)
        private val count: TextView = itemView.findViewById(R.id.matchCount)

        fun bind(competition: CompetitionItem) {
            name.text = competition.name
            count.text = "${competition.matchCount} events"
            
            logo.load(competition.logoUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
                transformations(CircleCropTransformation())
            }

            itemView.setOnClickListener {
                onCompetitionClick(competition)
            }
        }
    }

    class CompetitionDiffCallback : DiffUtil.ItemCallback<CompetitionItem>() {
        override fun areItemsTheSame(oldItem: CompetitionItem, newItem: CompetitionItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: CompetitionItem, newItem: CompetitionItem): Boolean {
            return oldItem == newItem
        }
    }
}
