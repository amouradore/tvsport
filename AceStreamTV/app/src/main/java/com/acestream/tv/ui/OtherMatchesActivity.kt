package com.acestream.tv.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.acestream.tv.R
import com.acestream.tv.databinding.ActivityOtherMatchesBinding
import com.acestream.tv.model.Match
import com.acestream.tv.ui.adapter.CompetitionAdapter
import com.acestream.tv.ui.adapter.CompetitionItem
import com.acestream.tv.ui.adapter.MatchAdapter
import com.acestream.tv.ui.player.PlayerActivity
import com.acestream.tv.viewmodel.ChannelViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class OtherMatchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherMatchesBinding
    private lateinit var adapter: MatchAdapter
    private val viewModel: ChannelViewModel by viewModels()
    
    // Use same URL as MatchesActivity to get all matches
    private val MATCHES_URL = "https://raw.githubusercontent.com/amouradore/tvsport/main/matches.json"
    
    private var allMatches: List<Match> = emptyList()
    private var allOtherMatches: List<Match> = emptyList() // Only excluded competitions
    private var selectedCompetition: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("OtherMatchesActivity", "onCreate: starting")
        try {
            binding = ActivityOtherMatchesBinding.inflate(layoutInflater)
            Log.d("OtherMatchesActivity", "onCreate: binding inflated")
            setContentView(binding.root)
            Log.d("OtherMatchesActivity", "onCreate: contentView set")
            
            setupToolbar()
            Log.d("OtherMatchesActivity", "onCreate: toolbar setup done")
            setupRecyclerView()
            Log.d("OtherMatchesActivity", "onCreate: recyclerView setup done")
            setupSearch()
            Log.d("OtherMatchesActivity", "onCreate: search setup done")
            loadMatches()
            Log.d("OtherMatchesActivity", "onCreate: loadMatches called")
        } catch (e: Exception) {
            Log.e("OtherMatchesActivity", "onCreate CRASHED: ${e.message}", e)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = MatchAdapter { match ->
            findAndPlayChannel(match)
        }
        binding.matchesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.matchesRecyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterMatches(s.toString())
            }
        })
    }
    
    private fun filterMatches(query: String) {
        val baseList = if (selectedCompetition != null) {
            allOtherMatches.filter { it.competition.equals(selectedCompetition, ignoreCase = true) }
        } else {
            allOtherMatches
        }
        
        if (query.isEmpty()) {
            adapter.submitList(baseList)
            return
        }
        
        val filteredList = baseList.filter { match ->
            match.homeTeam.contains(query, ignoreCase = true) ||
            match.awayTeam.contains(query, ignoreCase = true) ||
            match.competition.contains(query, ignoreCase = true)
        }
        
        adapter.submitList(filteredList)
    }

    private fun loadMatches() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(MATCHES_URL).build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    
                    val json = response.body?.string()
                    
                    val matches: List<Match> = try {
                        val matchType = object : TypeToken<List<Match>>() {}.type
                        Gson().fromJson(json, matchType)
                    } catch (e: Exception) {
                        try {
                            val jsonObject = Gson().fromJson(json, com.google.gson.JsonObject::class.java)
                            if (jsonObject.has("value")) {
                                val matchType = object : TypeToken<List<Match>>() {}.type
                                Gson().fromJson(jsonObject.get("value"), matchType)
                            } else {
                                emptyList()
                            }
                        } catch (e2: Exception) {
                            Log.e("OtherMatchesActivity", "Failed to parse JSON", e2)
                            emptyList()
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        binding.loadingIndicator.visibility = View.GONE
                        if (matches.isEmpty()) {
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = getString(R.string.no_matches_found)
                        } else {
                            // Filter to ONLY show excluded competitions (opposite of MatchesActivity)
                            val convertedMatches = filterAndConvertMatches(matches)
                            allOtherMatches = convertedMatches.filter { match ->
                                MatchesActivity.EXCLUDED_COMPETITIONS.any { excluded ->
                                    match.competition.contains(excluded, ignoreCase = true)
                                }
                            }
                            
                            if (allOtherMatches.isEmpty()) {
                                binding.errorText.visibility = View.VISIBLE
                                binding.errorText.text = getString(R.string.no_other_matches)
                            } else {
                                // Show competition picker
                                showCompetitionPicker()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OtherMatchesActivity", "Error loading matches", e)
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.error_loading, e.message ?: "Unknown error")
                }
            }
        }
    }
    
    private fun showCompetitionPicker() {
        // Group matches by competition and create CompetitionItems
        val competitionGroups = allOtherMatches.groupBy { it.competition }
        val competitionItems = competitionGroups.map { (name, matches) ->
            // Use the first match's home logo as the competition logo
            val logoUrl = matches.firstOrNull()?.homeLogo ?: ""
            CompetitionItem(name = name, logoUrl = logoUrl, matchCount = matches.size)
        }.sortedByDescending { it.matchCount }
        
        // Create BottomSheet
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_competitions, null)
        
        val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.competitionsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val competitionAdapter = CompetitionAdapter { competition ->
            selectedCompetition = competition.name
            binding.toolbar.title = competition.name
            
            // Filter and show matches for this competition
            val competitionMatches = allOtherMatches.filter { 
                it.competition.equals(competition.name, ignoreCase = true) 
            }
            allMatches = competitionMatches
            adapter.submitList(competitionMatches)
            
            bottomSheetDialog.dismiss()
        }
        
        competitionAdapter.submitList(competitionItems)
        recyclerView.adapter = competitionAdapter
        
        bottomSheetDialog.setContentView(bottomSheetView)
        bottomSheetDialog.show()
    }
    
    private fun findAndPlayChannel(match: Match) {
        if (match.links.isNotEmpty()) {
            showChannelSelectionDialog(match)
            return
        }
        
        if (match.link.isNotEmpty() && match.link.startsWith("acestream://")) {
            val acestreamId = match.link.replace("acestream://", "").trim()
            Log.d("OtherMatchesActivity", "✅ Launching AceStream: $acestreamId for ${match.homeTeam} vs ${match.awayTeam}")
            launchPlayer(acestreamId, "${match.homeTeam} vs ${match.awayTeam}")
            return
        }
        
        Snackbar.make(binding.root, getString(R.string.no_stream_available), Snackbar.LENGTH_LONG).show()
    }
    
    private fun showChannelSelectionDialog(match: Match) {
        val channelNames = match.links.map { it.channelName }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_channel))
            .setItems(channelNames) { dialog, which ->
                val selectedLink = match.links[which]
                launchPlayer(selectedLink.acestreamId, "${match.homeTeam} vs ${match.awayTeam} - ${selectedLink.channelName}")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun launchPlayer(acestreamId: String, channelName: String) {
        com.acestream.tv.ads.AdManager.getInstance(this@OtherMatchesActivity).checkAndShowAd(this@OtherMatchesActivity) {
            val intent = Intent(this@OtherMatchesActivity, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_ID, "match_$acestreamId")
                putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, acestreamId)
                putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channelName)
            }
            startActivity(intent)
        }
    }
    
    private fun filterAndConvertMatches(matches: List<Match>): List<Match> {
        val now = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sourceTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Europe/Madrid")
        }
        val localTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        
        return matches.mapNotNull { match ->
            try {
                val matchDate = dateFormat.parse(match.date) ?: return@mapNotNull null
                val matchTime = sourceTimeFormat.parse(match.time) ?: return@mapNotNull null
                
                val matchCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Madrid")).apply {
                    time = matchDate
                    set(java.util.Calendar.HOUR_OF_DAY, matchTime.hours)
                    set(java.util.Calendar.MINUTE, matchTime.minutes)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                
                val matchEndCalendar = matchCalendar.clone() as java.util.Calendar
                matchEndCalendar.add(java.util.Calendar.HOUR_OF_DAY, 6)
                
                if (now.timeInMillis > matchEndCalendar.timeInMillis) {
                    return@mapNotNull null
                }
                
                val localTimeStr = localTimeFormat.format(matchCalendar.time)
                match.copy(time = localTimeStr)
            } catch (e: Exception) {
                Log.e("OtherMatchesActivity", "Error converting match: ${e.message}")
                match
            }
        }
    }
}
