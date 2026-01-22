package com.acestream.tv.acestream

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AceStream Engine API Client
 * 
 * Provides communication with AceStream Engine via:
 * 1. Embedded HTTP API (localhost:6878)
 * 2. External AceStream app via Intent (fallback)
 * 
 * The Engine exposes a RESTful API on http://127.0.0.1:6878/
 */
class AceStreamEngineApi(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Critical: Limit connection pool to prevent socket exhaustion
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        // Reuse connections aggressively
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 6878
        
        // API Endpoints
        const val ENDPOINT_VERSION = "/webui/api/service?method=get_version"
        const val ENDPOINT_GET_STREAM = "/ace/getstream"
        const val ENDPOINT_MANIFEST = "/ace/manifest.m3u8"
        const val ENDPOINT_STAT = "/webui/api/service?method=get_engine_status"
        
        // External AceStream packages (priority order)
        // Note: Different distributions/devices use different package names.
        val ACESTREAM_PACKAGES = listOf(
            "org.acestream.node",          // Official
            "org.acestream.engine",        // Some builds use this
            "org.acestream.engine.atv",    // ATV engine variants
            "org.acestream.media",         // Ace Player / Media
            "org.acestream.media.atv",
            "com.acestream.media.atv.engine",
            "org.acestream.core",
            "org.acestream.core.atv"       // Seen on some APKMirror builds
        )
        
        const val PLAY_STORE_PACKAGE = "org.acestream.node"
    }

    private var engineHost = DEFAULT_HOST
    private var enginePort = DEFAULT_PORT
    
    /**
     * Configure engine connection parameters
     */
    fun configure(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT) {
        engineHost = host
        enginePort = port
    }

    /**
     * Get the base URL for engine API
     */
    fun getBaseUrl(): String = "http://$engineHost:$enginePort"

    /**
     * Check if engine is running and responding
     */
    suspend fun isEngineRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}$ENDPOINT_VERSION")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get engine version
     */
    suspend fun getEngineVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${getBaseUrl()}$ENDPOINT_VERSION")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "{}")
                    json.optJSONObject("result")?.optString("version")
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get stream URL for an AceStream content ID
     * 
     * @param contentId 40-character hex ID
     * @return HTTP URL for streaming
     */
    fun getStreamUrl(contentId: String): String {
        return "${getBaseUrl()}$ENDPOINT_GET_STREAM?id=$contentId"
    }

    /**
     * Get HLS manifest URL for an AceStream content ID
     * (Alternative format, better for some players)
     */
    fun getHlsUrl(contentId: String): String {
        return "${getBaseUrl()}$ENDPOINT_MANIFEST?id=$contentId"
    }

    /**
     * Get stream status/statistics
     */
    suspend fun getStreamStat(contentId: String): StreamStats? = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/ace/getstat?id=$contentId"
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val json = JSONObject(it.body?.string() ?: "{}")
                    StreamStats(
                        status = json.optString("status"),
                        peers = json.optInt("peers", 0),
                        speedDown = json.optLong("speed_down", 0),
                        speedUp = json.optLong("speed_up", 0),
                        downloaded = json.optLong("downloaded", 0),
                        uploaded = json.optLong("uploaded", 0)
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Wait for engine to become ready (with timeout)
     */
    suspend fun waitForEngine(timeoutMs: Long = 30000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isEngineRunning()) return true
            delay(500)
        }
        return false
    }

    /**
     * Stop a stream
     */
    suspend fun stopStream(contentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/ace/stop?id=$contentId"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ========================
    // External App Integration
    // ========================

    /**
     * Check if any AceStream app is installed
     */
    fun isAceStreamInstalled(): Boolean {
        val pm = context.packageManager
        Log.d("AceStreamApi", "isAceStreamInstalled: Checking packages...")

        // 1) Direct package check
        val hasKnownPackage = ACESTREAM_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                Log.d("AceStreamApi", "isAceStreamInstalled: FOUND $pkg")
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                Log.e("AceStreamApi", "isAceStreamInstalled: Error checking $pkg", e)
                false
            }
        }
        if (hasKnownPackage) return true

        // 2) Fallback: can we resolve acestream:// links?
        // This covers devices where AceStream/AcePlayer is installed under an unexpected package name.
        return try {
            val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("acestream://test"))
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(testIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(testIntent, 0)
            }
            val ok = resolved != null
            Log.d("AceStreamApi", "isAceStreamInstalled: intent-resolve(acestream://)=$ok")
            ok
        } catch (e: Exception) {
            Log.e("AceStreamApi", "isAceStreamInstalled: intent-resolve check failed", e)
            false
        }
    }

    /**
     * Get installed AceStream package name
     */
    fun getInstalledAceStreamPackage(): String? {
        val pm = context.packageManager
        
        // First: known packages
        val known = ACESTREAM_PACKAGES.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (known != null) return known

        // Fallback: resolve intent
        return try {
            val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("acestream://test"))
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(testIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(testIntent, 0)
            }
            resolved?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Start external AceStream app for content
     */
    fun startExternalPlayer(contentId: String): Boolean {
        val aceStreamUri = Uri.parse("acestream://$contentId")
        
        val intent = Intent(Intent.ACTION_VIEW, aceStreamUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Request AceStream engine to start via broadcast
     * (Works with some AceStream versions)
     */
    fun requestEngineStart() {
        val intent = Intent("org.acestream.action.START_ENGINE")
        context.sendBroadcast(intent)
    }

    /**
     * Open Play Store for AceStream installation
     */
    fun openAceStreamDownload() {
        val playStoreUri = Uri.parse("market://details?id=$PLAY_STORE_PACKAGE")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE")
        
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

/**
 * Stream statistics data class
 */
data class StreamStats(
    val status: String,
    val peers: Int,
    val speedDown: Long,  // bytes/sec
    val speedUp: Long,    // bytes/sec
    val downloaded: Long, // total bytes
    val uploaded: Long    // total bytes
) {
    fun getSpeedDownFormatted(): String {
        return when {
            speedDown >= 1_000_000 -> "${speedDown / 1_000_000} MB/s"
            speedDown >= 1_000 -> "${speedDown / 1_000} KB/s"
            else -> "$speedDown B/s"
        }
    }
}
