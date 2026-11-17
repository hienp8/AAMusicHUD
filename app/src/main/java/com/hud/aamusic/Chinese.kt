package com.hud.aamusic

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.util.concurrent.TimeUnit

/** Nhận biết lời tiếng Trung, thêm phiên âm (pinyin có dấu) và bản dịch tiếng Việt (dịch trên máy). */
object Chinese {
    private var translator: Translator? = null

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

    /** Dịch Trung -> Việt bằng ML Kit trên máy. Trả về null nếu lỗi (chưa tải được mô hình...). Gọi ngoài luồng chính. */
    fun translate(lines: List<String>): List<String>? {
        return try {
            val tr = translator ?: Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.CHINESE)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build()
            ).also { translator = it }
            Tasks.await(tr.downloadModelIfNeeded(DownloadConditions.Builder().build()), 240, TimeUnit.SECONDS)
            lines.map { if (it.isBlank()) "" else Tasks.await(tr.translate(it), 30, TimeUnit.SECONDS) }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Thêm phiên âm và/hoặc dịch Việt: mỗi dòng gốc kèm các dòng phụ cùng mốc thời gian (lời đồng bộ)
     * hoặc ngay bên dưới (lời thường). Trả về null nếu không có gì để thêm hoặc đã được thêm từ trước.
     */
    fun annotate(text: String, withPinyin: Boolean, withVi: Boolean): String? {
        val parsed = Lrc.parse(text)
        val synced = parsed.isNotEmpty()
        if (synced && parsed.any { it.extras.isNotEmpty() }) return null
        val src: List<Pair<Long?, String>> =
            if (synced) parsed.map { it.timeMs to it.text } else text.lines().map { null to it.trim() }
        val hanIdx = src.indices.filter { i -> src[i].second.any { isHan(it) } }
        if (hanIdx.isEmpty()) return null

        var vi: Map<Int, String> = emptyMap()
        if (withVi) {
            val tr = translate(hanIdx.map { src[it].second })
            if (tr != null) vi = hanIdx.zip(tr).toMap()
        }
        if (!withPinyin && vi.isEmpty()) return null

        val out = StringBuilder()
        var added = false
        for ((i, p) in src.withIndex()) {
            val t = p.first
            val line = p.second
            fun emit(s: String) {
                if (t != null) out.append(fmtTag(t))
                out.append(s).append('\n')
            }
            emit(line)
            if (line.any { isHan(it) }) {
                if (withPinyin) {
                    val py = pinyin(line)
                    if (py.isNotBlank()) { emit(py); added = true }
                }
                val v = vi[i]
                if (!v.isNullOrBlank()) { emit(v); added = true }
            }
        }
        return if (added) out.toString().trimEnd() else null
    }

    /** Áp dụng theo Cài đặt (pinyin / dịch Việt) cho lời vừa tải về nếu là tiếng Trung. */
    fun maybeAnnotate(lyr: Net.Lyr): Net.Lyr {
        val p = Engine.prefs
        val wp = p.getBoolean("zh_pinyin", true)
        val wv = p.getBoolean("zh_vi", true)
        if (!(wp || wv) || !isChinese(lyr.text)) return lyr
        val t = annotate(lyr.text, wp, wv) ?: return lyr
        return lyr.copy(text = t)
    }
}
