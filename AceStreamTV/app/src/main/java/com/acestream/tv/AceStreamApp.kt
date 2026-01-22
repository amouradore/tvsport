package com.acestream.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class for AceStream TV
 * Initializes notification channels and global resources
 */
class AceStreamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Setup Crash Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = java.io.File(getExternalFilesDir(null), "crash_log.txt")
                val writer = java.io.PrintWriter(file)
                throwable.printStackTrace(writer)
                writer.flush()
                writer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Re-throw to let the app crash normally (or kill process)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        createNotificationChannels()
        
        // Initialize Ads
        com.acestream.tv.ads.AdManager.getInstance(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelEngine = NotificationChannel(
                CHANNEL_ENGINE,
                getString(R.string.notification_channel_engine),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_engine_desc)
                setShowBadge(false)
            }

            val channelPlayback = NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_playback_desc)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channelEngine)
            notificationManager.createNotificationChannel(channelPlayback)
        }
    }

    companion object {
        const val CHANNEL_ENGINE = "acestream_engine"
        const val CHANNEL_PLAYBACK = "acestream_playback"
        
        lateinit var instance: AceStreamApp
            private set
    }
}
