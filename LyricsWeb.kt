package com.hud.aamusic

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tìm lời bài hát có mốc thời gian (LRC) trên Kugeci và Lyricsify.
 * Mỗi lần chỉ tra khi người dùng yêu cầu, tối đa 3 trang mỗi nguồn, có nghỉ giữa các lần gọi.
 * Nếu trang trả lỗi 403/429/503 thì tạm ngừng nguồn đó 10 phút (không cố vượt qua).
 */
object LyricsWeb {
    private const val UA = "AAMusicHud/1.0 (https://github.com/hienp8/AAMusicHUD)"
    private val blockedUntil = HashMap<String, Long>()
    private val notes = ArrayList<String>()
    private val tsRe = Regex("^\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?\\]")
    private val linkRe = Regex("<a\\b[^>]*?href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private val dlRe = Regex("href=[\"']([^\"']*(?:\\.lrc|/lrc/|download)[^\"']*)[\"']", RegexOption.IGNORE_CASE)

    /** Ghi chú về các nguồn web đã thử (để hiện cho người dùng khi không tìm thấy). */
    fun lastNotes(): String = notes.joinToString("; ")

    private fun get(url: String): Pair<Int, String?> {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "vi,en;q=0.8,zh;q=0.6")
            val code = c.responseCode
            if (code in 200..299) Pair(code, c.inputStream.bufferedReader().readText()) else Pair(code, null)
        } catch (e: Exception) {
            Pair(-1, null)
        }
    }

    private fun block(name: String, code: Int) {
        if (code == 403 || code == 429 || code == 503) {
            blockedUntil[name] = System.currentTimeMillis() + 10L * 60L * 1000L
        }
    }

    private fun absUrl(base: String, href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> base + href
        else -> "$base/$href"
    }

    private fun plain(html: String): String =
        try { Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString() } catch (e: Throwable) { html }

    /** Lấy các dòng có mốc thời gian [mm:ss.xx] trong trang; cần ít nhất 6 dòng. */
    fun extractLrc(html: String): String? {
        val lines = plain(html).lines().map { it.trim() }.filter { tsRe.containsMatchIn(it) }
        return if (lines.size >= 6) lines.joinToString("\n") else null
    }

    private fun trySource(name: String, base: String, searchUrl: String, linkPattern: Regex, title: String): String? {
        if ((blockedUntil[name] ?: 0L) > System.currentTimeMillis()) {
            notes += "$name: đang tạm ngừng (bị chặn)"
            return null
        }
        val (code, html) = get(searchUrl)
        if (html == null) {
            block(name, code)
            notes += "$name: " + if (code > 0) "HTTP $code" else "không kết nối được"
            return null
        }
        val cands = ArrayList<Pair<String, String>>()
        for (m in linkRe.findAll(html)) {
            val href = m.groupValues[1]
            if (!linkPattern.containsMatchIn(href)) continue
            if (cands.any { it.first == href }) continue
            cands += href to plain(m.groupValues[2]).trim()
        }
        if (cands.isEmpty()) {
            notes += "$name: không có kết quả"
            return null
        }
        val ordered = cands
            .sortedByDescending { if (LibraryCheck.similar(it.second, title) || LibraryCheck.similar(it.first, title)) 1 else 0 }
            .take(3)
        for ((href, _) in ordered) {
            val (c2, page) = get(absUrl(base, href))
            if (page == null) {
                block(name, c2)
                continue
            }
            extractLrc(page)?.let { return it }
            val dl = dlRe.find(page)?.groupValues?.get(1)
            if (dl != null) {
                val (_, t) = get(absUrl(base, dl))
                if (t != null) extractLrc(t)?.let { return it }
            }
            Thread.sleep(500)
        }
        notes += "$name: có trang nhưng không thấy lời có mốc thời gian"
        return null
    }

    fun fetch(title: String, artist: String): Net.Lyr? {
        notes.clear()
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val han = (title + artist).any { Chinese.isHan(it) }
        val order = if (han) listOf("kugeci", "lyricsify") else listOf("lyricsify", "kugeci")
        for (src in order) {
            val r = if (src == "kugeci") {
                trySource("Kugeci", "https://www.kugeci.com", "https://www.kugeci.com/search?q=$q", Regex("/(song|lrc|lyric)s?/"), title)
            } else {
                trySource("Lyricsify", "https://www.lyricsify.com", "https://www.lyricsify.com/search?q=$q", Regex("/lyrics?/"), title)
            }
            if (r != null) return Net.Lyr(r, true, if (src == "kugeci") "Kugeci" else "Lyricsify")
        }
        return null
    }
}
