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
                            adapter.submitList(matches)
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
        val matchBroadcasters = match.channels
        
        lifecycleScope.launch {
             // Ensure channels are loaded
             if (viewModel.filteredChannels.value.isEmpty()) {
                 viewModel.loadChannels()
                 // Attendre que les chaînes soient chargées
                 kotlinx.coroutines.delay(500)
             }
             
             // Get latest list
             val appChannels = viewModel.filteredChannels.value
             
             if (appChannels.isEmpty()) {
                 Snackbar.make(binding.root, "Les chaînes ne sont pas encore chargées. Veuillez réessayer.", Snackbar.LENGTH_LONG).show()
                 return@launch
             }
             
             var foundChannel: com.acestream.tv.model.Channel? = null
             
             // Algorithme de matching amélioré
             for (broadcaster in matchBroadcasters) {
                 val target = broadcaster.lowercase().trim()
                 
                 // 1. Correspondance exacte
                 foundChannel = appChannels.find { appChannel ->
                     appChannel.name.lowercase().trim() == target
                 }
                 if (foundChannel != null) break
                 
                 // 2. Correspondance contient (dans les deux sens)
                 foundChannel = appChannels.find { appChannel ->
                     val appName = appChannel.name.lowercase().trim()
                     appName.contains(target) || target.contains(appName)
                 }
                 if (foundChannel != null) break
                 
                 // 3. Correspondance par mots-clés significatifs
                 val targetWords = target.split(" ", "+", "-", ":").filter { it.length > 2 }
                 foundChannel = appChannels.find { appChannel ->
                     val appName = appChannel.name.lowercase().trim()
                     val appWords = appName.split(" ", "+", "-", ":").filter { it.length > 2 }
                     
                     // Si au moins 2 mots correspondent, ou 1 mot si c'est un nom unique
                     val matchingWords = targetWords.count { targetWord ->
                         appWords.any { appWord -> 
                             appWord.contains(targetWord) || targetWord.contains(appWord)
                         }
                     }
                     matchingWords >= 1 && (targetWords.size == 1 || matchingWords >= 2)
                 }
                 if (foundChannel != null) break
             }
             
             if (foundChannel != null) {
                 Log.d("MatchesActivity", "✅ Chaîne trouvée: ${foundChannel!!.name} pour ${match.homeTeam} vs ${match.awayTeam}")
                 com.acestream.tv.ads.AdManager.getInstance(this@MatchesActivity).checkAndShowAd(this@MatchesActivity) {
                    val intent = Intent(this@MatchesActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, foundChannel!!.id)
                        putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, foundChannel!!.aceStreamId)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, foundChannel!!.name)
                    }
                    startActivity(intent)
                 }
             } else {
                 Log.e("MatchesActivity", "❌ Aucune chaîne trouvée. Recherché: ${matchBroadcasters.joinToString()}")
                 Log.d("MatchesActivity", "Chaînes disponibles: ${appChannels.take(5).map { it.name }}")
                 Snackbar.make(binding.root, "Aucune chaîne trouvée pour ce match.\nRecherché: ${matchBroadcasters.firstOrNull() ?: "N/A"}", Snackbar.LENGTH_LONG).show()
             }
        }
    }
}
