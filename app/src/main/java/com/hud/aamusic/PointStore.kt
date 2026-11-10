package com.hud.aamusic

import android.content.Context
import java.io.File

data class Pt(
    val lat: Double,
    val lon: Double,
    val type: Int,
    val limit: Int?,
    val name: String
) {
    companion object {
        const val CAM = 0
        const val SIGN = 1
        const val WARN = 2
    }
}

/**
 * Dữ liệu offline do người dùng nhập, lưu trong bộ nhớ riêng của app:
 *  - alerts.csv : camera / điểm cảnh báo (mặc định mỗi dòng là camera)
 *  - signs.csv  : biển giới hạn tốc độ (mặc định mỗi dòng là biển)
 *
 * Định dạng dòng (phân cách bằng , ; hoặc tab; dòng bắt đầu bằng # và dòng tiêu đề bị bỏ qua):
 *   vĩ độ, kinh độ
 *   vĩ độ, kinh độ, giới hạn
 *   vĩ độ, kinh độ, loại, giới hạn, tên      (loại: camera | sign | warn)
 */
class PointStore(private val ctx: Context) {
    private fun alertsFile() = File(ctx.filesDir, "alerts.csv")
    private fun signsFile() = File(ctx.filesDir, "signs.csv")

    fun fileFor(kind: String): File = if (kind == "signs") signsFile() else alertsFile()

    fun stamp(): Long = alertsFile().lastModified() + signsFile().lastModified()

    fun clear() {
        alertsFile().delete()
        signsFile().delete()
    }

    /** @return Pair(điểm cảnh báo, biển giới hạn) */
    fun load(): Pair<List<Pt>, List<Pt>> {
        val all = ArrayList<Pt>()
        if (alertsFile().exists()) all += parse(alertsFile().readText(Charsets.UTF_8), Pt.CAM)
        if (signsFile().exists()) all += parse(signsFile().readText(Charsets.UTF_8), Pt.SIGN)
        return Pair(all.filter { it.type != Pt.SIGN }, all.filter { it.type == Pt.SIGN })
    }

    companion object {
        private fun parseLimit(s: String?): Int? =
            s?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

        fun parse(text: String, defaultType: Int): List<Pt> {
            val out = ArrayList<Pt>()
            for (raw in text.lineSequence()) {
                val line = raw.trim().trimStart('\uFEFF')
                if (line.isEmpty() || line.startsWith("#")) continue
                val f = line.split(',', ';', '\t').map { it.trim().trim('"') }
                val lat = f.getOrNull(0)?.toDoubleOrNull() ?: continue
                val lon = f.getOrNull(1)?.toDoubleOrNull() ?: continue
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue

                val f2 = f.getOrNull(2) ?: ""
                if (f2.isEmpty() || f2.toIntOrNull() != null) {
                    out += Pt(lat, lon, defaultType, parseLimit(f2), f.getOrNull(3) ?: "")
                } else {
                    val type = when (f2.lowercase()) {
                        "camera", "cam" -> Pt.CAM
                        "sign", "limit", "bien" -> Pt.SIGN
                        else -> Pt.WARN
                    }
                    val name = f.getOrNull(4)?.takeIf { it.isNotBlank() } ?: f2
                    out += Pt(lat, lon, type, parseLimit(f.getOrNull(3)), name)
                }
            }
            return out
        }
    }
}
