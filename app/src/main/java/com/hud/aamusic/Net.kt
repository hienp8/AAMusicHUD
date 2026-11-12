package com.hud.aamusic

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/** Tìm thông tin bài hát (iTunes Search API) và lời bài hát (LRCLIB). Không cần API key. */
object Net {
    data class Track(val title: String, val artist: String, val album: String, val artUrl: String?)
    data class Lyr(val text: String, val synced: Boolean)

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

    fun searchTracks(q: String): List<Track> {
        val t = text("https://itunes.apple.com/search?term=${enc(q)}&media=music&entity=song&limit=15")
            ?: return emptyList()
        return try {
            val arr = JSONObject(t).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<Track>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.str("artworkUrl100").ifEmpty { null }?.replace("100x100bb", "600x600bb")
                out += Track(o.str("trackName"), o.str("artistName"), o.str("collectionName"), art)
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
