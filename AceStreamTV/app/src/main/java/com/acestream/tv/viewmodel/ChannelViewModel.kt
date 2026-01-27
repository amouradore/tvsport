package com.acestream.tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acestream.tv.acestream.AceStreamManager
import com.acestream.tv.acestream.EngineState
import com.acestream.tv.model.Channel
import com.acestream.tv.model.ChannelGroup
import com.acestream.tv.repository.ChannelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for channel list screen
 */
class ChannelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChannelRepository.getInstance(application)
    private val aceStreamManager = AceStreamManager.getInstance(application)

    // Channel data
    val channels: StateFlow<List<Channel>> = repository.channels
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val groups: StateFlow<List<ChannelGroup>> = repository.groups
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val isLoading: StateFlow<Boolean> = repository.isLoading
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    val error: StateFlow<String?> = repository.error
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Filter state
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Engine state
    val engineStatus: StateFlow<EngineState> = aceStreamManager.engineStatus

    // Priority channels to show at top
    private val PRIORITY_CHANNELS = listOf(
        "TNT Sport 1",
        "Sky Sport Premier League",
        "M.L. Campeones 1",
        "Bein Sports 1"
    )

    // Filtered channels based on group and search
    val filteredChannels: StateFlow<List<Channel>> = combine(
        channels,
        _selectedGroup,
        _searchQuery
    ) { allChannels, group, query ->
        var result = allChannels
        
        // Filter by group
        if (!group.isNullOrBlank()) {
            result = result.filter { it.groupTitle.equals(group, ignoreCase = true) }
        }
        
        // Filter by search query
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { channel ->
                channel.name.lowercase().contains(lowerQuery) ||
                channel.groupTitle.lowercase().contains(lowerQuery)
            }
        }

        // Apply Priority Sort and Randomization
        if (result.isNotEmpty()) {
            val priorityList = mutableListOf<Channel>()
            val remainingList = mutableListOf<Channel>()
            
            result.forEach { channel ->
                val isPriority = PRIORITY_CHANNELS.any { priority -> 
                    channel.name.contains(priority, ignoreCase = true) 
                }
                if (isPriority) {
                    priorityList.add(channel)
                } else {
                    remainingList.add(channel)
                }
            }
            
            // Sort priority list to match the order in PRIORITY_CHANNELS
            priorityList.sortWith(compareBy { channel ->
                PRIORITY_CHANNELS.indexOfFirst { channel.name.contains(it, ignoreCase = true) }
            })
            
            // Priority first, the rest shuffled to avoid similar names together
            result = if (query.isBlank()) {
                // Shuffle only when not searching to keep UI stable during typing
                priorityList + remainingList.shuffled()
            } else {
                // When searching, just show results (priority first)
                priorityList + remainingList
            }
        }
        
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Group names for filter chips
    val groupNames: StateFlow<List<String>> = channels.map { channelList ->
        channelList.map { it.groupTitle }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadChannels()
    }

    /**
     * Load channels from assets
     */
    fun loadChannels() {
        viewModelScope.launch {
            repository.loadFromAssets()
        }
    }

    /**
     * Load channels from remote URL
     */
    fun loadChannelsFromUrl(url: String) {
        viewModelScope.launch {
            repository.loadFromUrl(url)
        }
    }

    /**
     * Set the group filter
     */
    fun setGroupFilter(groupName: String?) {
        _selectedGroup.value = groupName
    }

    /**
     * Clear the group filter
     */
    fun clearGroupFilter() {
        _selectedGroup.value = null
    }

    /**
     * Set search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Get channel by ID
     */
    fun getChannel(channelId: String): Channel? {
        return repository.getChannelById(channelId)
    }

    /**
     * Start AceStream engine
     */
    fun startEngine() {
        aceStreamManager.startEngine()
    }

    /**
     * Stop AceStream engine
     */
    fun stopEngine() {
        aceStreamManager.stopEngine()
    }

    override fun onCleared() {
        super.onCleared()
        // Don't stop the engine on ViewModel clear, let the service manage itself
    }
}
