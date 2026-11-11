package com.hud.aamusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.concurrent.thread

class MusicService : MediaBrowserServiceCompat() {

    companion object {
        const val ACTION_PREV = "com.hud.aamusic.PREV"
        const val ACTION_TOGGLE = "com.hud.aamusic.TOGGLE"
        const val ACTION_NEXT = "com.hud.aamusic.NEXT"
        const val ACTION_QUIT = "com.hud.aamusic.QUIT"
    }

    private lateinit var session: MediaSessionCompat
    private lateinit var traffic: TrafficMonitor
    private lateinit var speaker: Speaker
    private val main = Handler(Looper.getMainLooper())

    private var conn: CarConnection? = null
    private var aaConnected = false
    private var inForeground = false
    private var lastArt: Bitmap? = null

    private var speedKmh = 0
    private var limit: Int? = null
    private var alert = ""
    private var lastKey = ""
    private var lastPush = 0L

    private val player get() = Engine.player

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) { updateState() }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateState()
            if (playWhenReady) startFg()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { pushMeta(true) }
    }

    private val connObserver = Observer<Int> { t -> onCarType(t) }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "loc_mode") updateLocation()
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
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        player.addListener(listener)

        speaker = Speaker(this)
        traffic = TrafficMonitor(
            this,
            onUpdate = { s, l, a ->
                val changed = (l != limit) || (a != alert)
                speedKmh = s
                limit = l
                alert = a
                pushMeta(changed)
            },
            onSpeak = { text ->
                if (getSharedPreferences("aa", MODE_PRIVATE).getBoolean("tts", true)) speaker.speak(text)
            }
        )

        // Chỉ bật GPS khi điện thoại đang kết nối Android Auto (hoặc khi người dùng chọn "Luôn dùng GPS")
        Engine.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        try {
            conn = CarConnection(applicationContext).also { it.type.observeForever(connObserver) }
        } catch (e: Throwable) {
            Diag.aa = "Android Auto: không kiểm tra được (${e.javaClass.simpleName}), hãy bật \"Luôn dùng GPS\""
        }

        Diag.service = "Dịch vụ: đang chạy"
        DataSync.syncAsync(false)
        updateState()
        pushMeta(true)
        updateLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
                updateLocation()
            }
        }
        return START_STICKY
    }

    // ---------- Vị trí: chỉ chạy khi cần ----------

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
            pushMeta(true)
        }
        updateLocation()
    }

    private fun updateLocation() {
        val always = Engine.prefs.getInt("loc_mode", 0) == 1
        if (always || aaConnected) traffic.start() else traffic.stop()
    }

    // ---------- Thông báo kiểu điều khiển nhạc (thanh trạng thái / màn hình khoá) ----------

    private fun actionIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(): Notification {
        val s = Engine.current
        val playing = player.isPlaying
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, "aa")
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(s?.let { Engine.title(it) } ?: "AA Music")
            .setContentText(s?.let { Engine.artist(it) } ?: "Sẵn sàng phát nhạc")
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Trước", actionIntent(ACTION_PREV, 1))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Tạm dừng" else "Phát", actionIntent(ACTION_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Tiếp", actionIntent(ACTION_NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Đóng", actionIntent(ACTION_QUIT, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        lastArt?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("aa", "AA Music", NotificationManager.IMPORTANCE_LOW))
        val n = buildNotification()
        if (Build.VERSION.SDK_INT < 29) {
            startForeground(1, n)
            inForeground = true
            return
        }
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            startForeground(1, n, type)
            inForeground = true
        } catch (e: Exception) {
            try {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                inForeground = true
            } catch (e2: Exception) { }
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
                out += folder("local", "Bài hát")
                out += folder("albums", "Album")
                out += folder("online", "Radio online")
            }
            parentId == "local" -> Engine.ensureLoaded().take(300).forEach { out += playable(it) }
            parentId == "albums" -> Engine.ensureLoaded().map { Engine.album(it) }.distinct().sorted()
                .take(200).forEach { out += folder("album:$it", it) }
            parentId.startsWith("album:") -> {
                val a = parentId.removePrefix("album:")
                Engine.ensureLoaded().filter { Engine.album(it) == a }.take(200).forEach { out += playable(it) }
            }
            parentId == "online" -> Engine.streams().forEach { out += playable(it) }
        }
        result.sendResult(out)
    }

    private fun folder(id: String, title: String) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private fun playable(s: Song) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(s.id).setTitle(Engine.title(s))
            .setSubtitle(Engine.artist(s)).setMediaUri(s.uri).build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )

    // ---------- Trạng thái và thẻ media ----------

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
                        PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(st, player.currentPosition, 1f, SystemClock.elapsedRealtime())
                .build()
        )
        updateNotification()
    }

    /**
     * Đang nối Android Auto: ảnh bìa = biển tốc độ + cảnh báo.
     * Không nối xe: ảnh bìa thật của bài hát (hiện trên thông báo và màn hình khoá).
     */
    private fun pushMeta(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPush < 3000L) return
        val s = Engine.current
        val title = s?.let { Engine.title(it) } ?: "AA Music"
        val base = s?.let { Engine.artist(it) } ?: ""
        val onCar = aaConnected
        val artist = if (onCar && alert.isNotEmpty()) "⚠ $alert" else base
        val rounded = (speedKmh / 2) * 2
        val key = if (onCar) "$title|$artist|$rounded|$limit|car" else "$title|$artist|phone|${Engine.artVersion}"
        if (key == lastKey) return
        lastKey = key
        lastPush = now
        val spd = speedKmh
        val lim = limit
        val al = alert
        val dur = player.duration.takeIf { it > 0 } ?: -1L
        thread {
            val art = if (onCar) HudBitmap.render(spd, lim, al) else (Engine.loadArt(s, 512) ?: HudBitmap.placeholder())
            main.post {
                lastArt = art
                session.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                        .build()
                )
                if (!onCar || force) updateNotification()
            }
        }
    }

    override fun onDestroy() {
        Diag.service = "Dịch vụ: đã dừng"
        Engine.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try { conn?.type?.removeObserver(connObserver) } catch (e: Throwable) { }
        player.removeListener(listener)
        traffic.release()
        speaker.shutdown()
        session.release()
        super.onDestroy()
    }
}
