package com.hud.aamusic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max

/** Trung tâm của app: player dùng chung cho giao diện điện thoại và Android Auto. */
object Engine {
    lateinit var app: Context
    lateinit var player: ExoPlayer
    private val main = Handler(Looper.getMainLooper())

    // ---- Trạng thái cho giao diện ----
    var songs by mutableStateOf<List<Song>>(emptyList())
    var current by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var seeking = false
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var editing by mutableStateOf<Song?>(null)
    var editsVersion by mutableIntStateOf(0)
    var artVersion by mutableIntStateOf(0)
    var lyricLines by mutableStateOf<List<LrcLine>>(emptyList())
    var plainLyrics by mutableStateOf("")

    // ---- Equalizer / Bass ----
    var eqInfo by mutableStateOf<EqInfo?>(null)
    var eqEnabled by mutableStateOf(false)
    val bandLevels = mutableStateListOf<Int>()
    var bassStrength by mutableIntStateOf(0)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var attachedSession = -1

    private var index: Map<String, Song> = emptyMap()
    private var streamIndex: Map<String, Song> = emptyMap()
    private val edits = HashMap<String, Triple<String, String, String>>()
    private val artCache = LruCache<String, Bitmap>(40)

    val prefs get() = app.getSharedPreferences("aa", Context.MODE_PRIVATE)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Engine.isPlaying = isPlaying }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshCurrent() }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player.duration > 0) durationMs = player.duration
            if (playbackState == Player.STATE_READY) {
                if (eq == null) attachEffects(player.audioSessionId)
                refreshCurrent()
            }
        }
        override fun onAudioSessionIdChanged(audioSessionId: Int) { attachEffects(audioSessionId) }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
        override fun onRepeatModeChanged(repeatMode: Int) { Engine.repeatMode = repeatMode }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        player = ExoPlayer.Builder(app).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(listener)
        }
        eqEnabled = prefs.getBoolean("eq_on", false)
        bassStrength = prefs.getInt("bass", 0)
        loadEdits()
    }

    // ---------------- Thư viện ----------------

    fun loadSongsAsync() {
        thread {
            val l = querySongs()
            main.post { applySongs(l) }
        }
    }

    fun ensureLoaded(): List<Song> {
        if (songs.isEmpty()) applySongs(querySongs())
        return songs
    }

    private fun applySongs(l: List<Song>) {
        songs = l
        index = l.associateBy { it.id }
        refreshCurrent()
    }

    private fun querySongs(): List<Song> {
        val out = ArrayList<Song>()
        val media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION
        )
        try {
            app.contentResolver.query(
                media, proj,
                "${MediaStore.Audio.Media.IS_MUSIC}!=0 AND ${MediaStore.Audio.Media.DURATION}>10000", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: ""
                    val album = c.getString(3) ?: ""
                    out += Song("L:$id", c.getString(1) ?: "?", artist, album,
                        ContentUris.withAppendedId(media, id), c.getLong(4))
                }
            }
        } catch (e: SecurityException) {
        }
        return out
    }

    fun streams(): List<Song> {
        val text = prefs.getString("streams", "") ?: ""
        val l = ArrayList<Song>()
        text.lines().forEachIndexed { i, line ->
            val p = line.split("|", limit = 2)
            if (p.size == 2 && p[1].isNotBlank())
                l += Song("R:$i", p[0].trim(), "Radio", "Radio", Uri.parse(p[1].trim()), 0L, true)
        }
        streamIndex = l.associateBy { it.id }
        return l
    }

    // ---------------- Tên hiển thị (đã áp dụng chỉnh sửa tag) ----------------

    fun title(s: Song): String {
        if (editsVersion < 0) return s.title
        return edits[s.id]?.first?.takeIf { it.isNotBlank() } ?: s.title
    }

    fun artist(s: Song): String {
        if (editsVersion < 0) return s.artist
        return (edits[s.id]?.second?.takeIf { it.isNotBlank() } ?: s.artist).ifBlank { "Không rõ nghệ sĩ" }
    }

    fun album(s: Song): String {
        if (editsVersion < 0) return s.album
        return (edits[s.id]?.third?.takeIf { it.isNotBlank() } ?: s.album).ifBlank { "Không rõ album" }
    }

    // ---------------- Phát nhạc ----------------

    private fun item(s: Song) = MediaItem.Builder()
        .setUri(s.uri)
        .setMediaId(s.id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title(s)).setArtist(artist(s)).setAlbumTitle(album(s)).build()
        )
        .build()

    fun play(list: List<Song>, startIndex: Int) {
        if (list.isEmpty()) return
        player.setMediaItems(list.map { item(it) }, startIndex.coerceIn(0, list.size - 1), 0L)
        player.prepare()
        player.play()
    }

    fun playById(id: String) {
        if (id.startsWith("L:")) {
            val list = ensureLoaded()
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) play(list, i)
        } else {
            val list = streams()
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) play(listOf(list[i]), 0)
        }
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun tick() {
        if (!seeking) positionMs = player.currentPosition
        val d = player.duration
        if (d > 0) durationMs = d
    }

    fun refreshCurrent() {
        val id = player.currentMediaItem?.mediaId
        val s = if (id == null) null else (index[id] ?: streamIndex[id])
        if (s?.id != current?.id) {
            current = s
            loadLyrics(s)
        }
    }

    // ---------------- Chỉnh sửa thông tin ----------------

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9]"), "_")
    private fun editsFile() = File(app.filesDir, "tags.json")

    private fun loadEdits() {
        try {
            val f = editsFile()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                edits[k] = Triple(e.optString("t"), e.optString("a"), e.optString("b"))
            }
        } catch (e: Exception) {
        }
    }

    fun saveEdit(s: Song, t: String, a: String, b: String) {
        edits[s.id] = Triple(t.trim(), a.trim(), b.trim())
        val o = JSONObject()
        for ((k, v) in edits) o.put(k, JSONObject().put("t", v.first).put("a", v.second).put("b", v.third))
        try { editsFile().writeText(o.toString()) } catch (e: Exception) { }
        editsVersion++
    }

    // ---------------- Ảnh bìa ----------------

    private fun artFile(s: Song) = File(File(app.filesDir, "art"), "${safe(s.id)}.jpg")

    fun saveArt(s: Song, bytes: ByteArray): Boolean {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val f = artFile(s)
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        artCache.evictAll()
        main.post { artVersion++ }
        return true
    }

    private fun scaled(b: Bitmap, size: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= size) return b
        val k = size.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    fun loadArt(s: Song?, size: Int): Bitmap? {
        if (s == null) return null
        val key = "${s.id}:$size:$artVersion"
        artCache.get(key)?.let { return it }
        var bmp: Bitmap? = null
        val f = artFile(s)
        if (f.exists()) bmp = BitmapFactory.decodeFile(f.path)?.let { scaled(it, size) }
        if (bmp == null && !s.streaming) {
            if (Build.VERSION.SDK_INT >= 29) {
                try { bmp = app.contentResolver.loadThumbnail(s.uri, Size(size, size), null) } catch (e: Exception) { }
            }
            if (bmp == null) {
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(app, s.uri)
                    val b = r.embeddedPicture
                    r.release()
                    if (b != null) bmp = BitmapFactory.decodeByteArray(b, 0, b.size)?.let { scaled(it, size) }
                } catch (e: Exception) { }
            }
        }
        if (bmp != null) artCache.put(key, bmp)
        return bmp
    }

    // ---------------- Lời bài hát ----------------

    private fun lyricsFile(s: Song) = File(File(app.filesDir, "lyrics"), "${safe(s.id)}.lrc")

    fun saveLyrics(s: Song, text: String) {
        val f = lyricsFile(s)
        f.parentFile?.mkdirs()
        f.writeText(text)
        if (current?.id == s.id) loadLyrics(s)
    }

    private fun loadLyrics(s: Song?) {
        thread {
            var lines = emptyList<LrcLine>()
            var plain = ""
            if (s != null) {
                val f = lyricsFile(s)
                if (f.exists()) {
                    try {
                        val t = f.readText()
                        val l = Lrc.parse(t)
                        if (l.isNotEmpty()) lines = l else plain = t
                    } catch (e: Exception) { }
                }
            }
            main.post { lyricLines = lines; plainLyrics = plain }
        }
    }

    // ---------------- Equalizer & Bass booster ----------------

    private fun attachEffects(session: Int) {
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        if (session == attachedSession && eq != null) return
        releaseEffects()
        attachedSession = session
        try {
            val e = Equalizer(0, session)
            val range = e.bandLevelRange
            val n = e.numberOfBands.toInt()
            val freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            val presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            val saved = prefs.getString("eq_levels", "")?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            val levels = (0 until n).map { if (saved.size == n) saved[it].coerceIn(lo, hi) else 0 }
            for (i in 0 until n) e.setBandLevel(i.toShort(), levels[i].toShort())
            e.setEnabled(eqEnabled)
            eq = e
            main.post {
                bandLevels.clear()
                bandLevels.addAll(levels)
                eqInfo = EqInfo(lo, hi, freqs, presets)
            }
        } catch (ex: Exception) {
            eq = null
        }
        try {
            val b = BassBoost(0, session)
            if (b.strengthSupported) b.setStrength(bassStrength.toShort())
            b.setEnabled(bassStrength > 0)
            bass = b
        } catch (ex: Exception) {
            bass = null
        }
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (e: Exception) { }
        try { bass?.release() } catch (e: Exception) { }
        eq = null
        bass = null
    }

    private fun saveEq() {
        prefs.edit().putString("eq_levels", bandLevels.joinToString(",")).apply()
    }

    fun setEqEnabled(on: Boolean) {
        eqEnabled = on
        try { eq?.setEnabled(on) } catch (e: Exception) { }
        prefs.edit().putBoolean("eq_on", on).apply()
    }

    fun setBand(b: Int, level: Int) {
        if (b !in bandLevels.indices) return
        bandLevels[b] = level
        try { eq?.setBandLevel(b.toShort(), level.toShort()) } catch (e: Exception) { }
        saveEq()
    }

    fun usePreset(p: Int) {
        val e = eq ?: return
        try {
            e.usePreset(p.toShort())
            for (b in bandLevels.indices) bandLevels[b] = e.getBandLevel(b.toShort()).toInt()
            saveEq()
        } catch (ex: Exception) { }
    }

    fun setBass(v: Int) {
        bassStrength = v
        try {
            bass?.let {
                it.setEnabled(v > 0)
                if (it.strengthSupported) it.setStrength(v.toShort())
            }
        } catch (e: Exception) { }
        prefs.edit().putInt("bass", v).apply()
    }
}
