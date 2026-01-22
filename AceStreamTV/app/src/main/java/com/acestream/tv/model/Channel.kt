package com.acestream.tv.model

/**
 * Data class representing a TV channel from M3U playlist
 */
data class Channel(
    val id: String,                    // Unique identifier (hash of aceStreamId + name)
    val name: String,                  // Display name
    val logoUrl: String?,              // Logo URL (can be null)
    val groupTitle: String,            // Category/Group (e.g., "España", "DAZN", etc.)
    val tvgId: String?,                // EPG ID for TV guide
    val aceStreamId: String,           // AceStream content ID (40-char hex)
    val quality: StreamQuality = StreamQuality.UNKNOWN
) {
    /**
     * Generate the local HTTP URL for AceStream Engine
     */
    fun getStreamUrl(port: Int = 6878): String {
        return "http://127.0.0.1:$port/ace/getstream?id=$aceStreamId"
    }
    
    /**
     * Check if the channel has a valid logo
     */
    fun hasLogo(): Boolean = !logoUrl.isNullOrBlank()
}

/**
 * Stream quality enum
 */
enum class StreamQuality {
    SD,      // Standard Definition
    HD,      // 720p
    FHD,     // 1080p
    UHD,     // 4K
    UNKNOWN  // Not specified
}

/**
 * Data class for channel groups/categories
 */
data class ChannelGroup(
    val name: String,
    val channels: List<Channel>,
    val iconResId: Int? = null
) {
    val channelCount: Int get() = channels.size
}

/**
 * Status of AceStream engine
 */
sealed class EngineStatus {
    object Stopped : EngineStatus()
    object Starting : EngineStatus()
    object Running : EngineStatus()
    data class Error(val message: String) : EngineStatus()
}

/**
 * Playback state for UI
 */
sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Buffering : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val code: Int, val message: String) : PlaybackState()
}
