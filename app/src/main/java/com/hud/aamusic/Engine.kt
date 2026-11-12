package com.hud.aamusic

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
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
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Trung tâm của app: player dùng chung cho giao diện điện thoại và Android Auto. */
object Engine {
    lateinit var app: Context
    lateinit var player: ExoPlayer
    private val main = Handler(Looper.getMainLooper())

    // ---- Thư viện ----
    var songs by mutableStateOf<List<Song>>(emptyList())      // sau khi lọc thư mục / bài đã ẩn
    private var allSongs: List<Song> = emptyList()

    // ---- Trạng thái cho giao diện ----
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

    // ---- Equalizer 10 dải + Bass ----
    val EQ_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val EQ_PRESETS: List<Pair<String, IntArray>> = listOf(
        "Phẳng" to intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Bass mạnh" to intArrayOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        "Treble" to intArrayOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6),
        "Giọng hát" to intArrayOf(-2, -2, -1, 1, 3, 4, 3, 1, 0, -1),
        "Rock" to intArrayOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5),
        "Pop" to intArrayOf(-1, 1, 3, 4, 3, 0, -1, -1, 1, 2),
        "Jazz" to intArrayOf(3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
        "Cổ điển" to intArrayOf(4, 3, 2, 1, 0, 0, 0, 1, 2, 3),
        "Điện tử" to intArrayOf(5, 4, 1, 0, -2, 2, 1, 1, 4, 5)
    )
    var eqInfo by mutableStateOf<EqInfo?>(EqInfo(-1200, 1200, EQ_FREQS.toList(), EQ_PRESETS.map { it.first }))
    var eqEnabled by mutableStateOf(false)
    val bandLevels = mutableStateListOf<Int>()     // đơn vị 1/100 dB
    var bassStrength by mutableIntStateOf(0)
    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var attachedSession = -1

    private var index: Map<String, Song> = emptyMap()
    private var keyIndex: Map<String, Song> = emptyMap()
    private var streamIndex: Map<String, Song> = emptyMap()
    private val edits = HashMap<String, Triple<String, String, String>>()
    private val artCache = LruCache<String, Bitmap>(40)

    val prefs get() = app.getSharedPreferences("aa", Context.MODE_PRIVATE)

    /** Service đăng ký để cập nhật thông báo khi yêu thích / ngẫu nhiên / lặp thay đổi. */
    var onExternalChange: (() -> Unit)? = null
    var queueVersion by mutableIntStateOf(0)

    // ---- Lời: chỉnh lệch thời gian theo từng bài (dương = lời hiện sớm hơn) ----
    var lyricOffsetMs by mutableIntStateOf(0)
    private val offsets = HashMap<String, Int>()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Engine.isPlaying = isPlaying }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshCurrent() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { queueVersion++ }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player.duration > 0) durationMs = player.duration
            if (playbackState == Player.STATE_READY) {
                if (dp == null && eq == null) attachEffects(player.audioSessionId)
                refreshCurrent()
            }
        }
        override fun onAudioSessionIdChanged(audioSessionId: Int) { attachEffects(audioSessionId) }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled; onExternalChange?.invoke() }
        override fun onRepeatModeChanged(repeatMode: Int) { Engine.repeatMode = repeatMode; onExternalChange?.invoke() }
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
        val saved = prefs.getString("eq10", "")?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        for (i in 0 until 10) bandLevels.add(if (saved.size == 10) saved[i].coerceIn(-1200, 1200) else 0)
        loadEdits()
        Lists.load()
        try {
            val o = JSONObject(prefs.getString("lyric_offsets", "{}") ?: "{}")
            for (k in o.keys()) offsets[k] = o.optInt(k, 0)
        } catch (e: Exception) { }
    }

    // ---------------- Thư viện ----------------

    fun loadSongsAsync() {
        thread {
            val l = querySongs()
            main.post { applySongs(l) }
        }
    }

    fun ensureLoaded(): List<Song> {
        if (allSongs.isEmpty()) applySongs(querySongs())
        return songs
    }

    private fun applySongs(l: List<Song>) {
        allSongs = l
        index = l.associateBy { it.id }
        keyIndex = l.associateBy { Lists.key(it) }
        refilter()
        refreshCurrent()
    }

    /** Lọc lại thư viện theo thư mục đã chọn và danh sách bài đã ẩn. */
    fun refilter() {
        songs = allSongs.filter { Lists.visible(it) }
    }

    fun pathMap(): Map<String, Song> = keyIndex

    private fun querySongs(): List<Song> {
        val out = ArrayList<Song>()
        val media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
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
                        ContentUris.withAppendedId(media, id), c.getLong(4), false, c.getString(5) ?: "")
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

    private fun playIn(list: List<Song>, songId: String) {
        val i = list.indexOfFirst { it.id == songId }
        if (i >= 0) play(list, i)
    }

    /** Id từ Android Auto: "L:123", "FAV|L:123", "PL:tên playlist|L:123", "R:0". */
    fun playById(id: String) {
        when {
            id.startsWith("PL:") -> {
                val rest = id.removePrefix("PL:")
                val sep = rest.lastIndexOf('|')
                if (sep > 0) playIn(Lists.songsOf(rest.substring(0, sep)), rest.substring(sep + 1))
            }
            id.startsWith("FAV|") -> playIn(Lists.favSongs(), id.removePrefix("FAV|"))
            id.startsWith("AL:") || id.startsWith("AR:") -> {
                val byAlbum = id.startsWith("AL:")
                val rest = id.substring(3)
                val sep = rest.lastIndexOf('|')
                if (sep > 0) {
                    val name = rest.substring(0, sep)
                    val list = ensureLoaded().filter { if (byAlbum) album(it) == name else artist(it) == name }
                    playIn(list, rest.substring(sep + 1))
                }
            }
            id.startsWith("Q:") -> {
                val i = id.removePrefix("Q:").toIntOrNull()
                if (i != null && i in 0 until player.mediaItemCount) jumpTo(i)
            }
            id.startsWith("L:") -> playIn(ensureLoaded(), id)
            else -> {
                val list = streams()
                val i = list.indexOfFirst { it.id == id }
                if (i >= 0) play(listOf(list[i]), 0)
            }
        }
    }

    // ---------------- Hàng đợi ----------------

    fun queue(): List<Song> {
        val out = ArrayList<Song>()
        for (i in 0 until player.mediaItemCount) {
            val mi = player.getMediaItemAt(i)
            val s = index[mi.mediaId] ?: streamIndex[mi.mediaId] ?: Song(
                mi.mediaId, mi.mediaMetadata.title?.toString() ?: "?", mi.mediaMetadata.artist?.toString() ?: "", "",
                mi.localConfiguration?.uri ?: Uri.EMPTY, 0L
            )
            out += s
        }
        return out
    }

    fun playNext(s: Song) {
        if (player.mediaItemCount == 0) {
            play(listOf(s), 0)
        } else {
            player.addMediaItem(player.currentMediaItemIndex + 1, item(s))
        }
    }

    fun addToQueue(s: Song) {
        if (player.mediaItemCount == 0) {
            play(listOf(s), 0)
        } else {
            player.addMediaItem(item(s))
        }
    }

    fun removeFromQueue(i: Int) { if (i in 0 until player.mediaItemCount) player.removeMediaItem(i) }

    fun moveInQueue(from: Int, to: Int) {
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) player.moveMediaItem(from, to)
    }

    fun jumpTo(i: Int) {
        if (i in 0 until player.mediaItemCount) {
            player.seekTo(i, 0L)
            player.play()
        }
    }

    fun clearQueue() {
        val cur = player.currentMediaItemIndex
        for (i in player.mediaItemCount - 1 downTo 0) if (i != cur) player.removeMediaItem(i)
    }

    // ---------------- Tìm kiếm (dùng cho giọng nói trên Android Auto) ----------------

    private fun foldKey(x: String): String =
        java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").replace('đ', 'd')
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun search(q: String): List<Song> {
        val tokens = foldKey(q).split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return ensureLoaded().filter {
            val hay = foldKey("${title(it)} ${artist(it)} ${album(it)}")
            tokens.all { t -> t in hay }
        }
    }

    fun playFromSearch(q: String?) {
        val text = q?.trim().orEmpty()
        val all = ensureLoaded()
        if (text.isEmpty()) { playShuffled(all); return }
        val m = search(text)
        if (m.isNotEmpty()) play(m, 0)
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

    fun playShuffled(list: List<Song>) {
        if (list.isEmpty()) return
        player.shuffleModeEnabled = true
        play(list, kotlin.random.Random.nextInt(list.size))
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
            lyricOffsetMs = s?.let { offsets[Lists.key(it)] } ?: 0
            loadLyrics(s)
        }
    }

    fun adjustLyricOffset(delta: Int) {
        val s = current ?: return
        val v = (lyricOffsetMs + delta).coerceIn(-15000, 15000)
        lyricOffsetMs = v
        if (v == 0) offsets.remove(Lists.key(s)) else offsets[Lists.key(s)] = v
        val o = JSONObject()
        for ((k, x) in offsets) o.put(k, x)
        prefs.edit().putString("lyric_offsets", o.toString()).apply()
    }

    fun resetLyricOffset() { adjustLyricOffset(-lyricOffsetMs) }

    // ---------------- Xoá bài ----------------

    /** Xoá file nhạc khỏi máy. @return null nếu thành công, ngược lại là thông báo lỗi. */
    fun deleteSong(s: Song): String? {
        if (s.path.isEmpty()) return "Bài này không có đường dẫn file"
        var ok = false
        try { ok = File(s.path).delete() } catch (e: Exception) { }
        if (!ok && Build.VERSION.SDK_INT < 30) {
            try { ok = app.contentResolver.delete(s.uri, null, null) > 0 } catch (e: Exception) { }
        }
        if (!ok) return if (!Tags.canWrite()) "Chưa cấp quyền Quản lý tất cả tệp" else "Không xoá được file"
        MediaScannerConnection.scanFile(app, arrayOf(s.path), null, null)
        main.post {
            // Gỡ khỏi hàng đợi đang phát
            var i = player.mediaItemCount - 1
            while (i >= 0) {
                if (player.getMediaItemAt(i).mediaId == s.id) player.removeMediaItem(i)
                i--
            }
            allSongs = allSongs.filter { it.id != s.id }
            index = allSongs.associateBy { it.id }
            keyIndex = allSongs.associateBy { Lists.key(it) }
            Lists.forget(s)
            refilter()
            refreshCurrent()
        }
        return null
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

    /** Ghi tag vào file (nếu có quyền) và luôn lưu một bản trong app để hiển thị ngay. */
    fun applyTags(s: Song, title: String?, artist: String?, album: String?, lyrics: String?, cover: ByteArray?): String? {
        var err: String? = null
        if (!s.streaming && s.path.isNotEmpty()) {
            err = if (!Tags.canWrite()) "Chưa cấp quyền Quản lý tất cả tệp" else Tags.write(s.path, title, artist, album, lyrics, cover)
        }
        val e = edits[s.id]
        val t = title?.takeIf { it.isNotBlank() } ?: e?.first ?: ""
        val a = artist?.takeIf { it.isNotBlank() } ?: e?.second ?: ""
        val b = album?.takeIf { it.isNotBlank() } ?: e?.third ?: ""
        main.post { saveEdit(s, t, a, b) }
        if (cover != null) saveArt(s, cover)
        if (!lyrics.isNullOrBlank()) {
            try { saveLyrics(s, lyrics) } catch (ex: Exception) { }
        }
        return err
    }

    fun songById(id: String): Song? = index[id]

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
                var text = ""
                if (!s.streaming && s.path.isNotEmpty()) text = Tags.read(s.path)?.lyrics ?: ""
                if (text.isBlank()) {
                    val f = lyricsFile(s)
                    if (f.exists()) {
                        try { text = f.readText() } catch (e: Exception) { }
                    }
                }
                if (text.isNotBlank()) {
                    val l = Lrc.parse(text)
                    if (l.isNotEmpty()) lines = l else plain = text
                }
            }
            main.post { lyricLines = lines; plainLyrics = plain }
        }
    }

    // ---------------- Equalizer 10 dải & Bass booster ----------------

    private fun attachEffects(session: Int) {
        if (session == C.AUDIO_SESSION_ID_UNSET) return
        if (session == attachedSession && (dp != null || eq != null)) return
        releaseEffects()
        attachedSession = session

        // Ưu tiên bộ chỉnh âm 10 dải thật của Android 9 trở lên
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, 10, false, 0, false, 0, false
                ).build()
                val d = DynamicsProcessing(0, session, cfg)
                dp = d
                applyDp()
                d.setEnabled(eqEnabled)
            } catch (t: Throwable) {
                dp = null
            }
        }
        // Dự phòng: Equalizer hệ thống (thường chỉ có 5 dải, app ánh xạ 10 dải vào các dải gần nhất)
        if (dp == null) {
            try {
                val e = Equalizer(0, session)
                eq = e
                applyEqFallback()
                e.setEnabled(eqEnabled)
            } catch (t: Throwable) {
                eq = null
            }
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

    private fun applyDp() {
        val d = dp ?: return
        try {
            var maxPos = 0f
            for (b in 0 until 10) {
                val g = (bandLevels.getOrNull(b) ?: 0) / 100f
                if (g > maxPos) maxPos = g
                for (ch in 0 until 2) {
                    d.setPreEqBandByChannelIndex(ch, b, DynamicsProcessing.EqBand(true, EQ_FREQS[b].toFloat(), g))
                }
            }
            d.setInputGainAllChannelsTo(-maxPos * 0.5f)
        } catch (t: Throwable) {
        }
    }

    private fun applyEqFallback() {
        val e = eq ?: return
        try {
            val range = e.bandLevelRange
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            for (i in 0 until e.numberOfBands.toInt()) {
                val f = (e.getCenterFreq(i.toShort()) / 1000).coerceAtLeast(1)
                var best = 0
                var bd = Double.MAX_VALUE
                for (b in 0 until 10) {
                    val d = abs(ln(f.toDouble() / EQ_FREQS[b]))
                    if (d < bd) { bd = d; best = b }
                }
                e.setBandLevel(i.toShort(), (bandLevels.getOrNull(best) ?: 0).coerceIn(lo, hi).toShort())
            }
        } catch (t: Throwable) {
        }
    }

    private fun applyEffects() {
        applyDp()
        applyEqFallback()
    }

    private fun releaseEffects() {
        try { dp?.release() } catch (e: Throwable) { }
        try { eq?.release() } catch (e: Throwable) { }
        try { bass?.release() } catch (e: Throwable) { }
        dp = null
        eq = null
        bass = null
        attachedSession = -1
    }

    private fun saveEq() {
        prefs.edit().putString("eq10", bandLevels.joinToString(",")).apply()
    }

    fun changeEq(on: Boolean) {
        eqEnabled = on
        try { dp?.setEnabled(on) } catch (e: Throwable) { }
        try { eq?.setEnabled(on) } catch (e: Throwable) { }
        prefs.edit().putBoolean("eq_on", on).apply()
    }

    fun setBand(b: Int, level: Int) {
        if (b !in bandLevels.indices) return
        bandLevels[b] = level.coerceIn(-1200, 1200)
        applyEffects()
        saveEq()
    }

    fun usePreset(p: Int) {
        val preset = EQ_PRESETS.getOrNull(p) ?: return
        for (b in 0 until 10) bandLevels[b] = preset.second[b] * 100
        applyEffects()
        saveEq()
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

    /** Thoát hoàn toàn: dừng nhạc, dừng dịch vụ, đóng app và kết thúc tiến trình. */
    fun quit(activity: android.app.Activity) {
        try { player.stop() } catch (e: Exception) { }
        ScreenCapture.stop()
        releaseEffects()
        try { app.stopService(Intent(app, MusicService::class.java)) } catch (e: Exception) { }
        activity.finishAndRemoveTask()
        main.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 900)
    }
}
