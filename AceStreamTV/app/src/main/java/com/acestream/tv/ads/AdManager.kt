package com.acestream.tv.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.model.AdPreferences
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdManager private constructor(context: Context) : 
    IUnityAdsInitializationListener, IUnityAdsLoadListener, IUnityAdsShowListener {

    companion object {
        private const val TAG = "AdManager"
        
        // Configuration
        private const val UNITY_GAME_ID = "6029626"
        private const val UNITY_INTERSTITIAL_ID = "Interstitial_Android"
        private const val UNITY_TEST_MODE = false // Production
        
        private const val STARTAPP_APP_ID = "200165325"
        
        private const val PREFS_NAME = "acestream_ads_prefs"
        private const val KEY_CLICK_COUNT = "click_count"
        private const val KEY_LAST_DATE = "last_click_date"
        
        // 2 minutes cooldown (120000 ms)
        private const val AD_COOLDOWN_MS = 120000L
        
        @Volatile
        private var instance: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastAdShowTime: Long = 0
    private var isUnityLoaded = false
    private var pendingCallback: (() -> Unit)? = null

    init {
        initializeUnity(context)
        initializeStartApp(context)
    }

    private fun initializeUnity(context: Context) {
        if (!UnityAds.isInitialized) {
            UnityAds.initialize(context, UNITY_GAME_ID, UNITY_TEST_MODE, this)
        } else {
            loadUnityAd()
        }
    }

    private fun initializeStartApp(context: Context) {
        try {
            StartAppSDK.init(context, STARTAPP_APP_ID, true)
            StartAppSDK.setTestAdsEnabled(UNITY_TEST_MODE)
        } catch (e: Exception) {
            Log.e(TAG, "StartApp init failed", e)
        }
    }

    private fun loadUnityAd() {
        UnityAds.load(UNITY_INTERSTITIAL_ID, this)
    }

    // --- Public API ---

    fun checkAndShowAd(activity: Activity, onComplete: () -> Unit) {
        val count = incrementAndGetClickCount()
        Log.d(TAG, "Click count: $count")

        // 1. Premier clic du jour -> Pas de pub
        if (count <= 1) {
            Log.d(TAG, "First click of the day - skipping ad")
            onComplete()
            return
        }

        // 2. Vérifier Cooldown
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAdShowTime < AD_COOLDOWN_MS) {
            Log.d(TAG, "Ad cooldown active (${(AD_COOLDOWN_MS - (currentTime - lastAdShowTime)) / 1000}s remaining) - skipping ad")
            onComplete()
            return
        }

        // 3. Tenter Unity Ads
        if (isUnityLoaded) {
            Log.d(TAG, "Showing Unity Ad")
            pendingCallback = onComplete
            UnityAds.show(activity, UNITY_INTERSTITIAL_ID, this)
            lastAdShowTime = System.currentTimeMillis()
            return
        }

        // 4. Fallback StartApp
        Log.d(TAG, "Unity not loaded, trying StartApp")
        showStartAppAd(activity, onComplete)
    }
    
    fun showSplashAd(activity: Activity) {
        // StartApp Splash logic or simply Interstitial
        // StartApp has a specific Splash API but Interstitial is simpler for custom logic
        val startAppAd = StartAppAd(activity)
        startAppAd.loadAd(com.startapp.sdk.adsbase.model.AdPreferences())
        startAppAd.showAd(object : AdDisplayListener {
            override fun adHidden(ad: com.startapp.sdk.adsbase.Ad) {}
            override fun adDisplayed(ad: com.startapp.sdk.adsbase.Ad) {}
            override fun adClicked(ad: com.startapp.sdk.adsbase.Ad) {}
            override fun adNotDisplayed(ad: com.startapp.sdk.adsbase.Ad) {}
        })
    }
    
    fun showBackToHomeAd(activity: Activity) {
        val startAppAd = StartAppAd(activity)
        startAppAd.showAd() // Auto-cached by SDK usually
    }

    private fun showStartAppAd(activity: Activity, onComplete: () -> Unit) {
        val startAppAd = StartAppAd(activity)
        
        // We use a listener to know when it is closed
        val listener = object : AdDisplayListener {
            override fun adHidden(ad: com.startapp.sdk.adsbase.Ad) {
                Log.d(TAG, "StartApp ad hidden")
                onComplete()
            }
            override fun adDisplayed(ad: com.startapp.sdk.adsbase.Ad) {
                lastAdShowTime = System.currentTimeMillis()
            }
            override fun adClicked(ad: com.startapp.sdk.adsbase.Ad) {}
            override fun adNotDisplayed(ad: com.startapp.sdk.adsbase.Ad) {
                Log.d(TAG, "StartApp ad not displayed")
                onComplete()
            }
        }

        if (!startAppAd.showAd(listener)) {
             Log.d(TAG, "StartApp showAd returned false")
             onComplete()
        }
    }

    private fun incrementAndGetClickCount(): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        
        var count = prefs.getInt(KEY_CLICK_COUNT, 0)
        
        if (lastDate != today) {
            count = 0
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .apply()
        }
        
        count++
        prefs.edit().putInt(KEY_CLICK_COUNT, count).apply()
        return count
    }

    // --- Unity Callbacks ---

    override fun onInitializationComplete() {
        Log.d(TAG, "Unity Ads Initialized")
        loadUnityAd()
    }

    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
        Log.e(TAG, "Unity Ads Init Failed: $error - $message")
    }

    override fun onUnityAdsAdLoaded(placementId: String) {
        Log.d(TAG, "Unity Ad Loaded: $placementId")
        if (placementId == UNITY_INTERSTITIAL_ID) {
            isUnityLoaded = true
        }
    }

    override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
        Log.e(TAG, "Unity Ad Failed Load: $placementId - $message")
        isUnityLoaded = false
    }

    override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
        Log.e(TAG, "Unity Ad Show Failed: $placementId - $message")
        isUnityLoaded = false
        // Fallback to StartApp if Unity fails to show
        pendingCallback?.let { callback ->
             // Note: We don't have activity here easily, but usually show failure happens fast.
             // Ideally we would try StartApp here but we need Activity context. 
             // For safety, just proceed to content.
             callback.invoke()
             pendingCallback = null
        }
        loadUnityAd() // Reload
    }

    override fun onUnityAdsShowStart(placementId: String) {
        Log.d(TAG, "Unity Ad Show Start")
    }

    override fun onUnityAdsShowClick(placementId: String) {
        Log.d(TAG, "Unity Ad Clicked")
    }

    override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
        Log.d(TAG, "Unity Ad Show Complete")
        isUnityLoaded = false
        pendingCallback?.invoke()
        pendingCallback = null
        loadUnityAd() // Preload next
    }
}
