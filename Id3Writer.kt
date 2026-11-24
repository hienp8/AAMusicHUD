package com.hud.aamusic

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Ghi tag ID3v2 cho file MP3: chỉ thay các frame TIT2, TPE1, TALB, USLT (và APIC nếu có ảnh mới),
 * giữ nguyên byte của mọi frame khác (ảnh bìa, năm, thể loại...). Dữ liệu âm thanh được sao chép nguyên vẹn.
 * Ghi ra file tạm, kiểm tra kích thước rồi mới thay file gốc (có bản dự phòng) nên không làm hỏng file.
 */
object Id3Writer {
    class Unsupported(msg: String) : Exception(msg)

    private class Frame(val id: String, val flags: ByteArray, val data: ByteArray)

    private fun syncsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    private fun toSyncsafe(v: Int): ByteArray = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte()
    )

    private fun beInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun toBe(v: Int): ByteArray =
        byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun unsync(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            val x = b[i]
            o.write(x.toInt() and 0xFF)
            if (x == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return o.toByteArray()
    }

    private val BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    private fun textFrame(id: String, s: String) =
        Frame(id, byteArrayOf(0, 0), byteArrayOf(1) + BOM + s.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0))

    private fun lyricsFrame(s: String) = Frame(
        "USLT", byteArrayOf(0, 0),
        byteArrayOf(1) + "eng".toByteArray(Charsets.ISO_8859_1) + BOM + byteArrayOf(0, 0) + BOM + s.toByteArray(Charsets.UTF_16LE)
    )

    private fun pictureFrame(img: ByteArray): Frame {
        val png = img.size > 4 && img[0] == 0x89.toByte() && img[1] == 'P'.code.toByte()
        val mime = if (png) "image/png" else "image/jpeg"
        return Frame(
            "APIC", byteArrayOf(0, 0),
            byteArrayOf(0) + mime.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + img
        )
    }

    /** @return null nếu thành công. Ném [Unsupported] nếu kiểu tag không xử lý được (để dùng cách khác). */
    fun write(path: String, title: String?, artist: String?, album: String?, lyrics: String?, cover: ByteArray?): String? {
        val orig = File(path)
        val len = orig.length()
        var major = 3
        val frames = ArrayList<Frame>()
        var audioStart = 0L
        RandomAccessFile(orig, "r").use { raf ->
            val head = ByteArray(10)
            raf.readFully(head)
            if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                major = head[3].toInt() and 0xFF
                if (major != 3 && major != 4) throw Unsupported("ID3v2.$major")
                val flags = head[5].toInt() and 0xFF
                val size = syncsafe(head, 6)
                if (size < 0 || size.toLong() + 10 > len) throw Unsupported("kích thước tag không hợp lệ")
                var tag = ByteArray(size)
                raf.readFully(tag)
                audioStart = 10L + size + (if (major == 4 && (flags and 0x10) != 0) 10 else 0)
                if ((flags and 0x80) != 0) tag = unsync(tag)
                var pos = 0
                if ((flags and 0x40) != 0) {
                    pos = if (major == 4) syncsafe(tag, 0) else beInt(tag, 0) + 4
                    if (pos < 0 || pos > tag.size) throw Unsupported("extended header lỗi")
                }
                while (pos + 10 <= tag.size) {
                    if (tag[pos].toInt() == 0) break
                    val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                    val fsz = if (major == 4) syncsafe(tag, pos + 4) else beInt(tag, pos + 4)
                    if (fsz < 0 || pos + 10 + fsz > tag.size) throw Unsupported("frame $id lỗi")
                    frames += Frame(id, tag.copyOfRange(pos + 8, pos + 10), tag.copyOfRange(pos + 10, pos + 10 + fsz))
                    pos += 10 + fsz
                }
            }
        }

        fun has(x: String?) = !x.isNullOrBlank()
        val out = ArrayList<Frame>()
        for (f in frames) {
            if (has(title) && f.id == "TIT2") continue
            if (has(artist) && f.id == "TPE1") continue
            if (has(album) && f.id == "TALB") continue
            if (has(lyrics) && f.id == "USLT") continue
            if (cover != null && f.id == "APIC") continue
            out += f
        }
        if (has(title)) out += textFrame("TIT2", title?.trim().orEmpty())
        if (has(artist)) out += textFrame("TPE1", artist?.trim().orEmpty())
        if (has(album)) out += textFrame("TALB", album?.trim().orEmpty())
        if (has(lyrics)) out += lyricsFrame(lyrics.orEmpty())
        if (cover != null) out += pictureFrame(cover)

        var framesLen = 0
        for (f in out) framesLen += 10 + f.data.size
        val padding = 2048
        val tagSize = framesLen + padding

        val tmp = File(orig.parentFile, "." + orig.name + ".aatmp")
        try {
            tmp.outputStream().buffered().use { o ->
                o.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), major.toByte(), 0, 0))
                o.write(toSyncsafe(tagSize))
                for (f in out) {
                    o.write(f.id.toByteArray(Charsets.ISO_8859_1))
                    o.write(if (major == 4) toSyncsafe(f.data.size) else toBe(f.data.size))
                    o.write(f.flags)
                    o.write(f.data)
                }
                o.write(ByteArray(padding))
                RandomAccessFile(orig, "r").use { raf ->
                    raf.seek(audioStart)
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = raf.read(buf)
                        if (n <= 0) break
                        o.write(buf, 0, n)
                    }
                }
            }
            val expected = 10L + tagSize + (len - audioStart)
            if (tmp.length() != expected) return "Kích thước file sau khi ghi không khớp, đã huỷ để bảo vệ file"
            return Tags.replaceFile(orig, tmp)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
