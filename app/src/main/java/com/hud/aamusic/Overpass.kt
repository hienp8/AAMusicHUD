package com.hud.aamusic

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Gọi Overpass API, tự thử lần lượt nhiều máy chủ và ghi lại lý do lỗi. */
object Overpass {
    private val mirrors = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    class Res(val elements: JSONArray?, val log: String)

    fun query(q: String): Res {
        val log = StringBuilder()
        for (m in mirrors) {
            val host = URL(m).host
            try {
                val c = URL(m).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 6000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "AAMusicHud/2.0 (https://github.com/hienp8/AAMusicHUD)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write("data=${URLEncoder.encode(q, "UTF-8")}".toByteArray()) }
                val code = c.responseCode
                if (code in 200..299) {
                    val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("elements")
                    return Res(arr, host)
                }
                log.append("$host: HTTP $code; ")
            } catch (e: Exception) {
                log.append("$host: ${e.javaClass.simpleName} ${e.message?.take(60) ?: ""}; ")
            }
        }
        return Res(null, log.toString())
    }

    /** Dùng cho nút "Kiểm tra kết nối OSM" trong Cài đặt. */
    fun test(): String {
        val r = query("[out:json][timeout:15];node(around:3000,21.0285,105.8542)[\"highway\"=\"speed_camera\"];out;")
        return if (r.elements != null) "Kết nối OK qua ${r.log}: ${r.elements.length()} camera trong 3 km quanh trung tâm Hà Nội."
        else "KHÔNG kết nối được: ${r.log}"
    }
}
