package com.acestream.tv.ui

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.acestream.tv.R
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.acestream.tv.acestream.AceStreamEngineApi
import com.acestream.tv.acestream.AceStreamManager
import android.content.pm.PackageInstaller
import com.acestream.tv.acestream.EngineState
import com.acestream.tv.databinding.ActivityMainBinding
import com.acestream.tv.model.Channel
import com.acestream.tv.ui.adapter.ChannelAdapter
import com.acestream.tv.ui.adapter.GroupChipAdapter
import com.acestream.tv.ui.player.PlayerActivity
import com.acestream.tv.ui.settings.SettingsActivity
import com.acestream.tv.viewmodel.ChannelViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.text.Spanned
import android.widget.Button

/**
 * Main activity for phone/tablet
 * Displays channel grid with group filtering and search
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChannelViewModel by viewModels()

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var groupAdapter: GroupChipAdapter
    private lateinit var aceStreamManager: AceStreamManager

    // XAPK install flow state
    private var pendingInstallAfterUnknownSources: Boolean = false
    private var xapkInstallInProgress: Boolean = false
    
    // Flag pour éviter de relancer l'installation en boucle
    private var installationAttempted: Boolean = false
    
    // Flag pour afficher pub au retour du player
    private var shouldShowAdOnResume: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        
        aceStreamManager = AceStreamManager.getInstance(this)
        
        setupRecyclerView()
        setupGroupChips()
        setupSwipeRefresh()
        setupSetupOverlay()
        setupSetupOverlay()
        observeViewModel()

        findViewById<View>(R.id.btnMatches)?.setOnClickListener {
             startActivity(Intent(this, MatchesActivity::class.java))
        }
        
        findViewById<View>(R.id.btnOtherMatches)?.setOnClickListener {
             startActivity(Intent(this, OtherMatchesActivity::class.java))
        }
        
        // Check AceStream installation and start engine
        checkAceStreamAndStart()
        
        // Handle potential installation result from intent
        handleInstallationIntent(intent)
        
        // Show Splash Ad
        com.acestream.tv.ads.AdManager.getInstance(this).showSplashAd(this)

        // Setup LIVE blinking animation
        setupLiveBlinking()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleInstallationIntent(intent)
    }
    
    private fun handleInstallationIntent(intent: Intent?) {
        if (intent?.action == "PACKAGE_INSTALLED") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            Log.d(TAG, "handleInstallationIntent: status=$status")
            
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    Log.d(TAG, "handleInstallationIntent: Launching user action intent")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirmIntent != null) {
                        startActivity(confirmIntent)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d(TAG, "handleInstallationIntent: Installation SUCCESS")
                    xapkInstallInProgress = false
                    installationAttempted = false
                    hideSetupOverlay()
                    Snackbar.make(binding.root, getString(R.string.acestream_installed_success), Snackbar.LENGTH_LONG).show()
                    // Attendre un peu puis démarrer le moteur
                    lifecycleScope.launch {
                        delay(1000)
                        startAceStreamEngine()
                    }
                }
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.STATUS_FAILURE_ABORTED,
                PackageInstaller.STATUS_FAILURE_STORAGE,
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                    xapkInstallInProgress = false
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.e(TAG, "handleInstallationIntent: Installation FAILURE: $msg")
                    Snackbar.make(binding.root, getString(R.string.acestream_install_failed, msg ?: ""), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun checkAceStreamAndStart() {
        Log.d(TAG, "checkAceStreamAndStart: begin")
        
        // Vérifier si AceStream est installé via AceStreamManager
        val isInstalled = aceStreamManager.isAceStreamInstalled()
        Log.d(TAG, "checkAceStreamAndStart: isInstalled=$isInstalled")
        
        if (isInstalled) {
            // AceStream est installé, cacher l'overlay et démarrer le moteur
            hideSetupOverlay()
            startAceStreamEngine()
        } else {
            // AceStream n'est pas installé, vérifier les permissions et proposer l'installation
            val canInstallUnknown = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O 
                || packageManager.canRequestPackageInstalls()
            
            if (!canInstallUnknown) {
                showPermissionStep()
            } else if (!installationAttempted) {
                showInstallStep()
            }
        }
    }
    
    private fun startAceStreamEngine() {
        Log.d(TAG, "startAceStreamEngine: starting engine")
        
        aceStreamManager.onEngineReady = {
            Log.d(TAG, "Engine ready!")
            runOnUiThread {
                Snackbar.make(binding.root, getString(R.string.engine_connected), Snackbar.LENGTH_SHORT).show()
            }
        }
        
        aceStreamManager.onEngineError = { error ->
            Log.e(TAG, "Engine error: $error")
            runOnUiThread {
                Snackbar.make(binding.root, getString(R.string.engine_error_msg, error), Snackbar.LENGTH_LONG).show()
            }
        }
        
        try {
            aceStreamManager.startEngine()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start engine", e)
            Snackbar.make(binding.root, getString(R.string.error_msg, e.message ?: ""), Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun setupSetupOverlay() {
        // Le bouton sera configuré dynamiquement selon l'étape
    }
    
    private fun showPermissionStep() {
        Log.d(TAG, "showPermissionStep")
        binding.setupOverlay.visibility = View.VISIBLE
        binding.setupTitle.text = getString(R.string.engine_required_title)
        binding.setupMessage.text = getString(R.string.step1_permission)
        binding.setupActionButton.text = getString(R.string.btn_authorize)
        binding.setupProgress.visibility = View.GONE
        
        binding.setupActionButton.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            pendingInstallAfterUnknownSources = true
            startActivity(intent)
        }
    }
    
    private fun showInstallStep() {
        Log.d(TAG, "showInstallStep")
        binding.setupOverlay.visibility = View.VISIBLE
        binding.setupTitle.text = getString(R.string.engine_required_title)
        binding.setupMessage.text = getString(R.string.step2_install)
        binding.setupActionButton.text = getString(R.string.btn_install_acestream)
        binding.setupProgress.visibility = View.GONE
        
        binding.setupActionButton.setOnClickListener {
            binding.setupProgress.visibility = View.VISIBLE
            installationAttempted = true
            installAceStreamFromXapk()
        }
    }
    
    private fun hideSetupOverlay() {
        Log.d(TAG, "hideSetupOverlay")
        binding.setupOverlay.visibility = View.GONE
        binding.recyclerViewChannels.visibility = View.VISIBLE
        binding.recyclerViewGroups.visibility = View.VISIBLE
        binding.engineStatusBar.visibility = View.VISIBLE
    }

        private fun installAceStreamFromXapk() {
        if (xapkInstallInProgress) {
            Log.d(TAG, "installAceStreamFromXapk: already in progress, skipping")
            return
        }

        val canInstall = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
        Log.d(TAG, "installAceStreamFromXapk: starting (canRequestPackageInstalls=$canInstall)")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "installAceStreamFromXapk: No permission to install. Redirecting to settings.")
                pendingInstallAfterUnknownSources = true
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    xapkInstallInProgress = true
                    binding.setupProgress.visibility = View.VISIBLE
                    binding.setupActionButton.isEnabled = false
                    binding.setupActionButton.text = "Installation..."
                    binding.setupMessage.text = "Préparation de l'installation..."
                }

                val preferredSplit = when {
                    android.os.Build.SUPPORTED_ABIS.any { it.contains("arm64") } -> "config.arm64_v8a.apk"
                    else -> "config.arm64_v8a.apk"
                }

                // Copy XAPK to temp file first, then use ZipFile
                val tempDir = java.io.File(cacheDir, "xapk_temp")
                tempDir.mkdirs()
                
                withContext(Dispatchers.Main) {
                    binding.setupMessage.text = "Extraction des fichiers..."
                }
                
                val xapkFile = java.io.File(tempDir, "acestream.xapk")
                assets.open("xapk/acestream.xapk").use { input ->
                    java.io.FileOutputStream(xapkFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "XAPK copied to temp: ${xapkFile.length()} bytes")

                var baseApkFile: java.io.File? = null
                var splitApkFile: java.io.File? = null

                // Use ZipFile instead of ZipInputStream for proper random access
                java.util.zip.ZipFile(xapkFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val name = entry.name
                        when (name) {
                            "org.acestream.node.apk" -> {
                                Log.d(TAG, "Extracting base apk: $name (size=${entry.size})")
                                baseApkFile = java.io.File(tempDir, "base.apk")
                                zip.getInputStream(entry).use { input ->
                                    java.io.FileOutputStream(baseApkFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            preferredSplit -> {
                                Log.d(TAG, "Extracting split apk: $name (size=${entry.size})")
                                splitApkFile = java.io.File(tempDir, "split.apk")
                                zip.getInputStream(entry).use { input ->
                                    java.io.FileOutputStream(splitApkFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }

                if (baseApkFile == null || !baseApkFile!!.exists() || baseApkFile!!.length() == 0L) {
                    throw Exception("Base APK not found or empty in XAPK")
                }
                
                withContext(Dispatchers.Main) {
                    binding.setupMessage.text = "Finalisation..."
                }

                val packageInstaller = packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                // Write base APK with exact size
                java.io.FileInputStream(baseApkFile).use { input ->
                    session.openWrite("base.apk", 0, baseApkFile!!.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                Log.d(TAG, "Base APK written to session")

                // Write split APK if available
                if (splitApkFile != null && splitApkFile!!.exists() && splitApkFile!!.length() > 0) {
                    java.io.FileInputStream(splitApkFile).use { input ->
                        session.openWrite("split.apk", 0, splitApkFile!!.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    Log.d(TAG, "Split APK written to session")
                }

                val intent = Intent(this@MainActivity, MainActivity::class.java).apply { action = "PACKAGE_INSTALLED" }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this@MainActivity,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )

                Log.d(TAG, "installAceStreamFromXapk: committing")
                session.commit(pendingIntent.intentSender)
                session.close()

                // Cleanup temp files (keep xapk for potential retry)
                baseApkFile?.delete()
                splitApkFile?.delete()

                withContext(Dispatchers.Main) {
                    binding.setupMessage.text = "Veuillez confirmer l'installation dans la fenêtre système..."
                    Snackbar.make(binding.root, "Confirmez l'installation...", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "installAceStreamFromXapk error", e)
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Erreur installation: ${e.message}", Snackbar.LENGTH_LONG).show()
                    xapkInstallInProgress = false
                    binding.setupProgress.visibility = View.GONE
                    binding.setupActionButton.isEnabled = true
                    binding.setupActionButton.text = "Installer AceStream"
                    binding.setupMessage.text = "Étape 2/2 : Installez AceStream Engine pour lire les flux."
                }
            }
        }
    }

    
    override fun onResume() {
        super.onResume()

        // Si on revient des paramètres après avoir accordé la permission
        if (pendingInstallAfterUnknownSources) {
            pendingInstallAfterUnknownSources = false
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                // Permission accordée, lancer l'installation
                showInstallStep()
                return
            }
        }

        // Re-vérifier l'état d'AceStream
        checkAceStreamAndStart()
        
        if (shouldShowAdOnResume) {
            shouldShowAdOnResume = false
            com.acestream.tv.ads.AdManager.getInstance(this).showBackToHomeAd(this)
        }
    }

    private fun setupRecyclerView() {
        channelAdapter = ChannelAdapter { channel ->
            onChannelClicked(channel)
        }
        
        binding.recyclerViewChannels.apply {
            layoutManager = GridLayoutManager(this@MainActivity, getSpanCount())
            adapter = channelAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupGroupChips() {
        groupAdapter = GroupChipAdapter { groupName ->
            if (viewModel.selectedGroup.value == groupName) {
                viewModel.clearGroupFilter()
            } else {
                viewModel.setGroupFilter(groupName)
            }
        }
        
        binding.recyclerViewGroups.adapter = groupAdapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadChannels()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.filteredChannels.collectLatest { channels ->
                channelAdapter.submitList(channels)
                binding.emptyView.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        
        lifecycleScope.launch {
            viewModel.groupNames.collectLatest { groups ->
                groupAdapter.submitList(groups)
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
                binding.progressBar.visibility = if (isLoading && channelAdapter.itemCount == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.selectedGroup.collectLatest { selectedGroup ->
                groupAdapter.setSelectedGroup(selectedGroup)
            }
        }
        
        lifecycleScope.launch {
            aceStreamManager.engineStatus.collectLatest { status ->
                updateEngineStatusUI(status)
            }
        }
    }

    private fun updateEngineStatusUI(status: EngineState) {
        val statusText = when (status) {
            is EngineState.Stopped -> getString(R.string.engine_stopped)
            is EngineState.Starting -> getString(R.string.engine_starting)
            is EngineState.Running -> getString(R.string.engine_running)
            is EngineState.Error -> getString(R.string.engine_error, status.message)
        }
        
        // Show text ONLY when running (Souabni TechIA), otherwise hide it
        binding.engineStatusText.text = statusText
        binding.engineStatusText.visibility = if (status is EngineState.Running) View.VISIBLE else View.GONE
        
        binding.engineStatusIndicator.setImageResource(
            when (status) {
                is EngineState.Running -> R.drawable.ic_status_online
                // Error should be GREY (offline) now, not red
                else -> R.drawable.ic_status_offline
            }
        )
    }

    private fun onChannelClicked(channel: Channel) {
        if (!aceStreamManager.isAceStreamInstalled()) {
            showInstallStep()
            return
        }
        
        // Check and Show Ad before playing
        com.acestream.tv.ads.AdManager.getInstance(this).checkAndShowAd(this) {
            shouldShowAdOnResume = true
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id)
                putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, channel.aceStreamId)
                putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.name)
            }
            startActivity(intent)
        }
    }

    private fun getSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return (screenWidthDp / 180).toInt().coerceIn(2, 6)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
        
        return true
    }

    private fun setupLiveBlinking() {
        val btnMatches = binding.btnMatches
        val fullText = getString(R.string.live_event) // "LIVE EVENT"
        val livePart = "LIVE"
        val liveIndex = fullText.indexOf(livePart)

        if (liveIndex == -1) return

        lifecycleScope.launch {
            var isGreen = true
            while (true) {
                val spannable = SpannableString(fullText)
                val color = if (isGreen) Color.GREEN else Color.WHITE
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    liveIndex,
                    liveIndex + livePart.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                btnMatches.text = spannable
                isGreen = !isGreen
                delay(800) // Blink interval
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                viewModel.loadChannels()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}






