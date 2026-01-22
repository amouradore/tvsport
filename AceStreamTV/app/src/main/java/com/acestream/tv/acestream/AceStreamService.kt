package com.acestream.tv.acestream

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.acestream.tv.AceStreamApp
import com.acestream.tv.R
import com.acestream.tv.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground service that manages the AceStream Engine lifecycle
 * 
 * This service acts as a wrapper for the AceStream Engine, providing:
 * - Engine initialization and lifecycle management
 * - HTTP server health monitoring
 * - Stream URL generation
 */
class AceStreamService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _engineStatus = MutableStateFlow<EngineState>(EngineState.Stopped)
    val engineStatus: StateFlow<EngineState> = _engineStatus
    
    private var healthCheckJob: Job? = null

    // Prevent starting embedded engine multiple times in parallel
    private val embeddedStartInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    // Embedded engine process (Option A: ProcessBuilder)
    @Volatile private var embeddedProcess: Process? = null
    
    // AceStream Engine configuration
    private val enginePort = 6878
    private val engineHost = "127.0.0.1"
    
    inner class LocalBinder : Binder() {
        fun getService(): AceStreamService = this@AceStreamService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ENGINE -> startEngine()
            ACTION_STOP_ENGINE -> stopEngine()
        }
        return START_STICKY
    }

    /**
     * Start the AceStream Engine
     * 
     * Integration modes:
     * 1. EMBEDDED: Load native libraries (if available in jniLibs)
     * 2. EXTERNAL: Use installed AceStream app via HTTP API
     */
    private fun startEngine() {
        if (_engineStatus.value == EngineState.Running) return
        
        _engineStatus.value = EngineState.Starting
        
        serviceScope.launch {
            try {
                // Prefer embedded engine when assets are bundled
                if (isEmbeddedEngineAvailable(this@AceStreamService)) {
                    startEmbeddedEngine()
                } else {
                    // Fallback to external AceStream app
                    startExternalEngine()
                }
            } catch (e: Exception) {
                _engineStatus.value = EngineState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Option A does not use libpyembedded (it forks and crashes Binder on modern Android).
     * We start the embedded engine using a real child process (ProcessBuilder) running a bundled python binary.
     */
    
    /**
     * Start the embedded AceStream engine (native libraries)
     */
    private suspend fun startEmbeddedEngine() = withContext(Dispatchers.IO) {
        // Idempotency guard: avoid multiple simultaneous Python starts
        if (!embeddedStartInProgress.compareAndSet(false, true)) {
            android.util.Log.w("AceStreamService", "startEmbeddedEngine: already starting/started, skipping")
            return@withContext
        }

        val aceDir = java.io.File(filesDir, "acestream")
        val readyMarker = java.io.File(aceDir, ".ready_v2")

        try {
            updateNotification("Initializing engine assets (this may take a minute)...")

            // Extract if not ready OR if previous extraction is corrupted
            val expectedMain = java.io.File(aceDir, "main.py")
            val expectedPython = java.io.File(aceDir, "python/bin/python")

            if (!readyMarker.exists() || !expectedMain.exists() || !expectedPython.exists()) {
                android.util.Log.w(
                    "AceStreamService",
                    "Re-extracting assets: ready=${readyMarker.exists()} main=${expectedMain.exists()} python=${expectedPython.exists()}"
                )
                aceDir.deleteRecursively()
                aceDir.mkdirs()
                unpackAndExtractAssets(aceDir)
                readyMarker.createNewFile()
            }

            delay(500)

            updateNotification("Starting Python Engine...")

            // main.py is the real engine bootstrap (it uses app_bridge.py internally for Android RPC)
            val entryPoint = java.io.File(aceDir, "main.py")
            if (!entryPoint.exists()) {
                throw java.io.FileNotFoundException("Entry point not found: ${entryPoint.absolutePath}")
            }

            // Configure environment for embedded Python.
            // Required because Python libs (.so) are extracted into internal storage, not in nativeLibraryDir.
            try {
                val pythonHome = java.io.File(aceDir, "python")
                val pythonLib = java.io.File(pythonHome, "lib")
                val aceLibDir = java.io.File(aceDir, "acestreamengine")

                android.system.Os.setenv("PYTHONHOME", pythonHome.absolutePath, true)
                android.system.Os.setenv("PYTHONPATH", pythonLib.absolutePath, true)

                // AceStream Android bridge server config (required by app_bridge.py)
                android.system.Os.setenv("AP_HOST", engineHost, true)
                android.system.Os.setenv("AP_PORT", enginePort.toString(), true)

                // Let dlopen find libcrypto/libssl and python extension modules extracted from zip.
                val ldPath = listOf(pythonLib.absolutePath, aceLibDir.absolutePath).joinToString(":")
                android.system.Os.setenv("LD_LIBRARY_PATH", ldPath, true)

                android.util.Log.d(
                    "AceStreamService",
                    "Embedded env set: PYTHONHOME=${pythonHome.absolutePath}, PYTHONPATH=${pythonLib.absolutePath}, LD_LIBRARY_PATH=$ldPath"
                )
            } catch (e: Throwable) {
                android.util.Log.w("AceStreamService", "Failed to set embedded python env vars", e)
            }

            // Extract bundled python binary (asset: assets/acestream/python)
            val pythonBin = java.io.File(java.io.File(aceDir, "python/bin"), "python")
            if (!pythonBin.exists()) {
                pythonBin.parentFile?.mkdirs()
                assets.open("acestream/python/bin/python").use { input ->
                    java.io.FileOutputStream(pythonBin).use { out -> input.copyTo(out) }
                }
            }

            // Ensure executable bit on every start (some devices copy with 0600)
            try {
                android.system.Os.chmod(pythonBin.absolutePath, 0x1ED) // 0755
            } catch (t: Throwable) {
                android.util.Log.w("AceStreamService", "Os.chmod python failed", t)
            }
            if (!pythonBin.setExecutable(true, false)) {
                android.util.Log.w("AceStreamService", "setExecutable(true) returned false for ${pythonBin.absolutePath}")
            }
            android.util.Log.d("AceStreamService", "pythonBin perms: canExec=${pythonBin.canExecute()} size=${pythonBin.length()}")

            // Start python as a real child process (no fork inside app process)
            val pb = ProcessBuilder(listOf(pythonBin.absolutePath, entryPoint.absolutePath))
            pb.directory(aceDir)

            val env = pb.environment()
            val pythonHome = java.io.File(aceDir, "python")
            val pythonLib = java.io.File(pythonHome, "lib")
            val aceLibDir = java.io.File(aceDir, "acestreamengine")

            env["PYTHONHOME"] = pythonHome.absolutePath
            env["PYTHONPATH"] = pythonLib.absolutePath
            env["AP_HOST"] = engineHost
            env["AP_PORT"] = enginePort.toString()
            env["HOME"] = aceDir.absolutePath

            // Ensure child process can find native deps shipped in APK (nativeLibraryDir)
            val nativeLibDir = applicationInfo.nativeLibraryDir
            env["LD_LIBRARY_PATH"] = listOf(pythonLib.absolutePath, aceLibDir.absolutePath, nativeLibDir).joinToString(":")

            pb.redirectErrorStream(true)

            android.util.Log.d("AceStreamService", "Starting embedded process: ${pythonBin.absolutePath} ${entryPoint.absolutePath}")
            embeddedProcess = pb.start()

            // Wait for engine to spin up: poll HTTP endpoint instead of fixed sleep.
            var ok = false
            var lastErr: String? = null
            val deadline = System.currentTimeMillis() + 45_000
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (checkEngineHealth()) {
                        ok = true
                        break
                    }
                } catch (t: Throwable) {
                    lastErr = t.message
                }
                delay(1000)
            }

            if (ok) {
                android.util.Log.d("AceStreamService", "Embedded engine is responding on $engineHost:$enginePort")
                startHealthCheck()
                _engineStatus.value = EngineState.Running
                updateNotification("Engine running (Embedded)")
            } else {
                val msg = "Embedded engine failed to respond on $engineHost:$enginePort" + (lastErr?.let { ": $it" } ?: "")
                android.util.Log.e("AceStreamService", msg)
                _engineStatus.value = EngineState.Error(msg)
                updateNotification("Engine failed (Embedded)")
            }
        } catch (e: Exception) {
            _engineStatus.value = EngineState.Error("Start failed: ${e.message}")
            e.printStackTrace()
        } finally {
            // Allow re-attempt after the attempt finishes (success or failure).
            embeddedStartInProgress.set(false)
        }
    }

    private fun unpackAndExtractAssets(targetDir: java.io.File) {
        try {
            copyAssetsRecursively("acestream", targetDir)
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }
    }

    private fun copyAssetsRecursively(assetPath: String, targetDir: java.io.File) {
        val children = assets.list(assetPath) ?: return
        for (name in children) {
            val childPath = "$assetPath/$name"

            // Robust detection: assets.list() returns empty array both for files and empty dirs.
            // So try opening the asset; if it opens -> it's a file. If it throws -> it's a directory.
            val isFile = try {
                assets.open(childPath).close()
                true
            } catch (_: java.io.IOException) {
                false
            }

            if (!isFile) {
                val outDir = java.io.File(targetDir, name)
                outDir.mkdirs()
                copyAssetsRecursively(childPath, outDir)
                continue
            }

            // File
            if (name.endsWith(".zip")) {
                assets.open(childPath).use { input ->
                    unzipStream(input, targetDir)
                }
            } else {
                assets.open(childPath).use { input ->
                    val outFile = java.io.File(targetDir, name)
                    outFile.parentFile?.mkdirs()
                    java.io.FileOutputStream(outFile).use { out -> input.copyTo(out) }
                }
            }
        }
    }

    private fun unzipStream(inputStream: java.io.InputStream, targetDir: java.io.File) {
        val zipInputStream = java.util.zip.ZipInputStream(java.io.BufferedInputStream(inputStream))
        var zipEntry = zipInputStream.nextEntry
        val buffer = ByteArray(8192)

        while (zipEntry != null) {
            val validName = zipEntry.name.replace("..", "") // Security check
            val newFile = java.io.File(targetDir, validName)
            
            if (zipEntry.isDirectory) {
                newFile.mkdirs()
            } else {
                newFile.parentFile?.mkdirs()
                val out = java.io.FileOutputStream(newFile)
                var len: Int
                while (zipInputStream.read(buffer).also { len = it } > 0) {
                    out.write(buffer, 0, len)
                }
                out.close()
            }
            zipEntry = zipInputStream.nextEntry
        }
        zipInputStream.close()
    }

    
    /**
     * Start using external AceStream app via HTTP API (with AIDL binding)
     */
    private suspend fun startExternalEngine() {
        android.util.Log.d("AceStreamService", "startExternalEngine: Binding to AceStream...")
        updateNotification("Starting AceStream Engine...")
        
        val client = AceStreamEngineClient(this)
        val bound = client.bind()
        
        if (bound) {
             android.util.Log.d("AceStreamService", "startExternalEngine: Service bound successfully")
             // Wait a bit for the engine to initialize its HTTP server
             val api = AceStreamEngineApi(this@AceStreamService)
             val engineReady = api.waitForEngine(timeoutMs = 20000)
             
             if (engineReady) {
                 val version = api.getEngineVersion()
                 android.util.Log.d("AceStreamService", "Engine ready: v$version")
                 startHealthCheck()
                 _engineStatus.value = EngineState.Running
                 updateNotification("Engine running")
             } else {
                 _engineStatus.value = EngineState.Error("Engine bound but HTTP not ready")
             }
        } else {
            android.util.Log.e("AceStreamService", "Failed to bind to AceStream service")
            // Fallback: Try to start activity if binding fails (old method)
            val api = AceStreamEngineApi(this@AceStreamService)
            if (api.isAceStreamInstalled()) {
                 val intent = packageManager.getLaunchIntentForPackage("org.acestream.node")
                 if (intent != null) {
                     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                     startActivity(intent)
                 }
            } else {
                _engineStatus.value = EngineState.Error("AceStream not installed")
            }
        }
    }


    /**
     * Stop the AceStream Engine
     */
    private fun stopEngine() {
        healthCheckJob?.cancel()
        _engineStatus.value = EngineState.Stopped

        // Stop embedded process (Option A)
        embeddedProcess?.destroy()
        embeddedProcess = null
        embeddedStartInProgress.set(false)
        
        serviceScope.launch {
            // In production, this would call native methods to stop the engine
            delay(500)
        }
    }

    /**
     * Generate stream URL for a given AceStream content ID
     * Using HLS manifest for better compatibility
     */
    fun getStreamUrl(aceStreamId: String): String {
        val url = "http://$engineHost:$enginePort/ace/manifest.m3u8?id=$aceStreamId"
        android.util.Log.d("AceStreamService", "Generated stream URL: $url")
        return url
    }

    /**
     * Check if the engine is ready to handle requests
     */
    fun isEngineReady(): Boolean {
        return _engineStatus.value == EngineState.Running
    }

    /**
     * Get the engine HTTP server base URL
     */
    fun getEngineBaseUrl(): String {
        return "http://$engineHost:$enginePort"
    }

    /**
     * Periodically check if the engine HTTP server is responding
     */
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    val isHealthy = checkEngineHealth()
                    if (!isHealthy && _engineStatus.value == EngineState.Running) {
                        _engineStatus.value = EngineState.Error("Engine not responding")
                    }
                } catch (e: Exception) {
                    // Ignore health check errors
                }
                delay(HEALTH_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Check if the engine HTTP server is responding
     */
    private suspend fun checkEngineHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$engineHost:$enginePort/webui/api/service?method=get_version")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == 200
        } catch (e: IOException) {
            false
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AceStreamApp.CHANNEL_ENGINE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.engine_starting))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, AceStreamApp.CHANNEL_ENGINE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopEngine()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_ENGINE = "com.acestream.tv.START_ENGINE"
        const val ACTION_STOP_ENGINE = "com.acestream.tv.STOP_ENGINE"
        private const val HEALTH_CHECK_INTERVAL = 10_000L // 10 seconds

        /**
         * Detect if the embedded engine assets/libs are bundled with this APK.
         * This is used to avoid showing the "install AceStream" dialog when we ship the engine.
         */
        fun isEmbeddedEngineAvailable(context: android.content.Context): Boolean {
            return try {
                val hasAssets = context.assets.list("acestream")?.any { it.endsWith(".zip") } == true
                val libDir = context.applicationInfo.nativeLibraryDir ?: ""
                // For Option A we need the python binary bundled in assets.
                val hasPython = context.assets.list("acestream/python")?.contains("bin") == true
                hasAssets && hasPython
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Sealed class representing engine states
 */
sealed class EngineState {
    object Stopped : EngineState()
    object Starting : EngineState()
    object Running : EngineState()
    data class Error(val message: String) : EngineState()
}
