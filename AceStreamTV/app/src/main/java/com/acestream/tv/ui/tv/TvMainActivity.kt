package com.acestream.tv.ui.tv

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import coil.load
import com.acestream.tv.R
import com.acestream.tv.acestream.AceStreamManager
import com.acestream.tv.model.Channel
import com.acestream.tv.repository.ChannelRepository
import com.acestream.tv.repository.M3UParser
import com.acestream.tv.ui.player.PlayerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android TV Main Activity using Leanback library
 * Displays channels in a TV-friendly row-based UI
 */
class TvMainActivity : FragmentActivity() {

    private lateinit var aceStreamManager: AceStreamManager
    private lateinit var repository: ChannelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        aceStreamManager = AceStreamManager.getInstance(this)
        repository = ChannelRepository.getInstance(this)

        // Start engine
        aceStreamManager.startEngine()

        // Load channels
        lifecycleScope.launch {
            repository.loadFromAssets()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tv_container, TvBrowseFragment())
                .commitNow()
        }
    }
}

/**
 * Leanback Browse Fragment for TV channel browsing
 */
class TvBrowseFragment : BrowseSupportFragment() {

    private lateinit var repository: ChannelRepository
    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        repository = ChannelRepository.getInstance(requireContext())
        
        setupUI()
        setupEventListeners()
        loadChannels()
    }

    private fun setupUI() {
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        
        // Brand colors
        brandColor = resources.getColor(R.color.tv_brand_color, null)
        searchAffordanceColor = resources.getColor(R.color.tv_search_color, null)

        // Create presenter for channel cards
        val cardPresenter = ChannelCardPresenter()
        
        // Create rows adapter
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        // Channel click
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_CHANNEL_ID, item.id)
                    putExtra(PlayerActivity.EXTRA_ACESTREAM_ID, item.aceStreamId)
                    putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, item.name)
                }
                startActivity(intent)
            }
        }

        // Channel focus (for info display)
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item is Channel) {
                // Could update background or show info panel
            }
        }

        // Search click
        setOnSearchClickedListener {
            // TODO: Implement TV search
        }
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            repository.groups.collectLatest { groups ->
                rowsAdapter.clear()
                
                val cardPresenter = ChannelCardPresenter()
                
                groups.forEach { group ->
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    group.channels.forEach { channel ->
                        listRowAdapter.add(channel)
                    }
                    
                    val header = HeaderItem(group.name)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }
    }
}

/**
 * Presenter for channel cards in TV UI
 */
class ChannelCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val cardView = androidx.leanback.widget.ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Card dimensions for TV
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val cardView = viewHolder.view as androidx.leanback.widget.ImageCardView
        
        cardView.titleText = channel.name
        cardView.contentText = channel.groupTitle
        
        // Load logo
        if (channel.hasLogo()) {
            cardView.mainImageView.load(channel.logoUrl) {
                placeholder(R.drawable.ic_channel_placeholder)
                error(R.drawable.ic_channel_placeholder)
            }
        } else {
            cardView.mainImageView.setImageResource(R.drawable.ic_channel_placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as androidx.leanback.widget.ImageCardView
        cardView.mainImage = null
    }

    companion object {
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176
    }
}
