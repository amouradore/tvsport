package com.acestream.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.acestream.tv.acestream.AceStreamManager
import com.acestream.tv.model.Channel
import com.acestream.tv.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * AceStream Player wrapper around ExoPlayer
 * 
 * Handles:
 * - ExoPlayer initialization with optimized buffering for P2P streaming
 * - Integration with AceStream Engine for stream URL generation
 * - Playback state management
 */
class AceStreamPlayer(private val context: Context) {
    
    // Retry configuration
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    private var exoPlayer: ExoPlayer? = null
    private val aceStreamManager = AceStreamManager.getInstance(context)
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState
    
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel
    
    // Track current stream to avoid recreating connections
    private var currentStreamUrl: String? = null
    private var currentAceStreamId: String? = null

    // Callbacks
    var onPlayerReady: (() -> Unit)? = null
    var onError: ((PlaybackException) -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null

    /**
     * Initialize ExoPlayer with optimized settings for AceStream
     */
    fun initialize() {
        if (exoPlayer != null) return
        
        // 1. Configure OkHttp with strict ConnectionPool to prevent socket exhaustion
        // AceStream local engine has a limit of ~1024 sockets. 
        // Standard HTTP client opens a new connection for each TS segment without closing fast enough.
        val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
        
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("AceStream/3.1.77.9 (Android) ExoPlayerLib/2.18.1")

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        
        // 2. Optimized load control
        // Reduced buffer sizes to avoid "burst" requests at startup
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,    // Min buffer (reduced from 15s)
                30_000,   // Max buffer (reduced from 60s)
                1_500,    // Buffer for playback start
                5_000     // Buffer for rebuffer
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(10_000, true)
            .build()
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                player.addListener(playerListener)
                player.playWhenReady = true
            }
        
        android.util.Log.d("AceStreamPlayer", "ExoPlayer initialized with OkHttp + ConnectionPool(5)")
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> _playbackState.value = PlaybackState.Idle
                Player.STATE_BUFFERING -> {
                    _playbackState.value = PlaybackState.Buffering
                    onBufferingChanged?.invoke(true)
                }
                Player.STATE_READY -> {
                    _playbackState.value = PlaybackState.Playing
                    onBufferingChanged?.invoke(false)
                    onPlayerReady?.invoke()
                }
                Player.STATE_ENDED -> _playbackState.value = PlaybackState.Idle
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = PlaybackState.Error(error.errorCode, error.message ?: "Unknown error")
            onError?.invoke(error)
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                _playbackState.value = PlaybackState.Playing
            } else if (exoPlayer?.playbackState == Player.STATE_READY) {
                _playbackState.value = PlaybackState.Paused
            }
        }
    }

    /**
     * Play a channel
     */
    fun playChannel(channel: Channel) {
        _currentChannel.value = channel
        _playbackState.value = PlaybackState.Loading
        
        // Ensure engine is running
        if (!aceStreamManager.isEngineReady()) {
            aceStreamManager.onEngineReady = {
                startPlayback(channel)
            }
            aceStreamManager.startEngine()
        } else {
            startPlayback(channel)
        }
    }

    private fun startPlayback(channel: Channel) {
        val streamUrl = aceStreamManager.getStreamUrl(channel.aceStreamId)
            ?: channel.getStreamUrl()
        
        // Avoid reloading if already playing the same stream
        if (currentStreamUrl == streamUrl && currentAceStreamId == channel.aceStreamId) {
            android.util.Log.d("AceStreamPlayer", "Same stream already playing, skipping reload")
            if (exoPlayer?.isPlaying == false) {
                exoPlayer?.play()
            }
            return
        }
        
        currentStreamUrl = streamUrl
        currentAceStreamId = channel.aceStreamId
        
        android.util.Log.d("AceStreamPlayer", "Starting new stream: ${channel.name}")
        
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
        }
    }

    /**
     * Play directly from AceStream ID
     */
    fun playFromAceStreamId(aceStreamId: String) {
        _playbackState.value = PlaybackState.Loading
        
        if (!aceStreamManager.isEngineReady()) {
            aceStreamManager.onEngineReady = {
                startPlaybackFromId(aceStreamId)
            }
            aceStreamManager.startEngine()
        } else {
            startPlaybackFromId(aceStreamId)
        }
    }

    private fun startPlaybackFromId(aceStreamId: String) {
        val streamUrl = aceStreamManager.getStreamUrl(aceStreamId)
            ?: "http://127.0.0.1:6878/ace/manifest.m3u8?id=$aceStreamId"
        
        // Avoid reloading if already playing the same stream
        if (currentStreamUrl == streamUrl && currentAceStreamId == aceStreamId) {
            android.util.Log.d("AceStreamPlayer", "Same AceStream ID already playing, skipping reload")
            if (exoPlayer?.isPlaying == false) {
                exoPlayer?.play()
            }
            return
        }
        
        currentStreamUrl = streamUrl
        currentAceStreamId = aceStreamId
        
        android.util.Log.d("AceStreamPlayer", "Starting HLS playback: $streamUrl")
        
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
        }
    }

    /**
     * Attach player to a view
     */
    fun attachToView(playerView: PlayerView) {
        playerView.player = exoPlayer
    }

    /**
     * Detach player from view
     */
    fun detachFromView(playerView: PlayerView) {
        playerView.player = null
    }

    // Playback controls
    fun play() = exoPlayer?.play()
    fun pause() = exoPlayer?.pause()
    fun stop() = exoPlayer?.stop()
    
    fun seekTo(positionMs: Long) = exoPlayer?.seekTo(positionMs)
    fun seekForward(offsetMs: Long = 10_000) {
        exoPlayer?.let { it.seekTo(it.currentPosition + offsetMs) }
    }
    fun seekBackward(offsetMs: Long = 10_000) {
        exoPlayer?.let { it.seekTo(maxOf(0, it.currentPosition - offsetMs)) }
    }
    
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }
    
    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    // State getters
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0
    fun getDuration(): Long = exoPlayer?.duration ?: 0
    fun getBufferedPercentage(): Int = exoPlayer?.bufferedPercentage ?: 0

    /**
     * Release all resources
     */
    fun release() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        _currentChannel.value = null
        _playbackState.value = PlaybackState.Idle
        currentStreamUrl = null
        currentAceStreamId = null
    }
}


