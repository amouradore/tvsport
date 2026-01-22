package com.acestream.tv.repository

import android.content.Context
import com.acestream.tv.model.Channel
import com.acestream.tv.model.ChannelGroup
import com.acestream.tv.model.StreamQuality
import java.security.MessageDigest

/**
 * Parser for M3U playlist files
 * 
 * Parses M3U files containing AceStream channels and extracts:
 * - Channel name
 * - Logo URL
 * - Group/Category
 * - AceStream content ID
 */
object M3UParser {

    private val EXTINF_PATTERN = Regex(
        """#EXTINF:-1\s+tvg-logo="([^"]*?)"\s+group-title="([^"]*?)"\s+tvg-id="([^"]*?)",\s*(.+)""",
        RegexOption.IGNORE_CASE
    )
    
    private val ACESTREAM_ID_PATTERN = Regex(
        """http://127\.0\.0\.1:6878/ace/getstream\?id=([a-f0-9]{40})""",
        RegexOption.IGNORE_CASE
    )
    
    private val QUALITY_PATTERNS = mapOf(
        StreamQuality.UHD to listOf("4k", "uhd", "2160p"),
        StreamQuality.FHD to listOf("fhd", "1080p", "1080"),
        StreamQuality.HD to listOf("hd", "720p", "720"),
        StreamQuality.SD to listOf("sd", "480p", "360p")
    )

    /**
     * Parse M3U content string into list of channels
     */
    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (line.startsWith("#EXTINF:")) {
                val match = EXTINF_PATTERN.find(line)
                
                if (match != null && i + 1 < lines.size) {
                    val urlLine = lines[i + 1].trim()
                    val idMatch = ACESTREAM_ID_PATTERN.find(urlLine)
                    
                    if (idMatch != null) {
                        val name = match.groupValues[4].trim()
                        val aceStreamId = idMatch.groupValues[1]
                        
                        channels.add(
                            Channel(
                                id = generateChannelId(aceStreamId, name),
                                name = name,
                                logoUrl = match.groupValues[1].takeIf { it.isNotBlank() },
                                groupTitle = match.groupValues[2].ifBlank { "Other" },
                                tvgId = match.groupValues[3].takeIf { it.isNotBlank() },
                                aceStreamId = aceStreamId,
                                quality = detectQuality(name)
                            )
                        )
                    }
                    i++
                }
            }
            i++
        }
        
        return channels
    }

    /**
     * Parse M3U file from assets folder
     */
    fun parseFromAssets(context: Context, fileName: String = "channels/canales_acestream.m3u"): List<Channel> {
        return try {
            val content = context.assets.open(fileName).bufferedReader().use { it.readText() }
            parse(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Group channels by their group title
     */
    fun groupChannels(channels: List<Channel>): List<ChannelGroup> {
        return channels
            .groupBy { it.groupTitle }
            .map { (groupName, groupChannels) ->
                ChannelGroup(
                    name = groupName,
                    channels = groupChannels.sortedBy { it.name }
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Get unique group names from channel list
     */
    fun getGroupNames(channels: List<Channel>): List<String> {
        return channels.map { it.groupTitle }.distinct().sorted()
    }

    /**
     * Filter channels by group
     */
    fun filterByGroup(channels: List<Channel>, groupName: String): List<Channel> {
        return if (groupName.isBlank() || groupName.equals("all", ignoreCase = true)) {
            channels
        } else {
            channels.filter { it.groupTitle.equals(groupName, ignoreCase = true) }
        }
    }

    /**
     * Search channels by name
     */
    fun searchChannels(channels: List<Channel>, query: String): List<Channel> {
        if (query.isBlank()) return channels
        
        val lowerQuery = query.lowercase()
        return channels.filter { channel ->
            channel.name.lowercase().contains(lowerQuery) ||
            channel.groupTitle.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Detect stream quality from channel name
     */
    private fun detectQuality(name: String): StreamQuality {
        val lowerName = name.lowercase()
        
        for ((quality, patterns) in QUALITY_PATTERNS) {
            if (patterns.any { lowerName.contains(it) }) {
                return quality
            }
        }
        
        return StreamQuality.UNKNOWN
    }

    /**
     * Generate unique channel ID from aceStreamId and name
     */
    private fun generateChannelId(aceStreamId: String, name: String): String {
        val input = "$aceStreamId-$name"
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
