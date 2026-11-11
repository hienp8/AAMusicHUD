package com.hud.aamusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

class MusicService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private lateinit var traffic: TrafficMonitor
    private lateinit var speaker: Speaker

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
        traffic.start()
        Diag.service = "Dịch vụ: đang chạy"
        DataSync.syncAsync(false)
        updateState()
        pushMeta(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        traffic.start()
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("aa", "AA Music HUD", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "aa")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AA Music HUD đang chạy")
            .build()
        if (Build.VERSION.SDK_INT < 29) {
            startForeground(1, n)
            return
        }
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            startForeground(1, n, type)
        } catch (e: Exception) {
            try { startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) } catch (e2: Exception) { }
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
    }

    private fun pushMeta(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPush < 3000L) return
        val s = Engine.current
        val title = s?.let { Engine.title(it) } ?: "AA Music HUD"
        val base = s?.let { Engine.artist(it) } ?: ""
        val artist = if (alert.isNotEmpty()) "⚠ $alert" else base
        val rounded = (speedKmh / 2) * 2
        val key = "$title|$artist|$rounded|$limit"
        if (key == lastKey) return
        lastKey = key
        lastPush = now
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.duration.takeIf { it > 0 } ?: -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, HudBitmap.render(speedKmh, limit, alert))
                .build()
        )
    }

    override fun onDestroy() {
        Diag.service = "Dịch vụ: đã dừng"
        player.removeListener(listener)
        traffic.stop()
        speaker.shutdown()
        session.release()
        super.onDestroy()
    }
}
