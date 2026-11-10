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
    val streaming: Boolean = false
)

data class LrcLine(val timeMs: Long, val text: String)

data class EqInfo(val min: Int, val max: Int, val freqsHz: List<Int>, val presets: List<String>)

/** Trạng thái chẩn đoán hiển thị trong Cài đặt, giúp biết lỗi nằm ở dịch vụ, GPS hay mạng. */
object Diag {
    var service by mutableStateOf("Dịch vụ: chưa chạy")
    var gps by mutableStateOf("GPS: chưa có tín hiệu")
    var osmCam by mutableStateOf("OSM camera: chưa tải")
    var osmLim by mutableStateOf("OSM giới hạn đường: chưa tải")
    var test by mutableStateOf("")
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
}
