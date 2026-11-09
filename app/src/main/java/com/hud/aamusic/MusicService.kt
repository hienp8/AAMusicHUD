package com.hud.aamusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MusicService : MediaBrowserServiceCompat() {

    private data class Song(val id: String, val title: String, val artist: String, val uri: Uri)

    private lateinit var session: MediaSessionCompat
    private lateinit var player: ExoPlayer
    private lateinit var traffic: TrafficMonitor

    private val songs = mutableListOf<Song>()
    private val streams = mutableListOf<Song>()

    private var speedKmh = 0
    private var limit: Int? = null
    private var alert = ""
    private var lastKey = ""
    private var lastPush = 0L

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        session = MediaSessionCompat(this, "AAMusicHud").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player.play() }
                override fun onPause() { player.pause() }
                override fun onStop() { player.stop() }
                override fun onSkipToNext() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
                override fun onSkipToPrevious() { player.seekToPreviousMediaItem() }
                override fun onSeekTo(pos: Long) { player.seekTo(pos) }
                override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) { playId(mediaId) }
            })
            isActive = true
        }
        sessionToken = session.sessionToken

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { updateState() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { updateState() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { pushMeta(true) }
        })

        traffic = TrafficMonitor(this) { s, l, a ->
            val changed = (l != limit) || (a != alert)
            speedKmh = s
            limit = l
            alert = a
            pushMeta(changed)
        }
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
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        startForeground(1, n, type)
    }

    // ---------- Duyệt thư viện trên Android Auto ----------

    override fun onGetRoot(pkg: String, uid: Int, hints: Bundle?) = BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val out = mutableListOf<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            "root" -> {
                out += folder("local", "Nhạc trong máy")
                out += folder("online", "Radio online")
            }
            "local" -> { loadSongs(); songs.take(300).forEach { out += playable(it) } }
            "online" -> { loadStreams(); streams.forEach { out += playable(it) } }
        }
        result.sendResult(out)
    }

    private fun folder(id: String, title: String) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
    )

    private fun playable(s: Song) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(s.id).setTitle(s.title)
            .setSubtitle(s.artist).setMediaUri(s.uri).build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
    )

    private fun loadSongs() {
        songs.clear()
        val media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        try {
            contentResolver.query(
                media, proj, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    songs += Song("L:$id", c.getString(1) ?: "?", c.getString(2) ?: "",
                        ContentUris.withAppendedId(media, id))
                }
            }
        } catch (e: SecurityException) {
        }
    }

    private fun loadStreams() {
        streams.clear()
        val text = getSharedPreferences("aa", MODE_PRIVATE).getString("streams", "") ?: ""
        text.lines().forEachIndexed { i, line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank())
                streams += Song("R:$i", parts[0].trim(), "Radio", Uri.parse(parts[1].trim()))
        }
    }

    // ---------- Phát nhạc ----------

    private fun item(s: Song) = MediaItem.Builder()
        .setUri(s.uri)
        .setMediaId(s.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
        .build()

    private fun playId(id: String) {
        if (id.startsWith("L:")) {
            if (songs.isEmpty()) loadSongs()
            val idx = songs.indexOfFirst { it.id == id }
            if (idx < 0) return
            player.setMediaItems(songs.map { item(it) }, idx, 0L)
        } else {
            if (streams.isEmpty()) loadStreams()
            val s = streams.firstOrNull { it.id == id } ?: return
            player.setMediaItem(item(s))
        }
        player.prepare()
        player.play()
    }

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

    /**
     * Thẻ media trên Android Auto:
     *  - tiêu đề  = tên bài hát
     *  - dòng phụ = cảnh báo giao thông (nếu có), nếu không thì tên ca sĩ
     *  - ảnh bìa  = biển giới hạn tốc độ + tốc độ hiện tại
     */
    private fun pushMeta(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPush < 3000L) return
        val cur = player.currentMediaItem?.mediaMetadata
        val title = cur?.title?.toString() ?: "AA Music HUD"
        val artist = if (alert.isNotEmpty()) "⚠ $alert" else (cur?.artist?.toString() ?: "")
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
        traffic.stop()
        session.release()
        player.release()
        super.onDestroy()
    }
}
