package com.hud.aamusic

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/** Nhận biết lời tiếng Trung và thêm phiên âm pinyin có dấu thanh. */
object Chinese {
    fun isHan(ch: Char): Boolean = ch in '\u4e00'..'\u9fff' || ch in '\u3400'..'\u4dbf'

    fun isChinese(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val han = text.count { isHan(it) }
        val kana = text.count { it in '\u3040'..'\u30ff' }
        return han >= 6 && han * 100 / letters >= 40 && kana * 10 < han
    }

    private fun fmtTag(ms: Long): String {
        val m = ms / 60000
        val s = (ms / 1000) % 60
        val cs = (ms % 1000) / 10
        return "[%02d:%02d.%02d]".format(m, s, cs)
    }

    fun pinyin(line: String): String {
        val fmt = HanyuPinyinOutputFormat().apply {
            caseType = HanyuPinyinCaseType.LOWERCASE
            toneType = HanyuPinyinToneType.WITH_TONE_MARK
            vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
        }
        val sb = StringBuilder()
        var prevHan = false
        for (ch in line) {
            if (isHan(ch)) {
                val py = try { PinyinHelper.toHanyuPinyinStringArray(ch, fmt)?.firstOrNull() } catch (e: Throwable) { null }
                if (py != null) {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    sb.append(py)
                    prevHan = true
                } else {
                    sb.append(ch)
                    prevHan = false
                }
            } else if (ch.isWhitespace()) {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                prevHan = false
            } else {
                if (prevHan && ch.isLetterOrDigit() && sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                sb.append(ch)
                prevHan = false
            }
        }
        return sb.toString().trim()
    }

    /**
     * Thêm dòng phiên âm ngay dưới mỗi dòng có chữ Hán (cùng mốc thời gian với lời đồng bộ).
     * Trả về null nếu không có gì để thêm hoặc lời đã có phiên âm từ trước.
     */
    fun annotate(text: String, withPinyin: Boolean): String? {
        if (!withPinyin) return null
        val parsed = Lrc.parse(text)
        val synced = parsed.isNotEmpty()
        if (synced && parsed.any { it.extras.isNotEmpty() }) return null
        val src: List<Pair<Long?, String>> =
            if (synced) parsed.map { it.timeMs to it.text } else text.lines().map { null to it.trim() }
        val out = StringBuilder()
        var added = false
        for (p in src) {
            val t = p.first
            val line = p.second
            fun emit(s: String) {
                if (t != null) out.append(fmtTag(t))
                out.append(s).append('\n')
            }
            emit(line)
            if (line.any { isHan(it) }) {
                val py = pinyin(line)
                if (py.isNotBlank()) { emit(py); added = true }
            }
        }
        return if (added) out.toString().trimEnd() else null
    }

    /** Áp dụng theo Cài đặt cho lời vừa tải về nếu là tiếng Trung. */
    fun maybeAnnotate(lyr: Net.Lyr): Net.Lyr {
        if (!Engine.prefs.getBoolean("zh_pinyin", true) || !isChinese(lyr.text)) return lyr
        val t = annotate(lyr.text, true) ?: return lyr
        return lyr.copy(text = t)
    }
}
