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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingText: TextView
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var dotsCount = 0
    private var loadingRunning = false
    private var player: ExoPlayer? = null
    private var items: List<MediaItem> = emptyList()
    private var playlistJsonWithAnalytics: String = "[]"

    private val videosJson = """
        [
          {
            "id": 1,
            "userId": 1001,
            "title": "10 Seconds Placeholder (HD)",
            "url": "https://samplelib.com/lib/preview/mp4/sample-10s.mp4",
            "views": 18204,
            "orientation": "landscape"
          },
          {
            "id": 2,
            "userId": 1002,
            "title": "Big Buck Bunny (30s Clip)",
            "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "views": 958312,
            "orientation": "landscape"
          },
          {
            "id": 3,
            "userId": 1003,
            "title": "Sintel Trailer (Open Movie)",
            "url": "https://media.w3.org/2010/05/sintel/trailer.mp4",
            "views": 702149,
            "orientation": "portrait"
          },
          {
            "id": 4,
            "userId": 1004,
            "title": "Tears of Steel (Short Test)",
            "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "views": 489213,
            "orientation": "landscape"
          },
          {
            "id": 5,
            "userId": 1005,
            "title": "Bunny 720p Placeholder",
            "url": "https://file-examples.com/storage/feafbcc9dbfe6bb7/sample_960x400_ocean_with_audio.mp4",
            "views": 14326,
            "orientation": "portrait"
          },
          {
            "id": 6,
            "userId": 1006,
            "title": "5 Seconds Color Bars (No Audio)",
            "url": "https://sample-videos.com/video123/mp4/240/big_buck_bunny_240p_5mb.mp4",
            "views": 33102,
            "orientation": "portrait"
          },
          {
            "id": 7,
            "userId": 1007,
            "title": "Placeholder Landscape Video",
            "url": "https://www.w3schools.com/html/mov_bbb.mp4",
            "views": 110243,
            "orientation": "landscape"
          },
          {
            "id": 8,
            "userId": 1008,
            "title": "City Drone Footage Placeholder",
            "url": "https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4",
            "views": 57219,
            "orientation": "landscape"
          },
          {
            "id": 9,
            "userId": 1009,
            "title": "Pexels Stock Test Clip (Nature)",
            "url": "https://player.vimeo.com/external/310582961.sd.mp4?s=76f43e87875cc9d5c8adff73a8b80db0c4e8a0db&profile_id=164",
            "views": 243879,
            "orientation": "portrait"
          },
          {
            "id": 10,
            "userId": 1010,
            "title": "Black Screen Placeholder (Silent)",
            "url": "https://archive.org/download/black-video-placeholder/black-video-placeholder.mp4",
            "views": 5271,
            "orientation": "landscape"
          }
        ]
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        loadingText = findViewById(R.id.text_loading)
        items = buildMediaItemsFromJson()

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

    private fun buildMediaItemsFromJson(): List<MediaItem> {
        return try {
            val jsonArray = JSONArray(videosJson)
            val uris = mutableListOf<Uri>()
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val chosen = (prefs.getString(SettingsActivity.KEY_VIDEO_ORIENTATION, SettingsActivity.ORIENTATION_LANDSCAPE)
                ?: SettingsActivity.ORIENTATION_LANDSCAPE).lowercase()
            val establishment = prefs.getString(SettingsActivity.KEY_ESTABLISHMENT, "") ?: ""
            val cep = prefs.getString(SettingsActivity.KEY_CEP, "") ?: ""

            // Monta JSON aumentado com chave analytics por vídeo
            val augmented = JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                // Adiciona chave analytics
                val analytics = JSONObject()
                    .put("establishment", establishment)
                    .put("cep", cep)
                val itemWithAnalytics = JSONObject(item.toString())
                itemWithAnalytics.put("analytics", analytics)
                augmented.put(itemWithAnalytics)

                val url = itemWithAnalytics.getString("url")
                val itemOrientation = itemWithAnalytics.optString("orientation", "").lowercase()
                val orientationMatches = itemOrientation.isEmpty() || itemOrientation == chosen
                if (orientationMatches) {
                    runCatching { Uri.parse(url) }.getOrNull()?.let { uris.add(it) }
                }
            }
            
            playlistJsonWithAnalytics = augmented.toString()
            uris.map { uri -> MediaItem.fromUri(uri) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}