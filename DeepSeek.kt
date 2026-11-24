package com.hud.aamusic

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gọi dịch vụ AI (DeepSeek mặc định; có thể đổi sang Gemini, Groq... qua URL và model trong Cài đặt)
 * để thêm phiên âm pinyin, bản dịch tiếng Việt cho lời tiếng Trung và dịch tên bài / ca sĩ.
 * Nội dung gửi đi được xử lý bởi dịch vụ bạn chọn.
 */
object DeepSeek {
    private var lastError = ""

    private fun endpoint(): String =
        Engine.prefs.getString("ai_url", "")?.trim().orEmpty().ifEmpty { "https://api.deepseek.com/chat/completions" }

    private fun model(): String =
        Engine.prefs.getString("ai_model", "")?.trim().orEmpty().ifEmpty { "deepseek-chat" }

    private fun extractJson(content: String): JSONObject? {
        val i = content.indexOf('{')
        val j = content.lastIndexOf('}')
        if (i < 0 || j <= i) return null
        return try { JSONObject(content.substring(i, j + 1)) } catch (e: Exception) { null }
    }

    private fun hint(code: Int): String = when (code) {
        401 -> " (API key sai)"
        402 -> " (tài khoản chưa có số dư: cần nạp tiền, hoặc đổi sang Gemini / Groq miễn phí trong Cài đặt)"
        404 -> " (URL hoặc tên model sai)"
        429 -> " (gửi quá nhanh hoặc hết hạn mức, thử lại sau)"
        else -> ""
    }

    /** @return nội dung trả lời hoặc null (lỗi nằm trong lastError). */
    private fun chat(system: String, user: String): String? {
        val key = Engine.prefs.getString("deepseek_key", "")?.trim().orEmpty()
        if (key.isEmpty()) {
            lastError = "Chưa nhập API key (Cài đặt > Nâng cao)"
            return null
        }
        val url = endpoint()
        val body = JSONObject()
            .put("model", model())
            .put("temperature", 0.8)
            .put("stream", false)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        if (url.contains("deepseek")) body.put("response_format", JSONObject().put("type", "json_object"))
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 120000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $key")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                val detail = try {
                    val e = JSONObject(txt).opt("error")
                    when (e) {
                        is JSONObject -> e.optString("message")
                        is String -> e
                        else -> txt
                    }
                } catch (e: Exception) { txt }.take(160)
                lastError = "AI: HTTP $code" + (if (detail.isNotBlank()) " - $detail" else "") + hint(code)
                null
            } else {
                JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        } catch (e: Exception) {
            lastError = "AI: lỗi ${e.javaClass.simpleName} ${e.message ?: ""}"
            null
        }
    }

    private fun callApi(lines: List<String>): List<Pair<String, String>>? {
        val sys = "Bạn là chuyên gia dịch lời bài hát tiếng Trung sang tiếng Việt. Bạn nhận một mảng JSON gồm các dòng lời. " +
            "Với MỖI dòng, trả về pinyin có dấu thanh (các âm tiết cách nhau bằng khoảng trắng, viết thường) " +
            "và bản dịch tiếng Việt mượt mà, tự nhiên, giữ ý thơ và cảm xúc của lời hát. " +
            "Chỉ trả về JSON đúng dạng {\"lines\":[{\"pinyin\":\"...\",\"vi\":\"...\"}]} " +
            "với số phần tử đúng bằng số dòng đầu vào và đúng thứ tự."
        val content = chat(sys, "Các dòng lời (JSON): " + JSONArray(lines).toString()) ?: return null
        val arr = extractJson(content)?.optJSONArray("lines")
        if (arr == null) {
            lastError = "AI trả về không đúng định dạng"
            return null
        }
        if (arr.length() != lines.size) {
            lastError = "AI trả về sai số dòng (${arr.length()}/${lines.size}), thử lại"
            return null
        }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Pair(o.optString("pinyin"), o.optString("vi"))
        }
    }

    /**
     * Dịch tên bài hát và ca sĩ tiếng Trung sang tiếng Việt (tên người theo âm Hán-Việt).
     * Gọi ngoài luồng chính. @return Pair(Pair(tên bài, ca sĩ) hoặc null, thông báo)
     */
    fun translateNames(title: String, artist: String): Pair<Pair<String, String>?, String> {
        val sys = "Bạn là chuyên gia dịch tên bài hát và tên ca sĩ tiếng Trung sang tiếng Việt. " +
            "Tên người (ca sĩ) dịch theo âm Hán-Việt phổ biến (ví dụ 周杰伦 thành Châu Kiệt Luân, 邓紫棋 thành Đặng Tử Kỳ). " +
            "Tên bài hát dịch sang tiếng Việt tự nhiên (ưu tiên tên Hán-Việt hoặc tên Việt hoá quen thuộc nếu có). " +
            "Chỉ dịch phần chữ Hán, giữ nguyên phần chữ khác. " +
            "Chỉ trả về JSON dạng {\"title\":\"...\",\"artist\":\"...\"}."
        val user = "Dịch: " + JSONObject().put("title", title).put("artist", artist).toString()
        val content = chat(sys, user) ?: return Pair(null, lastError)
        val o = extractJson(content) ?: return Pair(null, "AI trả về không đúng định dạng")
        val t = o.optString("title").trim()
        val a = o.optString("artist").trim()
        if (t.isEmpty() && a.isEmpty()) return Pair(null, "AI không trả về tên")
        return Pair(Pair(t.ifEmpty { title }, a.ifEmpty { artist }), "ok")
    }

    /**
     * Thêm dòng phiên âm và dòng dịch ngay dưới mỗi dòng có chữ Hán (cùng mốc thời gian với lời đồng bộ).
     * Gọi ngoài luồng chính. @return Pair(lời mới hoặc null, thông báo)
     */
    fun annotate(text: String): Pair<String?, String> {
        val parsed = Lrc.parse(text)
        val synced = parsed.isNotEmpty()
        if (synced && parsed.any { it.extras.isNotEmpty() }) return Pair(null, "Lời đã có phiên âm và dịch")
        val src: List<Pair<Long?, String>> =
            if (synced) parsed.map { it.timeMs to it.text } else text.lines().map { null to it.trim() }
        val hanIdx = src.indices.filter { i -> src[i].second.any { Chinese.isHan(it) } }
        if (hanIdx.isEmpty()) return Pair(null, "Không có chữ Hán trong lời")

        val py = HashMap<Int, String>()
        val vi = HashMap<Int, String>()
        for (chunk in hanIdx.chunked(40)) {
            var res = callApi(chunk.map { src[it].second })
            if (res == null && lastError.startsWith("AI trả về")) {
                Thread.sleep(1200)
                res = callApi(chunk.map { src[it].second })
            }
            if (res == null) return Pair(null, lastError)
            for ((k, i) in chunk.withIndex()) {
                py[i] = res[k].first
                vi[i] = res[k].second
            }
        }

        val out = StringBuilder()
        for ((i, p) in src.withIndex()) {
            val t = p.first
            fun emit(s: String) {
                if (t != null) out.append(Chinese.fmtTag(t))
                out.append(s).append('\n')
            }
            emit(p.second)
            if (i in py) {
                py[i]?.takeIf { it.isNotBlank() }?.let { emit(it) }
                vi[i]?.takeIf { it.isNotBlank() }?.let { emit(it) }
            }
        }
        return Pair(out.toString().trimEnd(), "Đã thêm phiên âm và bản dịch")
    }
}
