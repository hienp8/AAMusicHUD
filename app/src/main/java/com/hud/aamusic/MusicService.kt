package com.hud.aamusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlin.concurrent.thread

class MusicService : MediaBrowserServiceCompat() {

    companion object {
        const val ACTION_PREV = "com.hud.aamusic.PREV"
        const val ACTION_TOGGLE = "com.hud.aamusic.TOGGLE"
        const val ACTION_NEXT = "com.hud.aamusic.NEXT"
        const val ACTION_QUIT = "com.hud.aamusic.QUIT"
        const val ACTION_FAV = "com.hud.aamusic.FAV"
        const val ACTION_SHUFFLE = "com.hud.aamusic.SHUFFLE"
        const val ACTION_REPEAT = "com.hud.aamusic.REPEAT"
    }

    private lateinit var session: MediaSessionCompat
    private val main = Handler(Looper.getMainLooper())

    private var conn: CarConnection? = null
    private var aaConnected = false
    private var inForeground = false
    private var lastArt: Bitmap? = null
    private var capArt: Bitmap? = null
    private var capSeq = 0
    private var lyricLine = ""
    private var lastKey = ""
    private val lyricRunnable = Runnable { scheduleLyric() }

    private val player get() = Engine.player

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) { updateState() }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateState()
            if (playWhenReady) startFg()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lyricLine = ""
            pushMeta()
            scheduleLyric()
            updateQueue()
            updateState()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { scheduleLyric() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { updateQueue() }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) { scheduleLyric() }
    }

    private val connObserver = Observer<Int> { t -> onCarType(t) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "cap_enabled", "cap_pkg" -> updateCapture()
            "lock_lyrics" -> { lyricLine = ""; scheduleLyric(); pushMeta() }
            "notif_extra" -> updateState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "AAMusicHud").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player.play() }
                override fun onPause() { player.pause() }
                override fun onStop() { player.stop() }
                override fun onSkipToNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
                override fun onSkipToPrevious() { player.seekToPreviousMediaItem() }
                override fun onSeekTo(pos: Long) { player.seekTo(pos) }
                override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) { Engine.playById(mediaId) }
                override fun onCustomAction(action: String, extras: Bundle?) { handleExtra(action) }
                override fun onSkipToQueueItem(id: Long) { Engine.jumpTo(id.toInt()) }
                override fun onPlayFromSearch(query: String?, extras: Bundle?) { Engine.playFromSearch(query) }
                override fun onSetShuffleMode(shuffleMode: Int) {
                    player.shuffleModeEnabled = shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
                }
                override fun onSetRepeatMode(repeatMode: Int) {
                    player.repeatMode = when (repeatMode) {
                        PlaybackStateCompat.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                        PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        player.addListener(listener)

        Engine.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        try {
            conn = CarConnection(applicationContext).also { it.type.observeForever(connObserver) }
        } catch (e: Throwable) {
            Diag.aa = "Android Auto: không kiểm tra được (${e.javaClass.simpleName})"
        }

        Engine.onExternalChange = { updateState() }
        ScreenCapture.onFrame = { bmp ->
            capArt = bmp
            capSeq++
            pushMeta()
        }

        Diag.service = "Dịch vụ: đang chạy"
        updateState()
        pushMeta()
        updateQueue()
        updateCapture()
        scheduleLyric()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FAV, ACTION_SHUFFLE, ACTION_REPEAT -> handleExtra(intent?.action ?: "")
            ACTION_PREV -> player.seekToPreviousMediaItem()
            ACTION_TOGGLE -> Engine.togglePlay()
            ACTION_NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            ACTION_QUIT -> {
                player.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                inForeground = false
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startFg()
                updateCapture()
            }
        }
        return START_STICKY
    }

    // ---------- Android Auto: kết nối và chụp hình DiOTO ----------

    private fun onCarType(t: Int) {
        val now = t == CarConnection.CONNECTION_TYPE_PROJECTION
        Diag.aa = when (t) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> "Android Auto: đã kết nối"
            CarConnection.CONNECTION_TYPE_NATIVE -> "Xe (Automotive): đã kết nối"
            else -> "Android Auto: chưa kết nối"
        }
        if (now != aaConnected) {
            aaConnected = now
            lastKey = ""
            pushMeta()
        }
        updateCapture()
    }

    /** Chụp hình app (DiOTO...) làm ảnh bìa: chỉ chạy khi đang kết nối Android Auto. */
    private fun updateCapture() {
        val prefs = Engine.prefs
        val pkg = prefs.getString("cap_pkg", "") ?: ""
        val want = prefs.getBoolean("cap_enabled", false) && aaConnected && pkg.isNotBlank()
        if (want) {
            ScreenCapture.start(this, pkg, false)
        } else if (!ScreenCapture.manual && ScreenCapture.running) {
            ScreenCapture.stop()
            capArt = null
            pushMeta()
        }
    }

    // ---------- Lời bài hát đồng bộ (thay tên nghệ sĩ) ----------

    private fun scheduleLyric() {
        main.removeCallbacks(lyricRunnable)
        if (!Engine.prefs.getBoolean("lock_lyrics", true) || !player.isPlaying) return
        val lines = Engine.lyricLines
        if (lines.isEmpty()) {
            if (lyricLine.isNotEmpty()) { lyricLine = ""; pushMeta() }
            main.postDelayed(lyricRunnable, 2500L)
            return
        }
        val pos = player.currentPosition + Engine.lyricOffsetMs
        val idx = lines.indexOfLast { it.timeMs <= pos + 150 }
        val line = if (idx >= 0) lines[idx].text else ""
        if (line != lyricLine) {
            lyricLine = line
            pushMeta()
        }
        val wait = if (idx + 1 < lines.size) lines[idx + 1].timeMs - pos else 3000L
        main.postDelayed(lyricRunnable, wait.coerceIn(250L, 4000L))
    }

    // ---------- Thông báo điều khiển nhạc ----------

    private fun actionIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Nút phụ thứ 5 trên thông báo: ngẫu nhiên hoặc lặp lại (theo Cài đặt). */
    private fun extraInfo(): Triple<Int, String, String> {
        val kind = Engine.prefs.getString("notif_extra", "shuffle") ?: "shuffle"
        return if (kind == "repeat") {
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> Triple(R.drawable.ic_n_repeat_all, ACTION_REPEAT, "Lặp: tất cả")
                Player.REPEAT_MODE_ONE -> Triple(R.drawable.ic_n_repeat_one, ACTION_REPEAT, "Lặp: một bài")
                else -> Triple(R.drawable.ic_n_repeat_off, ACTION_REPEAT, "Lặp: tắt")
            }
        } else if (player.shuffleModeEnabled) {
            Triple(R.drawable.ic_n_shuffle_on, ACTION_SHUFFLE, "Ngẫu nhiên: bật")
        } else {
            Triple(R.drawable.ic_n_shuffle_off, ACTION_SHUFFLE, "Ngẫu nhiên: tắt")
        }
    }

    private fun handleExtra(action: String) {
        when (action) {
            ACTION_FAV -> Engine.current?.let { Lists.toggleFav(it) }
            ACTION_SHUFFLE -> Engine.toggleShuffle()
            ACTION_REPEAT -> Engine.cycleRepeat()
        }
    }

    private fun buildNotification(): Notification {
        val s = Engine.current
        val playing = player.isPlaying
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, LyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val extra = extraInfo()
        val fav = Engine.current?.let { Lists.isFav(it) } == true
        val b = NotificationCompat.Builder(this, "aa")
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(s?.let { Engine.title(it) } ?: "AA Music")
            .setContentText(lyricLine.ifEmpty { s?.let { Engine.artist(it) } ?: "Sẵn sàng phát nhạc" })
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(
                if (fav) R.drawable.ic_n_heart_on else R.drawable.ic_n_heart_off,
                if (fav) "Bỏ yêu thích" else "Yêu thích", actionIntent(ACTION_FAV, 6)
            )
            .addAction(android.R.drawable.ic_media_previous, "Trước", actionIntent(ACTION_PREV, 1))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Tạm dừng" else "Phát", actionIntent(ACTION_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Tiếp", actionIntent(ACTION_NEXT, 3))
            .addAction(extra.first, extra.third, actionIntent(extra.second, 7))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        lastArt?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("aa", "AA Music", NotificationManager.IMPORTANCE_LOW))
        val n = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, n)
            }
            inForeground = true
        } catch (e: Exception) {
        }
    }

    private fun updateNotification() {
        if (!inForeground) return
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification())
        } catch (e: Exception) {
        }
    }

    // ---------- Duyệt thư viện trên Android Auto ----------

    override fun onGetRoot(pkg: String, uid: Int, hints: Bundle?) = BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val out = mutableListOf<MediaBrowserCompat.MediaItem>()
        when {
            parentId == "root" -> {
                out += folder("queue", "Hàng đợi")
                out += folder("fav", "Yêu thích")
                out += folder("playlists", "Playlist")
                out += folder("lib", "Thư viện")
            }
            parentId == "lib" -> {
                out += folder("local", "Bài hát")
                out += folder("albums", "Album")
                out += folder("artists", "Nghệ sĩ")
                out += folder("online", "Radio online")
            }
            parentId == "queue" -> {
                val q = Engine.queue()
                val cur = player.currentMediaItemIndex.coerceAtLeast(0)
                for (i in cur until minOf(q.size, cur + 100)) {
                    out += MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder().setMediaId("Q:$i").setTitle(Engine.title(q[i]))
                            .setSubtitle(Engine.artist(q[i])).build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                }
            }
            parentId == "local" -> Engine.ensureLoaded().take(300).forEach { out += playable(it) }
            parentId == "albums" -> Engine.ensureLoaded().map { Engine.album(it) }.distinct().sorted()
                .take(200).forEach { out += folder("album:$it", it) }
            parentId.startsWith("album:") -> {
                val a = parentId.removePrefix("album:")
                Engine.ensureLoaded().filter { Engine.album(it) == a }.take(200).forEach { out += playable(it, "AL:$a|") }
            }
            parentId == "artists" -> Engine.ensureLoaded().map { Engine.artist(it) }.distinct().sorted()
                .take(200).forEach { out += folder("artist:$it", it) }
            parentId.startsWith("artist:") -> {
                val a = parentId.removePrefix("artist:")
                Engine.ensureLoaded().filter { Engine.artist(it) == a }.take(200).forEach { out += playable(it, "AR:$a|") }
            }
            parentId == "online" -> Engine.streams().forEach { out += playable(it) }
            parentId == "fav" -> Lists.favSongs().take(300).forEach { out += playable(it, "FAV|") }
            parentId == "playlists" -> Lists.playlists.keys.forEach { out += folder("playlist:$it", it) }
            parentId.startsWith("playlist:") -> {
                val n = parentId.removePrefix("playlist:")
                Lists.songsOf(n).take(300).forEach { out += playable(it, "PL:$n|") }
            }
        }
        result.sendResult(out)
    }

    private fun folder(id: String, title: String) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private fun playable(s: Song, prefix: String = "") = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(prefix + s.id).setTitle(Engine.title(s))
            .setSubtitle(Engine.artist(s)).setMediaUri(s.uri).build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )

    // ---------- Trạng thái, hàng đợi, thẻ media ----------

    private fun updateState() {
        val st = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY ->
                if (player.playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        ACTION_FAV, "Yêu thích",
                        if (Engine.current?.let { Lists.isFav(it) } == true) R.drawable.ic_n_heart_on else R.drawable.ic_n_heart_off
                    ).build()
                )
                .addCustomAction(extraInfo().let { PlaybackStateCompat.CustomAction.Builder(it.second, it.third, it.first).build() })
                .setState(st, player.currentPosition, 1f, SystemClock.elapsedRealtime())
                .setActiveQueueItemId(player.currentMediaItemIndex.toLong())
                .build()
        )
        session.setShuffleMode(if (player.shuffleModeEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
        session.setRepeatMode(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
        )
        updateNotification()
    }

    /** Hàng đợi hiển thị trong Android Auto (20 bài trước, 100 bài sau bài đang phát). */
    private fun updateQueue() {
        val n = player.mediaItemCount
        if (n == 0) {
            session.setQueue(null)
            return
        }
        val cur = player.currentMediaItemIndex
        val items = ArrayList<MediaSessionCompat.QueueItem>()
        for (i in (cur - 20).coerceAtLeast(0) until (cur + 100).coerceAtMost(n)) {
            val mi = player.getMediaItemAt(i)
            val s = Engine.songById(mi.mediaId)
            val title = s?.let { Engine.title(it) } ?: mi.mediaMetadata.title?.toString() ?: "?"
            val sub = s?.let { Engine.artist(it) } ?: mi.mediaMetadata.artist?.toString() ?: ""
            val d = MediaDescriptionCompat.Builder().setMediaId(mi.mediaId).setTitle(title).setSubtitle(sub).build()
            items += MediaSessionCompat.QueueItem(d, i.toLong())
        }
        session.setQueue(items)
        session.setQueueTitle("Hàng đợi")
    }

    /**
     * Ảnh bìa: đang nối xe và có hình chụp từ DiOTO thì dùng hình đó, ngược lại dùng ảnh bìa thật.
     * Dòng nghệ sĩ: dòng lời đang hát (nếu bật), ngược lại là tên nghệ sĩ.
     */
    private fun pushMeta() {
        val s = Engine.current
        val title = s?.let { Engine.title(it) } ?: "AA Music"
        val base = s?.let { Engine.artist(it) } ?: ""
        val onCar = aaConnected
        val lyricOn = Engine.prefs.getBoolean("lock_lyrics", true) && lyricLine.isNotEmpty()
        val artist = if (lyricOn) lyricLine else base
        val key = "$title|$artist|$onCar|$capSeq|${Engine.artVersion}"
        if (key == lastKey) return
        lastKey = key
        val dur = player.duration.takeIf { it > 0 } ?: -1L
        val capLocal = capArt
        thread {
            val art = if (onCar && capLocal != null) capLocal else (Engine.loadArt(s, 512) ?: HudBitmap.placeholder())
            main.post {
                lastArt = art
                session.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, base)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                        .build()
                )
                if (!onCar) updateNotification()
            }
        }
    }

    override fun onDestroy() {
        Diag.service = "Dịch vụ: đã dừng"
        Engine.savePos()
        main.removeCallbacks(lyricRunnable)
        ScreenCapture.onFrame = null
        Engine.onExternalChange = null
        if (!ScreenCapture.manual) ScreenCapture.stop()
        Engine.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try { conn?.type?.removeObserver(connObserver) } catch (e: Throwable) { }
        player.removeListener(listener)
        session.release()
        super.onDestroy()
    }
}
