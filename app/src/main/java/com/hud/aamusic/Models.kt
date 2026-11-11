package com.hud.aamusic

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val durationMs: Long,
    val streaming: Boolean = false,
    val path: String = ""
)

data class LrcLine(val timeMs: Long, val text: String)

data class EqInfo(val min: Int, val max: Int, val freqsHz: List<Int>, val presets: List<String>)

/** Kết quả đọc tag của một file nhạc. lyricKind: 0 = chưa có lời, 1 = lời thường, 2 = lời đồng bộ. */
data class TagState(
    val id: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val hasCover: Boolean,
    val lyricKind: Int,
    val ok: Boolean
) {
    fun missing(): List<String> {
        val m = ArrayList<String>()
        if (title.isBlank()) m += "tên bài"
        if (artist.isBlank()) m += "nghệ sĩ"
        if (album.isBlank()) m += "album"
        if (!hasCover) m += "bìa"
        if (lyricKind == 0) m += "lời" else if (lyricKind == 1) m += "lời đồng bộ"
        return m
    }
}

/** Đề xuất bổ sung tag cho một bài (chỉ chứa những trường đang thiếu). */
data class Proposal(
    val songId: String,
    val label: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artUrl: String?,
    val lyrics: Net.Lyr?
) {
    fun summary(): String {
        val s = ArrayList<String>()
        if (title != null) s += "tên bài: $title"
        if (artist != null) s += "nghệ sĩ: $artist"
        if (album != null) s += "album: $album"
        if (artUrl != null) s += "bìa album"
        if (lyrics != null) s += if (lyrics.synced) "lời đồng bộ" else "lời thường"
        return "Thêm " + s.joinToString(", ")
    }
}

/** Trạng thái chẩn đoán hiển thị trong Cài đặt. */
object Diag {
    var service by mutableStateOf("Dịch vụ: chưa chạy")
    var gps by mutableStateOf("GPS: chưa có tín hiệu")
    var aa by mutableStateOf("Android Auto: chưa kiểm tra")
    var osmCam by mutableStateOf("OSM camera: chưa tải")
    var osmLim by mutableStateOf("OSM giới hạn đường: chưa tải")
    var test by mutableStateOf("")
    var sync by mutableStateOf("Dữ liệu từ URL: chưa cập nhật")
}

object Lrc {
    private val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(text: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        for (line in text.lines()) {
            val ms = tag.findAll(line).toList()
            if (ms.isEmpty()) continue
            val content = line.substring(ms.last().range.last + 1).trim()
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fr = m.groupValues[3]
                val frac = if (fr.isEmpty()) 0L else fr.padEnd(3, '0').take(3).toLong()
                out += LrcLine(min * 60000L + sec * 1000L + frac, content)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** 0 = rỗng, 1 = lời thường, 2 = có mốc thời gian. */
    fun kind(text: String): Int {
        if (text.isBlank()) return 0
        return if (parse(text).isNotEmpty()) 2 else 1
    }
}
