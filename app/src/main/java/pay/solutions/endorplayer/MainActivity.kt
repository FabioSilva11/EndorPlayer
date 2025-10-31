package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingText: TextView
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var dotsCount = 0
    private var loadingRunning = false
    private var player: ExoPlayer? = null
    private var items: List<MediaItem> = emptyList()
    private data class VideoRecord(
        val dbKey: String,
        val videoId: Int,
        val mediaItem: MediaItem
    )
    private var videoRecords: List<VideoRecord> = emptyList()
    private var lastPlayedIndex: Int = -1

    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private var tvId: Int = -1
    private val presenceHandler = Handler(Looper.getMainLooper())
    private val presenceRunnable = object : Runnable {
        override fun run() {
            updateLastSeen()
            presenceHandler.postDelayed(this, 60_000) // 60s
        }
    }

    // JSON local removido: agora os vídeos vêm apenas do Firebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        loadingText = findViewById(R.id.text_loading)
        fetchMediaItemsFromDatabase { mediaItems, records ->
            items = mediaItems
            videoRecords = records
            if (items.isNotEmpty()) {
                initializePlayer()
            }
        }

        // Carrega id da TV salvo nas preferências
        tvId = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(SettingsActivity.KEY_TV_ID, -1)

        applyImmersiveMode()

        if (items.isNotEmpty()) {
            initializePlayer()
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, playerView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    

    override fun onStart() {
        super.onStart()
        if (items.isNotEmpty() && player == null) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onResume() {
        super.onResume()
        setOnline(true)
        presenceHandler.removeCallbacks(presenceRunnable)
        presenceHandler.post(presenceRunnable)
    }

    override fun onPause() {
        super.onPause()
        presenceHandler.removeCallbacks(presenceRunnable)
        setOnline(false)
        updateLastSeen()
    }

    override fun onDestroy() {
        presenceHandler.removeCallbacks(presenceRunnable)
        setOnline(false)
        updateLastSeen()
        super.onDestroy()
    }

    private fun initializePlayer() {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo

        exo.setRepeatMode(Player.REPEAT_MODE_ALL)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> showLoading()
                    Player.STATE_READY, Player.STATE_ENDED -> hideLoading()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val prevIndex = lastPlayedIndex
                    if (prevIndex >= 0) incrementViewsForIndex(prevIndex)
                }
                lastPlayedIndex = exo.currentMediaItemIndex
            }
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                // Pula silenciosamente para o próximo item
                if (exo.hasNextMediaItem()) {
                    exo.seekToNextMediaItem()
                } else {
                    exo.seekTo(0, 0)
                }
                exo.prepare()
                exo.play()
                showLoading()
            }
        })

        exo.setMediaItems(items)
        showLoading()
        exo.prepare()
        exo.play()
        lastPlayedIndex = exo.currentMediaItemIndex
    }

    private val loadingRunnable = object : Runnable {
        override fun run() {
            if (!loadingRunning) return
            dotsCount = (dotsCount + 1) % 6 // 0..5
            val dots = ".".repeat(dotsCount)
            loadingText.text = "sincronizando dados com a nuvem aguarde" + dots
            loadingHandler.postDelayed(this, 500)
        }
    }

    private fun showLoading() {
        if (loadingText.visibility != View.VISIBLE) {
            loadingText.visibility = View.VISIBLE
        }
        if (!loadingRunning) {
            loadingRunning = true
            dotsCount = 0
            loadingHandler.post(loadingRunnable)
        }
    }

    private fun hideLoading() {
        loadingText.visibility = View.GONE
        loadingRunning = false
        loadingHandler.removeCallbacks(loadingRunnable)
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    private fun setOnline(online: Boolean) {
        if (tvId <= 0) return
        db.child("tvs").child(tvId.toString()).child("status_online").setValue(online)
        if (online) updateLastSeen()
    }

    private fun updateLastSeen() {
        if (tvId <= 0) return
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        db.child("tvs").child(tvId.toString()).child("ultimo_visto").setValue(iso)
    }

    private fun fetchMediaItemsFromDatabase(onLoaded: (List<MediaItem>, List<VideoRecord>) -> Unit) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val chosen = (prefs.getString(SettingsActivity.KEY_VIDEO_ORIENTATION, SettingsActivity.ORIENTATION_LANDSCAPE)
            ?: SettingsActivity.ORIENTATION_LANDSCAPE).lowercase()
        // Campos locais não são mais acoplados a um JSON auxiliar

        db.child("videos").get().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onLoaded(emptyList(), emptyList())
                return@addOnCompleteListener
            }
            val mediaItems = mutableListOf<MediaItem>()
            val records = mutableListOf<VideoRecord>()
            val snapshot = task.result
            snapshot?.children?.forEach { child ->
                val key = child.key ?: return@forEach
                val url = child.child("url").getValue(String::class.java) ?: return@forEach
                val itemOrientation = (child.child("orientation").getValue(String::class.java) ?: "").lowercase()
                val orientationMatches = itemOrientation.isEmpty() || itemOrientation == chosen
                if (!orientationMatches) return@forEach
                val videoId = child.child("id").getValue(Int::class.java) ?: 0

                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                mediaItems.add(mediaItem)
                records.add(VideoRecord(dbKey = key, videoId = videoId, mediaItem = mediaItem))
            }
            onLoaded(mediaItems, records)
        }
    }

    private fun incrementViewsForIndex(index: Int) {
        if (index < 0 || index >= videoRecords.size) return
        val record = videoRecords[index]
        val viewsRef = db.child("videos").child(record.dbKey).child("views")
        viewsRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val current = (currentData.getValue(Int::class.java) ?: 0)
                currentData.value = current + 1
                return com.google.firebase.database.Transaction.success(currentData)
            }
            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: com.google.firebase.database.DataSnapshot?
            ) { }
        })
    }
}