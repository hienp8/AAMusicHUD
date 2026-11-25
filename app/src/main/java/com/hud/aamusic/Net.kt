package com.hud.aamusic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/** Tìm thông tin bài hát (iTunes Search API) và lời bài hát (LRCLIB). Không cần API key. */
object Net {
    data class Track(val title: String, val artist: String, val album: String, val artUrl: String?, val thumbUrl: String? = null)
    data class Lyr(val text: String, val synced: Boolean, val source: String = "LRCLIB")

    private const val UA = "AAMusicHud/2.0 (https://github.com/hienp8/AAMusicHUD)"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", UA)
        return c
    }

    fun text(url: String): String? = try {
        val c = open(url)
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) {
        null
    }

    fun bytes(url: String): ByteArray? = try {
        val c = open(url)
        if (c.responseCode in 200..299) c.inputStream.readBytes() else null
    } catch (e: Exception) {
        null
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

    private val thumbCache = LruCache<String, Bitmap>(60)

    /** Tải ảnh từ URL (có nhớ tạm) để xem trước; gọi ngoài luồng chính. */
    fun thumb(url: String): Bitmap? {
        thumbCache.get(url)?.let { return it }
        val b = bytes(url) ?: return null
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return null
        thumbCache.put(url, bmp)
        return bmp
    }

    fun searchTracks(q: String): List<Track> {
        val t = text("https://itunes.apple.com/search?term=${enc(q)}&media=music&entity=song&limit=15")
            ?: return emptyList()
        return try {
            val arr = JSONObject(t).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<Track>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.str("artworkUrl100").ifEmpty { null }?.replace("100x100bb", "600x600bb")
                out += Track(o.str("trackName"), o.str("artistName"), o.str("collectionName"), art, o.str("artworkUrl100").ifEmpty { null })
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fold(x: String): String =
        Normalizer.normalize(x, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')

    /** Tìm rộng hơn: thử nhiều biến thể từ khoá (không dấu, bớt từ) và xếp theo độ giống. */
    fun searchLoose(q: String): List<Track> {
        val clean = q.trim()
        if (clean.isEmpty()) return emptyList()
        val noAccent = fold(clean)
        val words = noAccent.split(Regex("\\s+")).filter { it.isNotBlank() }
        val variants = LinkedHashSet<String>()
        variants += clean
        variants += noAccent
        if (words.size > 2) {
            variants += words.take(2).joinToString(" ")
            variants += words.takeLast(2).joinToString(" ")
        }
        if (words.size > 1) variants += words.first()
        val seen = HashSet<String>()
        val out = ArrayList<Track>()
        for (v in variants) {
            for (t in searchTracks(v)) {
                if (seen.add("${t.title}|${t.artist}")) out += t
            }
            if (out.size >= 14) break
        }
        val qw = words.map { it.lowercase() }.toSet()
        fun score(t: Track): Int {
            val tw = fold("${t.title} ${t.artist}").lowercase().split(Regex("\\s+")).toSet()
            return qw.count { it in tw }
        }
        return out.sortedByDescending { score(it) }.take(12)
    }

    // ---------------- Đề xuất bài hát tiếng Trung theo ca sĩ ----------------

    private fun itunesArtists(term: String, country: String): List<Pair<Long, String>> {
        val t = text("https://itunes.apple.com/search?term=${enc(term)}&media=music&entity=musicArtist&limit=8&country=$country")
            ?: return emptyList()
        return try {
            val arr = JSONObject(t).optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optLong("artistId", 0L)
                val n = o.str("artistName")
                if (id != 0L && Chinese.hasHan(n)) id to n else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun itunesArtistsFromSongs(term: String, country: String): List<Pair<Long, String>> {
        val t = text("https://itunes.apple.com/search?term=${enc(term)}&media=music&entity=song&limit=15&country=$country")
            ?: return emptyList()
        return try {
            val arr = JSONObject(t).optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optLong("artistId", 0L)
                val n = o.str("artistName")
                if (id != 0L && Chinese.hasHan(n)) id to n else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun artistSongs(id: Long): List<Track> {
        val t = text("https://itunes.apple.com/lookup?id=$id&entity=song&limit=60&country=cn") ?: return emptyList()
        return try {
            val arr = JSONObject(t).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<Track>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.str("wrapperType") != "track") continue
                val art = o.str("artworkUrl100").ifEmpty { null }?.replace("100x100bb", "600x600bb")
                out += Track(o.str("trackName"), o.str("artistName"), o.str("collectionName"), art, o.str("artworkUrl100").ifEmpty { null })
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun normPy(x: String) = fold(x).lowercase().filter { it.isLetterOrDigit() }

    private fun sim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.9
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1.0 - dp[b.length].toDouble() / maxOf(a.length, b.length)
    }

    /**
     * Từ tên ca sĩ (pinyin / Việt / Anh / Trung) tìm ca sĩ có tên tiếng Trung rồi liệt kê các bài của họ,
     * xếp lên đầu những bài có tên giống [titleHint] (so theo pinyin). Gọi ngoài luồng chính.
     */
    fun suggestChinese(artistQuery: String, titleHint: String): Pair<List<Track>, String> {
        val aq = artistQuery.trim()
        val th = titleHint.trim()
        if (aq.isEmpty() && th.isEmpty()) return Pair(emptyList(), "Chưa có tên ca sĩ hoặc tên bài để đề xuất")
        val artists = LinkedHashMap<Long, String>()
        val terms = LinkedHashSet<String>()
        if (aq.isNotEmpty()) {
            terms += aq
            terms += fold(aq)
        }
        for (country in listOf("cn", "tw", "hk")) {
            for (term in terms) for ((id, n) in itunesArtists(term, country)) artists.putIfAbsent(id, n)
            if (artists.size >= 3) break
        }
        if (artists.isEmpty()) {
            val comb = "$aq $th".trim()
            for (country in listOf("cn", "tw")) for ((id, n) in itunesArtistsFromSongs(comb, country)) artists.putIfAbsent(id, n)
        }
        if (artists.isEmpty()) return Pair(emptyList(), "Không tìm được ca sĩ có tên tiếng Trung từ \"$aq\"")
        val songs = ArrayList<Track>()
        val seen = HashSet<String>()
        for ((id, _) in artists.entries.take(3)) for (t in artistSongs(id)) if (seen.add(t.title + "|" + t.artist)) songs += t
        if (songs.isEmpty()) return Pair(emptyList(), "Ca sĩ ${artists.values.first()} chưa có bài nào trong kho tìm kiếm")

        // Xếp theo độ giống tên bài hiện tại
        val scores = HashMap<Track, Double>()
        if (th.isNotEmpty()) {
            if (Chinese.hasHan(th)) {
                for (t in songs) scores[t] = if (t.title == th) 1.0 else if (t.title.contains(th)) 0.9 else 0.0
            } else {
                val hint = normPy(th.replace(Regex("\\(.*\\)"), ""))
                for (t in songs.take(40)) {
                    val py = Gt.romanize(t.title)
                    scores[t] = if (py == null) 0.0 else sim(hint, normPy(py))
                    Thread.sleep(60)
                }
            }
        }
        val sorted = songs.withIndex().sortedWith(compareByDescending<IndexedValue<Track>> { scores[it.value] ?: 0.0 }.thenBy { it.index }).map { it.value }
        val best = scores.values.maxOrNull() ?: 0.0
        val msg = "Đề xuất theo ca sĩ ${artists.values.first()}: ${songs.size} bài" +
            (if (best >= 0.6) ", bài giống nhất đã xếp lên đầu. Chọn một bài bên dưới:" else ". Chọn bài đúng bên dưới:")
        return Pair(sorted, msg)
    }

    /** LRCLIB trước; nếu không có lời đồng bộ thì thử thêm Kugeci và Lyricsify (nếu bật trong Cài đặt). */
    fun lyricsAll(title: String, artist: String, album: String, durationSec: Long): Lyr? {
        val r = lyrics(title, artist, album, durationSec)
        if (r != null && r.synced) return r
        if (!Engine.prefs.getBoolean("web_src", true)) return r
        return LyricsWeb.fetch(title, artist) ?: r
    }

    private fun toLyr(o: JSONObject): Lyr? {
        val syn = o.str("syncedLyrics")
        if (syn.isNotBlank()) return Lyr(syn, true)
        val pl = o.str("plainLyrics")
        if (pl.isNotBlank()) return Lyr(pl, false)
        return null
    }

    fun lyrics(title: String, artist: String, album: String, durationSec: Long): Lyr? {
        val g = text(
            "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
                "&album_name=${enc(album)}&duration=$durationSec"
        )
        if (g != null) {
            try { toLyr(JSONObject(g))?.let { return it } } catch (e: Exception) { }
        }
        val s = text("https://lrclib.net/api/search?q=${enc("$title $artist")}") ?: return null
        return try {
            val arr = JSONArray(s)
            var plain: Lyr? = null
            for (i in 0 until arr.length()) {
                val l = toLyr(arr.getJSONObject(i)) ?: continue
                if (l.synced) return l
                if (plain == null) plain = l
            }
            plain
        } catch (e: Exception) {
            null
        }
    }
}
