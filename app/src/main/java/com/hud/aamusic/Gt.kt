package com.hud.aamusic

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google dịch (điểm cuối công khai, không cần API key): phiên âm pinyin và dịch tiếng Trung sang tiếng Việt. */
object Gt {
    /** @return Pair(pinyin hoặc null, bản dịch tiếng Việt hoặc null). Gọi ngoài luồng chính. */
    fun both(text: String): Pair<String?, String?> {
        if (text.isBlank()) return Pair(null, null)
        return try {
            val u = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=vi&dt=t&dt=rm&q=" +
                URLEncoder.encode(text, "UTF-8")
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (c.responseCode !in 200..299) return Pair(null, null)
            val segs = JSONArray(c.inputStream.bufferedReader().readText()).getJSONArray(0)
            val tr = StringBuilder()
            var rm: String? = null
            for (i in 0 until segs.length()) {
                val sg = segs.optJSONArray(i) ?: continue
                if (!sg.isNull(0)) {
                    tr.append(sg.optString(0))
                } else {
                    val r = when {
                        sg.length() > 2 && !sg.isNull(2) -> sg.optString(2)
                        sg.length() > 3 && !sg.isNull(3) -> sg.optString(3)
                        else -> ""
                    }
                    if (r.isNotBlank()) rm = if (rm == null) r else "$rm $r"
                }
            }
            Pair(rm?.trim(), tr.toString().trim().ifEmpty { null })
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    fun romanize(text: String): String? = both(text).first
}
