package com.hud.aamusic

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gửi lời tiếng Trung tới DeepSeek để thêm phiên âm pinyin và bản dịch tiếng Việt mượt mà.
 * Cần API key của bạn (Cài đặt > Nâng cao > Lời bài hát). Lời bài hát được gửi tới máy chủ DeepSeek.
 */
object DeepSeek {
    private var lastError = ""

    private fun callApi(key: String, lines: List<String>): List<Pair<String, String>>? {
        val sys = "Bạn là chuyên gia dịch lời bài hát tiếng Trung sang tiếng Việt. Bạn nhận một mảng JSON gồm các dòng lời. " +
            "Với MỖI dòng, trả về pinyin có dấu thanh (các âm tiết cách nhau bằng khoảng trắng, viết thường) " +
            "và bản dịch tiếng Việt mượt mà, tự nhiên, giữ ý thơ và cảm xúc của lời hát. " +
            "Chỉ trả về JSON đúng dạng {\"lines\":[{\"pinyin\":\"...\",\"vi\":\"...\"}]} " +
            "với số phần tử đúng bằng số dòng đầu vào và đúng thứ tự."
        val user = "Các dòng lời (JSON): " + JSONArray(lines).toString()
        val body = JSONObject()
            .put("model", "deepseek-chat")
            .put("temperature", 1.0)
            .put("stream", false)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", sys))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        return try {
            val c = URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection
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
                    JSONObject(txt).optJSONObject("error")?.optString("message").orEmpty().take(160)
                } catch (e: Exception) { txt.take(120) }
                lastError = "DeepSeek: HTTP $code" + (if (detail.isNotBlank()) " - $detail" else "") + when (code) {
                    401 -> " (API key sai)"
                    402 -> " (hết số dư tài khoản)"
                    429 -> " (gửi quá nhanh, thử lại sau)"
                    else -> ""
                }
                null
            } else {
                val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                val arr = JSONObject(content).getJSONArray("lines")
                if (arr.length() != lines.size) {
                    lastError = "DeepSeek trả về sai số dòng (${arr.length()}/${lines.size}), thử lại"
                    null
                } else {
                    (0 until arr.length()).map {
                        val o = arr.getJSONObject(it)
                        Pair(o.optString("pinyin"), o.optString("vi"))
                    }
                }
            }
        } catch (e: Exception) {
            lastError = "DeepSeek: lỗi ${e.javaClass.simpleName} ${e.message ?: ""}"
            null
        }
    }

    /**
     * Thêm dòng phiên âm và dòng dịch ngay dưới mỗi dòng có chữ Hán (cùng mốc thời gian với lời đồng bộ).
     * Gọi ngoài luồng chính. @return Pair(lời mới hoặc null, thông báo)
     */
    fun annotate(text: String): Pair<String?, String> {
        val key = Engine.prefs.getString("deepseek_key", "")?.trim().orEmpty()
        if (key.isEmpty()) return Pair(null, "Chưa nhập DeepSeek API key (Cài đặt > Nâng cao > Lời bài hát)")
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
            var res = callApi(key, chunk.map { src[it].second })
            if (res == null) {
                Thread.sleep(1500)
                res = callApi(key, chunk.map { src[it].second })
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
        return Pair(out.toString().trimEnd(), "Đã thêm phiên âm và bản dịch (DeepSeek)")
    }
}
