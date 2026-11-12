package com.hud.aamusic

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tra giới hạn tốc độ từ Goong Speed Limit API (cần API key đã được Goong bật dịch vụ này).
 * Tài liệu: https://help.goong.io/kb/rest-api/speed-limit/speed-limit-canh-bao-gioi-han-toc-do/
 */
object Goong {
    /** @return Pair(giới hạn km/h hoặc null, thông báo trạng thái) */
    fun query(lat: Double, lon: Double, bearing: Float?, key: String): Pair<Int?, String> {
        if (key.isBlank()) return Pair(null, "Goong: chưa nhập API key")
        val b = if (bearing != null) "&bearing=$bearing" else ""
        val url = "https://speedlimit.goong.io/api/v1/speedlimit?lat=$lat&lon=$lon$b&api_key=${URLEncoder.encode(key, "UTF-8")}"
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            val code = c.responseCode
            if (code !in 200..299) {
                Pair(null, "Goong: HTTP $code" + if (code == 401 || code == 403) " (key chưa hợp lệ hoặc chưa được bật Speed Limit)" else "")
            } else {
                val o = JSONObject(c.inputStream.bufferedReader().readText())
                val ms = if (o.has("max_speed") && !o.isNull("max_speed")) o.optInt("max_speed", -1) else -1
                val distKm = o.optDouble("distance", 0.0)
                val name = o.optString("name", "")
                when {
                    ms <= 0 -> Pair(null, "Goong: không có dữ liệu giới hạn tại đây")
                    distKm > 0.15 -> Pair(null, "Goong: điểm dữ liệu gần nhất cách ${(distKm * 1000).toInt()} m, bỏ qua")
                    else -> Pair(ms, "Goong: OK, $ms km/h ($name)")
                }
            }
        } catch (e: Exception) {
            Pair(null, "Goong: lỗi ${e.javaClass.simpleName}")
        }
    }
}
