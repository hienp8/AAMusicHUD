package com.hud.aamusic

import android.media.MediaScannerConnection
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val streamUrl: String,
    val downloadUrl: String,
    val imageUrl: String?,
    val downloadAllowed: Boolean,
    val license: String
)

/**
 * Nhạc online hợp pháp: kho nhạc Creative Commons của Jamendo (API chính thức, cần Client ID miễn phí
 * tại developer.jamendo.com). Nghe trực tiếp và tải về với nhiều mức chất lượng nếu tác giả cho phép.
 */
object Online {
    val QUALITIES = listOf(
        "mp31" to "MP3 96 kbps (nhỏ, tiết kiệm dung lượng)",
        "mp32" to "MP3 chất lượng cao (VBR)",
        "ogg" to "OGG Vorbis",
        "flac" to "FLAC (không nén, dung lượng lớn)"
    )

    private fun clientId() = Engine.prefs.getString("jamendo_id", "")?.trim().orEmpty()

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

    /** @return Pair(danh sách hoặc null, thông báo lỗi). Gọi ngoài luồng chính. */
    fun search(q: String) = fetch("&search=" + URLEncoder.encode(q, "UTF-8"))

    /** Bảng xếp hạng tuần (có thể lọc theo thể loại/tag). */
    fun popular(tag: String? = null) =
        fetch("&order=popularity_week" + (if (tag != null) "&tags=" + URLEncoder.encode(tag, "UTF-8") else ""))

    fun fetch(extra: String): Pair<List<OnlineTrack>?, String> {
        val id = clientId()
        if (id.isEmpty()) return Pair(null, "Chưa nhập Jamendo Client ID (Cài đặt > Nâng cao)")
        val url = "https://api.jamendo.com/v3.0/tracks/?client_id=${URLEncoder.encode(id, "UTF-8")}&format=json&limit=30" +
            "&audioformat=mp32&include=licenses$extra"
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "AAMusicHud/1.0")
            val code = c.responseCode
            if (code !in 200..299) return Pair(null, "Jamendo: HTTP $code")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val arr = o.optJSONArray("results") ?: return Pair(emptyList(), "Không có kết quả")
            val out = ArrayList<OnlineTrack>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                out += OnlineTrack(
                    t.str("id"), t.str("name"), t.str("artist_name"), t.str("album_name"),
                    t.optLong("duration", 0L), t.str("audio"), t.str("audiodownload"),
                    t.str("image").ifEmpty { t.str("album_image") }.ifEmpty { null },
                    t.optBoolean("audiodownload_allowed", false), t.str("license_ccurl")
                )
            }
            Pair(out, if (out.isEmpty()) "Không có kết quả" else "${out.size} kết quả (nhạc Creative Commons)")
        } catch (e: Exception) {
            Pair(null, "Jamendo: lỗi ${e.javaClass.simpleName}")
        }
    }

    fun toSong(t: OnlineTrack) = Song(
        "O:${t.id}", t.title, t.artist, t.album, android.net.Uri.parse(t.streamUrl),
        t.durationSec * 1000L, true, "", t.imageUrl ?: ""
    )

    private fun downloadLink(t: OnlineTrack, fmt: String): String {
        val u = t.downloadUrl
        val re = Regex("/(mp31|mp32|ogg|flac)/?$")
        return if (re.containsMatchIn(u)) u.replace(re, "/$fmt/")
        else u + (if (u.contains("?")) "&" else "?") + "audioformat=$fmt"
    }

    /** Tải về thư mục Music/AA Music. @return null nếu thành công, ngược lại là thông báo lỗi. */
    fun download(t: OnlineTrack, fmt: String, status: (String) -> Unit): String? {
        if (!t.downloadAllowed || t.downloadUrl.isEmpty()) return "Tác giả không cho phép tải bài này"
        if (!Tags.canWrite()) return "Cần cấp quyền Quản lý tất cả tệp (Cài đặt > Nâng cao)"
        return try {
            val ext = if (fmt.startsWith("mp3")) "mp3" else fmt
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "AA Music")
            dir.mkdirs()
            val safe = "${t.artist} - ${t.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
            val out = File(dir, "$safe.$ext")
            val c = URL(downloadLink(t, fmt)).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "AAMusicHud/1.0")
            if (c.responseCode !in 200..299) return "Tải lỗi: HTTP ${c.responseCode}"
            val total = c.contentLengthLong
            var done = 0L
            var last = 0L
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        o.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 400) {
                            last = now
                            status(if (total > 0) "Đang tải ${t.title}: ${done * 100 / total}%" else "Đang tải ${t.title}: ${done / 1024} KB")
                        }
                    }
                }
            }
            if (out.length() < 10_000L) { out.delete(); return "File tải về không hợp lệ" }
            if (ext == "mp3") {
                val cover = t.imageUrl?.let { Net.bytes(it) }
                Tags.write(out.path, t.title, t.artist, t.album, null, cover)
            }
            MediaScannerConnection.scanFile(Engine.app, arrayOf(out.path), null) { _, _ -> Engine.loadSongsAsync() }
            null
        } catch (e: Exception) {
            "Tải lỗi: ${e.javaClass.simpleName} ${e.message ?: ""}"
        }
    }
}
