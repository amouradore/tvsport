package com.acestream.tv.repository

import android.content.Context
import com.acestream.tv.model.Channel
import com.acestream.tv.model.ChannelGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Repository for managing channel data
 * 
 * Provides:
 * - Loading channels from assets or remote URL
 * - Caching loaded channels
 * - Filtering and searching
 */
class ChannelRepository(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: Flow<List<Channel>> = _channels

    private val _groups = MutableStateFlow<List<ChannelGroup>>(emptyList())
    val groups: Flow<List<ChannelGroup>> = _groups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error

    /**
     * Load channels from built-in assets
     */
    suspend fun loadFromAssets(fileName: String = "channels/canales_acestream.m3u") {
        _isLoading.value = true
        _error.value = null
        
        try {
            withContext(Dispatchers.IO) {
                val channels = M3UParser.parseFromAssets(context, fileName)
                _channels.value = channels
                _groups.value = M3UParser.groupChannels(channels)
            }
        } catch (e: Exception) {
            _error.value = "Failed to load channels: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Load channels from a remote URL
     */
    suspend fun loadFromUrl(url: String) {
        _isLoading.value = true
        _error.value = null
        
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AceStreamTV/1.0")
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                val content = response.body?.string() ?: ""
                val channels = M3UParser.parse(content)
                _channels.value = channels
                _groups.value = M3UParser.groupChannels(channels)
            }
        } catch (e: Exception) {
            _error.value = "Failed to load channels: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Load channels from M3U content string
     */
    suspend fun loadFromContent(content: String) {
        _isLoading.value = true
        _error.value = null
        
        try {
            withContext(Dispatchers.IO) {
                val channels = M3UParser.parse(content)
                _channels.value = channels
                _groups.value = M3UParser.groupChannels(channels)
            }
        } catch (e: Exception) {
            _error.value = "Failed to parse channels: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Get channels filtered by group
     */
    fun getChannelsByGroup(groupName: String): Flow<List<Channel>> {
        return channels.map { M3UParser.filterByGroup(it, groupName) }
    }

    /**
     * Search channels by query
     */
    fun searchChannels(query: String): Flow<List<Channel>> {
        return channels.map { M3UParser.searchChannels(it, query) }
    }

    /**
     * Get a channel by its ID
     */
    fun getChannelById(channelId: String): Channel? {
        return _channels.value.find { it.id == channelId }
    }

    /**
     * Get a channel by its AceStream ID
     */
    fun getChannelByAceStreamId(aceStreamId: String): Channel? {
        return _channels.value.find { it.aceStreamId == aceStreamId }
    }

    /**
     * Get list of all group names
     */
    fun getGroupNames(): List<String> {
        return M3UParser.getGroupNames(_channels.value)
    }

    /**
     * Get total channel count
     */
    fun getChannelCount(): Int = _channels.value.size

    /**
     * Clear all loaded channels
     */
    fun clear() {
        _channels.value = emptyList()
        _groups.value = emptyList()
        _error.value = null
    }

    companion object {
        @Volatile
        private var instance: ChannelRepository? = null

        fun getInstance(context: Context): ChannelRepository {
            return instance ?: synchronized(this) {
                instance ?: ChannelRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
