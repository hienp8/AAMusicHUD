package com.hud.aamusic

/** Nhận biết lời tiếng Trung. */
object Chinese {
    fun isHan(ch: Char): Boolean = ch in '\u4e00'..'\u9fff' || ch in '\u3400'..'\u4dbf'

    fun isChinese(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val han = text.count { isHan(it) }
        val kana = text.count { it in '\u3040'..'\u30ff' }
        return han >= 6 && han * 100 / letters >= 40 && kana * 10 < han
    }

    fun fmtTag(ms: Long): String {
        val m = ms / 60000
        val s = (ms / 1000) % 60
        val cs = (ms % 1000) / 10
        return "[%02d:%02d.%02d]".format(m, s, cs)
    }
}
