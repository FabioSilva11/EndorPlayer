package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var progressLoading: CircularProgressIndicator
    private var player: ExoPlayer? = null
    private var items: List<MediaItem> = emptyList()
    private data class VideoRecord(
        val videoId: Int,
        val mediaItem: MediaItem
    )
    private var videoRecords: List<VideoRecord> = emptyList()
    private var lastPlayedIndex: Int = -1
    // Controle para não incrementar mais de uma vez por vídeo nesta sessão
    private val incrementedVideoIds: MutableSet<Int> = mutableSetOf()

    private val httpClient by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        progressLoading = findViewById(R.id.progress_loading)
        fetchMediaItemsFromDatabase { mediaItems, records ->
            items = mediaItems
            videoRecords = records
            if (items.isNotEmpty()) {
                initializePlayer()
            }
        }

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
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
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
                    Player.STATE_READY -> {
                        // Migração: incrementa visualizações ao iniciar reprodução,
                        // seguindo o exemplo Python (incremento após reproduzir).
                        maybeIncrementCurrent()
                        hideLoading()
                    }
                    Player.STATE_ENDED -> hideLoading()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

    private fun showLoading() {
        progressLoading.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressLoading.visibility = View.GONE
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    private fun fetchMediaItemsFromDatabase(onLoaded: (List<MediaItem>, List<VideoRecord>) -> Unit) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val chosen = (prefs.getString(SettingsActivity.KEY_VIDEO_ORIENTATION, SettingsActivity.ORIENTATION_LANDSCAPE)
            ?: SettingsActivity.ORIENTATION_LANDSCAPE).lowercase()
        val filterEnabled = prefs.getBoolean(SettingsActivity.KEY_FILTER_ENABLED, false)
        val filterValueStr = prefs.getString(SettingsActivity.KEY_FILTER_VALUE, "")?.trim().orEmpty()
        val filterValueNum: Long? = filterValueStr.toLongOrNull()

        val urlBuilder = HttpUrl.Builder()
            .scheme(API_SCHEME)
            .host(API_HOST)
            .addPathSegment("functions")
            .addPathSegment("v1")
            .addPathSegment("videos")

        if (filterEnabled && filterValueNum != null && filterValueNum > 0) {
            urlBuilder.addQueryParameter("filter_code", filterValueNum.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { onLoaded(emptyList(), emptyList()) }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: "{}"
                    // The API returns an object with a "videos" array. Be robust to raw array too.
                    val arr = try {
                        val root = JSONObject(body)
                        root.optJSONArray("videos") ?: JSONArray()
                    } catch (_: Exception) {
                        try { JSONArray(body) } catch (_: Exception) { JSONArray() }
                    }
                    val mediaItems = mutableListOf<MediaItem>()
                    val records = mutableListOf<VideoRecord>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val rawUrl = obj.optString("url")
                        // Remove accidental backticks/spaces copied from formatting
                        val urlStr = rawUrl.replace("`", "").trim()
                        val idVal = obj.optInt("id", -1)
                        val o = obj.optString("orientation", "").lowercase(Locale.getDefault())
                        if (urlStr.isEmpty() || !urlStr.startsWith("http") || idVal <= 0) continue
                        if (o.isNotEmpty() && chosen != "auto" && o != chosen) continue
                        val mediaItem = MediaItem.fromUri(Uri.parse(urlStr))
                        mediaItems.add(mediaItem)
                        records.add(VideoRecord(videoId = idVal, mediaItem = mediaItem))
                    }
                    runOnUiThread { onLoaded(mediaItems, records) }
                }
            }
        })
    }

    private fun incrementViewsForIndex(index: Int) {
        if (index < 0 || index >= videoRecords.size) return
        val videoId = videoRecords[index].videoId
        // Evita incrementar duas vezes o mesmo vídeo em uma única sessão
        if (incrementedVideoIds.contains(videoId)) return

        val url = HttpUrl.Builder()
            .scheme(API_SCHEME)
            .host(API_HOST)
            .addPathSegment("functions")
            .addPathSegment("v1")
            .addPathSegment("videos")
            .addQueryParameter("video_id", videoId.toString())
            .build()

        // 1) GET current views
        val getReq = Request.Builder().url(url).get().build()
        httpClient.newCall(getReq).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // Ignore failures silently
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    val bodyStr = it.body?.string().orEmpty()
                    var currentViews = -1
                    try {
                        val root = JSONObject(bodyStr)
                        // Prefer { "video": { "views": N } }
                        val videoObj = root.optJSONObject("video")
                        if (videoObj != null) {
                            currentViews = videoObj.optInt("views", -1)
                        } else {
                            // Fallbacks: { "views": N } or { "videos": [ {...} ] }
                            currentViews = root.optInt("views", -1)
                            if (currentViews < 0) {
                                val arr = root.optJSONArray("videos")
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        val obj = arr.optJSONObject(i) ?: continue
                                        if (obj.optInt("id", -1) == videoId) {
                                            currentViews = obj.optInt("views", -1)
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // If parsing fails, skip
                    }

                    if (currentViews < 0) return
                    val newViews = currentViews + 1

                    // 2) POST set views to current+1
                    val jsonObj = JSONObject()
                        .put("action", "set")
                        .put("views", newViews)
                    val postBody: RequestBody = jsonObj.toString()
                        .toRequestBody("application/json".toMediaTypeOrNull())
                    val postReq = Request.Builder().url(url).post(postBody).build()
                    httpClient.newCall(postReq).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { /* ignore */ }
                        override fun onResponse(call: okhttp3.Call, response: Response) { response.close() }
                    })
                    // Marca como incrementado após disparar POST
                    incrementedVideoIds.add(videoId)
                }
            }
        })
    }

    // Auxiliar: incrementa views quando o player entra em READY para o item atual
    private fun maybeIncrementCurrent() {
        val exo = player ?: return
        val idx = exo.currentMediaItemIndex
        if (idx in videoRecords.indices) {
            incrementViewsForIndex(idx)
        }
    }

    companion object {
        private const val API_SCHEME = "https"
        private const val API_HOST = "snhkaxkpczwmxsxdfjas.supabase.co"
    }
}