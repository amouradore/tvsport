package com.acestream.tv.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.acestream.tv.databinding.ActivityMatchesBinding
import com.acestream.tv.model.Match
import com.acestream.tv.ui.adapter.MatchAdapter
import com.acestream.tv.ui.player.PlayerActivity
import com.acestream.tv.viewmodel.ChannelViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MatchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchesBinding
    private lateinit var adapter: MatchAdapter
    private val viewModel: ChannelViewModel by viewModels()
    
    // URL to your raw JSON on GitHub
    private val MATCHES_URL = "https://raw.githubusercontent.com/amouradore/tvsport/main/matches.json"
    
    private var allMatches: List<Match> = emptyList() 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MatchesActivity", "onCreate: starting")
        try {
            binding = ActivityMatchesBinding.inflate(layoutInflater)
            Log.d("MatchesActivity", "onCreate: binding inflated")
            setContentView(binding.root)
            Log.d("MatchesActivity", "onCreate: contentView set")
            
            setupToolbar()
            Log.d("MatchesActivity", "onCreate: toolbar setup done")
            setupRecyclerView()
            Log.d("MatchesActivity", "onCreate: recyclerView setup done")
            setupSearch()
            Log.d("MatchesActivity", "onCreate: search setup done")
            loadMatches()
            Log.d("MatchesActivity", "onCreate: loadMatches called")
        } catch (e: Exception) {
            Log.e("MatchesActivity", "onCreate CRASHED: ${e.message}", e)
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
        if (query.isEmpty()) {
            adapter.submitList(allMatches)
            return
        }
        
        val filteredList = allMatches.filter { match ->
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
                    
                    // Handle both formats: direct list or wrapped in {"value": [...]}
                    val matches: List<Match> = try {
                        // First try parsing as direct list
                        val matchType = object : TypeToken<List<Match>>() {}.type
                        Gson().fromJson(json, matchType)
                    } catch (e: Exception) {
                        // If that fails, try parsing as wrapped object
                        try {
                            val jsonObject = Gson().fromJson(json, com.google.gson.JsonObject::class.java)
                            if (jsonObject.has("value")) {
                                val matchType = object : TypeToken<List<Match>>() {}.type
                                Gson().fromJson(jsonObject.get("value"), matchType)
                            } else {
                                emptyList()
                            }
                        } catch (e2: Exception) {
                            Log.e("MatchesActivity", "Failed to parse JSON", e2)
                            emptyList()
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        binding.loadingIndicator.visibility = View.GONE
                        if (matches.isEmpty()) {
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = "Aucun match trouvé."
                        } else {
                            // Filtrer les matches terminés (3h après le début) et convertir l'heure
                            val filteredMatches = filterAndConvertMatches(matches)
                            
                            if (filteredMatches.isEmpty()) {
                                binding.errorText.visibility = View.VISIBLE
                                binding.errorText.text = "Aucun match en cours ou à venir."
                            } else {
                                allMatches = filteredMatches
                                adapter.submitList(filteredMatches)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MatchesActivity", "Error loading matches", e)
                withContext(Dispatchers.Main) {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Erreur de chargement: ${e.message}"
                     // Load local test data if failed (Backup/Demo)
                     // loadTestData() 
                }
            }
        }
    }
    
    private fun findAndPlayChannel(match: Match) {
        // Si le match a plusieurs liens, afficher un dialogue de sélection
        if (match.links.isNotEmpty()) {
            showChannelSelectionDialog(match)
            return
        }
        
        // Sinon, utiliser le lien direct si disponible
        if (match.link.isNotEmpty() && match.link.startsWith("acestream://")) {
            val acestreamId = match.link.replace("acestream://", "").trim()
            Log.d("MatchesActivity", "✅ Lancement direct AceStream: $acestreamId pour ${match.homeTeam} vs ${match.awayTeam}")
            launchPlayer(acestreamId, "${match.homeTeam} vs ${match.awayTeam}")
            return
        }
        
        // Fallback: afficher un message d'erreur
        Snackbar.make(binding.root, "Aucun lien de diffusion disponible pour ce match", Snackbar.LENGTH_LONG).show()
    }
    
    private fun showChannelSelectionDialog(match: Match) {
        val channelNames = match.links.map { it.channelName }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choisir une chaîne")
            .setItems(channelNames) { dialog, which ->
                val selectedLink = match.links[which]
                launchPlayer(selectedLink.acestreamId, "${match.homeTeam} vs ${match.awayTeam} - ${selectedLink.channelName}")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun launchPlayer(acestreamId: String, channelName: String) {
        com.acestream.tv.ads.AdManager.getInstance(this@MatchesActivity).checkAndShowAd(this@MatchesActivity) {
            val intent = Intent(this@MatchesActivity, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_ID, "match_${acestreamId}")
                putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, acestreamId)
                putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channelName)
            }
            startActivity(intent)
        }
    }
    
    private fun filterAndConvertMatches(matches: List<Match>): List<Match> {
        val now = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        
        return matches.mapNotNull { match ->
            try {
                // Parser la date et l'heure du match
                val matchDate = dateFormat.parse(match.date) ?: return@mapNotNull null
                val matchTime = timeFormat.parse(match.time) ?: return@mapNotNull null
                
                // Combiner date et heure
                val matchCalendar = java.util.Calendar.getInstance().apply {
                    time = matchDate
                    set(java.util.Calendar.HOUR_OF_DAY, matchTime.hours)
                    set(java.util.Calendar.MINUTE, matchTime.minutes)
                }
                
                // Ajouter 3 heures pour la fin estimée du match
                val matchEndCalendar = matchCalendar.clone() as java.util.Calendar
                matchEndCalendar.add(java.util.Calendar.HOUR_OF_DAY, 3)
                
                // Filtrer si le match est terminé depuis plus de 3h
                if (now.after(matchEndCalendar)) {
                    Log.d("MatchesActivity", "Filtré (terminé): ${match.homeTeam} vs ${match.awayTeam}")
                    return@mapNotNull null
                }
                
                // Convertir l'heure au fuseau horaire local (format 24h)
                val localTimeStr = timeFormat.format(matchCalendar.time)
                
                // Retourner le match avec l'heure convertie
                match.copy(time = localTimeStr)
            } catch (e: Exception) {
                Log.e("MatchesActivity", "Erreur conversion match: ${e.message}")
                match // Garder le match en cas d'erreur
            }
        }
    }
}
