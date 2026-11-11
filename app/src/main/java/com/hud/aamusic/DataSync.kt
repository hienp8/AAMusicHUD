package com.hud.aamusic

import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.concurrent.thread

/**
 * Tải dữ liệu camera / biển giới hạn tốc độ (CSV) từ một URL, mặc định là file do GitHub Actions
 * trong repo của bạn tự cập nhật từ OpenStreetMap. Cách này hoạt động ngay cả khi điện thoại
 * không truy cập được Overpass API.
 */
object DataSync {
    const val DEF_ALERTS = "https://raw.githubusercontent.com/hienp8/AAMusicHUD/main/data/vn_cameras.csv"
    const val DEF_SIGNS = "https://raw.githubusercontent.com/hienp8/AAMusicHUD/main/data/vn_signs.csv"

    private val main = Handler(Looper.getMainLooper())

    fun urlAlerts(): String = Engine.prefs.getString("url_alerts", DEF_ALERTS) ?: DEF_ALERTS
    fun urlSigns(): String = Engine.prefs.getString("url_signs", DEF_SIGNS) ?: DEF_SIGNS

    fun syncAsync(force: Boolean) {
        val prefs = Engine.prefs
        val last = prefs.getLong("last_sync", 0L)
        if (!force && System.currentTimeMillis() - last < 24L * 3600L * 1000L) return
        main.post { Diag.sync = "Dữ liệu từ URL: đang tải..." }
        thread {
            val store = PointStore(Engine.app)
            val sb = StringBuilder()
            var okCount = 0
            for ((kind, url) in listOf("alerts" to urlAlerts(), "signs" to urlSigns())) {
                if (url.isBlank()) continue
                val label = if (kind == "alerts") "camera/cảnh báo" else "biển giới hạn"
                val b = Net.bytes(url)
                val head = b?.take(64)?.toByteArray()?.toString(Charsets.UTF_8)?.trim()?.lowercase() ?: ""
                if (b == null || b.isEmpty() || head.startsWith("<")) {
                    sb.append("$label: không tải được; ")
                } else {
                    val f = store.onlineFile(kind)
                    val tmp = File(f.path + ".tmp")
                    tmp.writeBytes(b)
                    tmp.renameTo(f)
                    sb.append("$label: ${b.size / 1024} KB; ")
                    okCount++
                }
            }
            if (okCount > 0) prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
            main.post { Diag.sync = "Dữ liệu từ URL: $sb" }
        }
    }
}
