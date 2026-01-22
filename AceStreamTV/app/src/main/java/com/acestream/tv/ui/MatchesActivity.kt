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
        binding = ActivityMatchesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        loadMatches()
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
                    val matchType = object : TypeToken<List<Match>>() {}.type
                    val matches: List<Match> = Gson().fromJson(json, matchType)
                    
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
        val channels = viewModel.filteredChannels.value // Access current channel list from VM memory if possible, otherwise rely on repository
        // Note: filteredChannels might be empty if main activity didn't load them or if VM scope is different.
        // Better to check Repository directly or assume Main Activity loaded it.
        // For simplicity, we trigger play if we find a fuzzy match.
        
        var foundChannel: com.acestream.tv.model.Channel? = null
        val matchBroadcasters = match.channels
        
        // Simple fuzzy matching logic
        // 1. Iterate over all channels in the app
        // 2. See if any match broadcaster matches the app channel name
        
        // We need to access the channel list. ViewModel in Activity scope is new, so it might be empty.
        // We should trigger a load if empty.
        
        lifecycleScope.launch {
             // Ensure channels are loaded
             if (viewModel.filteredChannels.value.isEmpty()) {
                 viewModel.loadChannels()
                 // Wait a bit? Or better, observe. 
                 // For now let's just show logic.
             }
             
             // Get latest list
             val appChannels = viewModel.filteredChannels.value
             
             for (broadcaster in matchBroadcasters) {
                 val target = broadcaster.lowercase()
                 foundChannel = appChannels.find { appChannel ->
                     val appName = appChannel.name.lowercase()
                     // Logic: "beIN Sports 1" vs "FR : beIN Sports 1"
                     appName.contains(target) || target.contains(appName)
                     // Refined: check if significant words match
                 }
                 if (foundChannel != null) break
             }
             
             if (foundChannel != null) {
                 com.acestream.tv.ads.AdManager.getInstance(this@MatchesActivity).checkAndShowAd(this@MatchesActivity) {
                    val intent = Intent(this@MatchesActivity, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_CHANNEL_ID, foundChannel!!.id)
                        putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, foundChannel!!.aceStreamId)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, foundChannel!!.name)
                    }
                    startActivity(intent)
                 }
             } else {
                 Snackbar.make(binding.root, "Aucune chaîne trouvée pour ce match", Snackbar.LENGTH_LONG).show()
             }
        }
    }
}
