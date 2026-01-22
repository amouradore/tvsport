package com.acestream.tv.acestream

import com.acestream.tv.acestream.EngineState

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.HttpURLConnection
import java.net.URL



class AceStreamManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AceStreamManager"
        private const val ENGINE_PORT = 6878
        private const val ENGINE_CHECK_INTERVAL = 2000L
        
        val ACESTREAM_PACKAGES = listOf(
            "org.acestream.media",
            "org.acestream.media.atv", 
            "org.acestream.core",
            "org.acestream.core.atv",
            "org.acestream.node"
        )

        @Volatile
        private var instance: AceStreamManager? = null

        fun getInstance(context: Context): AceStreamManager {
            return instance ?: synchronized(this) {
                instance ?: AceStreamManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkJob: Job? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _engineStatus = MutableStateFlow<EngineState>(EngineState.Stopped)
    val engineStatus: StateFlow<EngineState> = _engineStatus

    var onEngineReady: (() -> Unit)? = null
    var onEngineError: ((String) -> Unit)? = null

    fun isAceStreamInstalled(): Boolean {
        val pm = context.packageManager
        for (pkg in ACESTREAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                Log.d(TAG, "AceStream package found: $pkg")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Continue checking
            }
        }
        Log.d(TAG, "No AceStream package found")
        return false
    }

    fun getInstalledPackage(): String? {
        val pm = context.packageManager
        for (pkg in ACESTREAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // Continue
            }
        }
        return null
    }

    fun startEngine() {
        if (_engineStatus.value == EngineState.Running) {
            Log.d(TAG, "Engine already running")
            onEngineReady?.invoke()
            return
        }

        _engineStatus.value = EngineState.Starting
        
        // Start AceStream engine in BACKGROUND (without showing UI)
        val pkg = getInstalledPackage()
        if (pkg != null) {
            try {
                // Option 1: Send broadcast to start engine silently
                val broadcastIntent = android.content.Intent("org.acestream.action.START_ENGINE")
                broadcastIntent.setPackage(pkg)
                context.sendBroadcast(broadcastIntent)
                Log.d(TAG, "Sent START_ENGINE broadcast to: $pkg")
                
                // Option 2: Try to start the engine service directly (fallback)
                try {
                    val serviceIntent = android.content.Intent()
                    serviceIntent.setClassName(pkg, "org.acestream.engine.AceStreamEngineService")
                    serviceIntent.action = "org.acestream.engine.action.START"
                    context.startService(serviceIntent)
                    Log.d(TAG, "Started AceStream service directly: $pkg")
                } catch (e: Exception) {
                    Log.w(TAG, "Direct service start failed, relying on broadcast", e)
                }
                
                // Option 3: Alternative service names for different AceStream versions
                try {
                    val altServiceIntent = android.content.Intent()
                    altServiceIntent.setClassName(pkg, "org.acestream.node.AceStreamEngineService")
                    altServiceIntent.action = "org.acestream.action.START_ENGINE"
                    context.startService(altServiceIntent)
                } catch (e: Exception) {
                    // Silently ignore - broadcast should work
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AceStream engine in background", e)
            }
        }

        // Start checking for engine availability
        startEngineCheck()
    }

    private fun startEngineCheck() {
        checkJob?.cancel()
        checkJob = scope.launch {
            var attempts = 0
            val maxAttempts = 30 // 60 seconds max
            
            while (attempts < maxAttempts) {
                if (isEngineResponding()) {
                    _isReady.value = true
                    _engineStatus.value = EngineState.Running
                    withContext(Dispatchers.Main) {
                        onEngineReady?.invoke()
                    }
                    return@launch
                }
                delay(ENGINE_CHECK_INTERVAL)
                attempts++
            }
            
            _engineStatus.value = EngineState.Error("Engine timeout")
            withContext(Dispatchers.Main) {
                onEngineError?.invoke("AceStream engine did not start in time")
            }
        }
    }

    private fun isEngineResponding(): Boolean {
        return try {
            val url = URL("http://127.0.0.1:$ENGINE_PORT/webui/api/service?method=get_version")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    fun stopEngine() {
        checkJob?.cancel()
        _isReady.value = false
        _engineStatus.value = EngineState.Stopped
    }

    fun getStreamUrl(aceStreamId: String): String {
        // Use manifest.m3u8 endpoint for HLS playback in ExoPlayer
        return "http://127.0.0.1:$ENGINE_PORT/ace/manifest.m3u8?id=$aceStreamId"
    }
    
    /**
     * Get HLS stream URL (manifest.m3u8)
     */
    fun getHlsStreamUrl(aceStreamId: String): String {
        return "http://127.0.0.1:$ENGINE_PORT/ace/manifest.m3u8?id=$aceStreamId"
    }

    fun getEngineBaseUrl(): String {
        return "http://127.0.0.1:$ENGINE_PORT"
    }

    fun isEngineReady(): Boolean {
        return _isReady.value
    }

    fun release() {
        checkJob?.cancel()
        scope.cancel()
        _isReady.value = false
    }
}


