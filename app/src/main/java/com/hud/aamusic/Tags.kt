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

/** Đọc / ghi tag. MP3 ghi bằng [Id3Writer] (giữ nguyên ảnh bìa); định dạng khác dùng jaudiotagger trên bản sao rồi kiểm tra. */
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

    /** Thay file gốc bằng file đã ghi, có bản dự phòng; lỗi thì khôi phục. @return null nếu thành công. */
    fun replaceFile(orig: File, tmp: File): String? {
        val bak = File(orig.parentFile, "." + orig.name + ".aabak")
        if (bak.exists()) bak.delete()
        if (!orig.renameTo(bak)) return "Không đổi tên được file gốc (cần quyền Quản lý tất cả tệp)"
        if (!tmp.renameTo(orig)) {
            bak.renameTo(orig)
            return "Không thay thế được file, đã giữ nguyên file gốc"
        }
        bak.delete()
        return null
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
            val f = File(path)
            if (!f.exists()) return "Không thấy file nhạc"
            var err: String? = null
            var done = false
            if (f.extension.equals("mp3", ignoreCase = true)) {
                try {
                    err = Id3Writer.write(path, title, artist, album, lyrics, cover)
                    done = true
                } catch (e: Id3Writer.Unsupported) {
                    done = false
                }
            }
            if (!done) err = writeWithJaudiotagger(f, title, artist, album, lyrics, cover)
            if (err == null) MediaScannerConnection.scanFile(Engine.app, arrayOf(path), null, null)
            err
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        }
    }

    private fun writeWithJaudiotagger(
        orig: File, title: String?, artist: String?, album: String?, lyrics: String?, cover: ByteArray?
    ): String? {
        val tmp = File(orig.parentFile, "." + orig.name + ".aatmp." + orig.extension)
        try {
            orig.copyTo(tmp, overwrite = true)
            val af = AudioFileIO.read(tmp)
            val hadCover = af.tag?.firstArtwork != null
            val tag = af.tagOrCreateAndSetDefault
            if (!title.isNullOrBlank()) tag.setField(FieldKey.TITLE, title.trim())
            if (!artist.isNullOrBlank()) tag.setField(FieldKey.ARTIST, artist.trim())
            if (!album.isNullOrBlank()) tag.setField(FieldKey.ALBUM, album.trim())
            if (!lyrics.isNullOrBlank()) tag.setField(FieldKey.LYRICS, lyrics)
            if (cover != null) {
                val img = File.createTempFile("cover", ".jpg", Engine.app.cacheDir)
                img.writeBytes(cover)
                val art = ArtworkFactory.createArtworkFromFile(img)
                tag.deleteArtworkField()
                tag.setField(art)
                img.delete()
            }
            af.commit()
            // Kiểm tra bản đã ghi trước khi thay file gốc
            val chk = AudioFileIO.read(tmp)
            val t2 = chk.tag ?: return "Sau khi ghi không đọc lại được tag, đã huỷ để bảo vệ file"
            if (hadCover && cover == null && t2.firstArtwork == null) {
                return "Ghi tag sẽ làm mất ảnh bìa, đã huỷ để bảo vệ file"
            }
            if (tmp.length() < orig.length() / 2) return "File sau khi ghi nhỏ bất thường, đã huỷ"
            return replaceFile(orig, tmp)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
