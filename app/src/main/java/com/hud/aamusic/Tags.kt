package com.hud.aamusic

import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Đọc / ghi tag ID3 (MP3), và các định dạng khác mà jaudiotagger hỗ trợ (FLAC, M4A, OGG...). */
object Tags {
    data class Info(val title: String, val artist: String, val album: String, val lyrics: String, val hasCover: Boolean)

    init {
        try { Logger.getLogger("org.jaudiotagger").level = Level.OFF } catch (e: Exception) { }
    }

    /** Android 11 trở lên cần quyền "Quản lý tất cả tệp" để ghi trực tiếp vào file nhạc. */
    fun canWrite(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun read(path: String): Info? {
        return try {
            val af = AudioFileIO.read(File(path))
            val t = af.tag ?: return Info("", "", "", "", false)
            Info(
                t.getFirst(FieldKey.TITLE) ?: "",
                t.getFirst(FieldKey.ARTIST) ?: "",
                t.getFirst(FieldKey.ALBUM) ?: "",
                t.getFirst(FieldKey.LYRICS) ?: "",
                t.firstArtwork != null
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun readCover(path: String): ByteArray? {
        return try {
            AudioFileIO.read(File(path)).tag?.firstArtwork?.binaryData
        } catch (e: Throwable) {
            null
        }
    }

    /** @return null nếu thành công, ngược lại là thông báo lỗi. Trường rỗng/null sẽ không bị ghi đè. */
    fun write(
        path: String,
        title: String?,
        artist: String?,
        album: String?,
        lyrics: String?,
        cover: ByteArray?
    ): String? {
        return try {
            val af = AudioFileIO.read(File(path))
            val tag = af.tagOrCreateAndSetDefault
            if (!title.isNullOrBlank()) tag.setField(FieldKey.TITLE, title.trim())
            if (!artist.isNullOrBlank()) tag.setField(FieldKey.ARTIST, artist.trim())
            if (!album.isNullOrBlank()) tag.setField(FieldKey.ALBUM, album.trim())
            if (!lyrics.isNullOrBlank()) tag.setField(FieldKey.LYRICS, lyrics)
            if (cover != null) {
                val tmp = File.createTempFile("cover", ".jpg", Engine.app.cacheDir)
                tmp.writeBytes(cover)
                val art = ArtworkFactory.createArtworkFromFile(tmp)
                tag.deleteArtworkField()
                tag.setField(art)
                tmp.delete()
            }
            af.commit()
            MediaScannerConnection.scanFile(Engine.app, arrayOf(path), null, null)
            null
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        }
    }
}
