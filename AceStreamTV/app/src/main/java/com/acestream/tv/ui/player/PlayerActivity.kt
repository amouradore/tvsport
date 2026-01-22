package com.acestream.tv.ui.player

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.acestream.tv.R
import com.acestream.tv.databinding.ActivityPlayerBinding
import com.acestream.tv.model.PlaybackState
import com.acestream.tv.player.AceStreamPlayer
import com.acestream.tv.viewmodel.ChannelViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full-screen player activity for video playback
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: AceStreamPlayer
    private val viewModel: ChannelViewModel by viewModels()

    private var channelId: String? = null
    private var aceStreamId: String? = null
    private var channelName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract intent data
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        aceStreamId = intent.getStringExtra(EXTRA_ACESTREAM_ID)
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)

        setupFullscreen()
        setupPlayer()
        setupUI()
        observePlayback()

        // Start playback
        startPlayback()
    }

    private fun setupFullscreen() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system UI
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun setupPlayer() {
        player = AceStreamPlayer(this)
        player.initialize()
        player.attachToView(binding.playerView)

        player.onPlayerReady = {
            hideLoading()
        }

        player.onError = { error ->
            showError(error.message ?: getString(R.string.playback_error))
        }

        player.onBufferingChanged = { isBuffering ->
            if (isBuffering) showLoading() else hideLoading()
        }
    }

    private fun setupUI() {
        // Channel name
        binding.channelNameText.text = channelName ?: getString(R.string.unknown_channel)

        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Retry button
        binding.btnRetry.setOnClickListener {
            binding.errorLayout.visibility = View.GONE
            startPlayback()
        }

        // Player controls (handled by ExoPlayer's PlayerView)
        binding.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        binding.playerView.controllerAutoShow = true
        binding.playerView.controllerShowTimeoutMs = 3000
    }

    private fun observePlayback() {
        lifecycleScope.launch {
            player.playbackState.collectLatest { state ->
                when (state) {
                    is PlaybackState.Loading -> showLoading()
                    is PlaybackState.Buffering -> showLoading()
                    is PlaybackState.Playing -> hideLoading()
                    is PlaybackState.Paused -> hideLoading()
                    is PlaybackState.Error -> showError(state.message)
                    is PlaybackState.Idle -> { /* Initial state */ }
                }
            }
        }
    }

    private fun startPlayback() {
        showLoading()
        
        val contentId = aceStreamId ?: if (channelId != null) viewModel.getChannel(channelId!!)?.aceStreamId else null
        
        if (contentId != null) {
            try {
                // Find the installed AceStream package
                val acePackage = findInstalledAceStreamPackage()
                
                if (acePackage != null) {
                    val streamUrl = "http://127.0.0.1:6878/ace/getstream?id=$contentId"
                    val uri = android.net.Uri.parse(streamUrl)
                    
                    // Base intent
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "video/*")
                    intent.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    
                    // Critical Fix: Resolve specific component to bypass chooser
                    val queryIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    queryIntent.setDataAndType(uri, "video/*")
                    queryIntent.setPackage(acePackage)
                    
                    val activities = packageManager.queryIntentActivities(queryIntent, 0)
                    if (activities.isNotEmpty()) {
                        // Take the first matching activity in the target package
                        val activityInfo = activities[0].activityInfo
                        val component = android.content.ComponentName(acePackage, activityInfo.name)
                        intent.component = component
                        android.util.Log.d("PlayerActivity", "Forcing component to bypass chooser: $component")
                    } else {
                        // Fallback: just set package
                        intent.setPackage(acePackage)
                    }
                    
                    startActivity(intent)
                    finish()
                } else {
                    showError("AceStream n'est pas installé. Veuillez installer Ace Player.")
                }
            } catch (e: Exception) {
                showError("Erreur lors du lancement: ${e.message}")
            }
        } else {
            showError(getString(R.string.channel_not_found))
        }
    }
    
    /**
     * Find the installed AceStream package
     */
    private fun findInstalledAceStreamPackage(): String? {
        val packages = listOf(
            "org.acestream.media",      // Ace Player HD (preferred for playback)
            "org.acestream.media.atv",  // Ace Player for Android TV
            "org.acestream.node",       // AceStream Engine
            "org.acestream.core",       // AceStream Core
            "org.acestream.core.atv",   // AceStream Core for Android TV
            "org.acestream.engine"      // AceStream Engine alternative
        )
        
        for (pkg in packages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                android.util.Log.d("PlayerActivity", "Found AceStream package: $pkg")
                return pkg
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                // Continue searching
            }
        }
        return null
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.loadingLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_ACESTREAM_ID = "acestream_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
    }
}
