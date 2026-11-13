package com.hud.aamusic

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import kotlin.concurrent.thread

/** Quét thư viện tìm bài thiếu tag, đề xuất bổ sung từ internet. Kết quả được lưu để lần sau không phải quét lại. */
object LibraryCheck {
    var visible by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var suggesting by mutableStateOf(false)
    var applying by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var message by mutableStateOf("")
    var states by mutableStateOf<List<TagState>>(emptyList())
    val proposals = mutableStateListOf<Proposal>()

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cancel = false

    fun stop() { cancel = true }

    private fun file() = File(Engine.app.filesDir, "scan.json")

    // ---------------- Lưu / nạp kết quả ----------------

    fun load() {
        thread {
            try {
                val f = file()
                if (!f.exists()) return@thread
                val o = JSONObject(f.readText())
                val ss = ArrayList<TagState>()
                val a = o.optJSONArray("s")
                if (a != null) {
                    for (i in 0 until a.length()) {
                        val x = a.getJSONObject(i)
                        ss += TagState(
                            x.optString("i"), x.optString("p"), x.optString("t"), x.optString("a"), x.optString("b"),
                            x.optBoolean("c"), x.optInt("l"), x.optBoolean("k", true), x.optLong("m")
                        )
                    }
                }
                val ps = ArrayList<Proposal>()
                val pa = o.optJSONArray("p")
                if (pa != null) {
                    for (i in 0 until pa.length()) {
                        val x = pa.getJSONObject(i)
                        val lyr = if (x.has("lt")) Net.Lyr(x.getString("lt"), x.optBoolean("ls")) else null
                        ps += Proposal(
                            x.optString("s"), x.optString("lb"),
                            if (x.has("t")) x.getString("t") else null,
                            if (x.has("a")) x.getString("a") else null,
                            if (x.has("b")) x.getString("b") else null,
                            if (x.has("u")) x.getString("u") else null,
                            lyr, x.optString("pa")
                        )
                    }
                }
                main.post {
                    states = ss
                    proposals.clear()
                    proposals.addAll(ps)
                    if (ss.isNotEmpty()) message = "Đã nạp kết quả quét lần trước (${ss.size} file)."
                }
            } catch (e: Exception) {
            }
        }
    }

    fun saveAsync() {
        val st = states.toList()
        val ps = proposals.toList()
        thread {
            try {
                val o = JSONObject()
                val a = JSONArray()
                for (t in st) {
                    a.put(
                        JSONObject().put("i", t.id).put("p", t.path).put("t", t.title).put("a", t.artist)
                            .put("b", t.album).put("c", t.hasCover).put("l", t.lyricKind).put("k", t.ok).put("m", t.mtime)
                    )
                }
                o.put("s", a)
                val pa = JSONArray()
                for (p in ps) {
                    pa.put(
                        JSONObject().put("s", p.songId).put("lb", p.label).putOpt("t", p.title).putOpt("a", p.artist)
                            .putOpt("b", p.album).putOpt("u", p.artUrl).putOpt("lt", p.lyrics?.text)
                            .put("ls", p.lyrics?.synced ?: false).put("pa", p.path)
                    )
                }
                o.put("p", pa)
                file().writeText(o.toString())
            } catch (e: Exception) {
            }
        }
    }

    fun dismiss(p: Proposal) {
        proposals.remove(p)
        saveAsync()
    }

    // ---------------- Quét ----------------

    fun scan() {
        if (scanning || suggesting || applying) return
        val list = Engine.songs.filter { !it.streaming && it.path.isNotEmpty() }
        val prev = states.associateBy { it.path }
        scanning = true
        cancel = false
        progress = 0
        total = list.size
        message = if (list.isEmpty()) "Chưa có bài hát nào (hãy cấp quyền truy cập nhạc)." else ""
        thread {
            val out = ArrayList<TagState>()
            for ((i, s) in list.withIndex()) {
                if (cancel) break
                val m = try { File(s.path).lastModified() } catch (e: Exception) { 0L }
                val old = prev[s.path]
                if (old != null && old.ok && m != 0L && old.mtime == m) {
                    out += old.copy(id = s.id)   // file không đổi: dùng lại kết quả cũ
                } else {
                    val info = Tags.read(s.path)
                    out += if (info == null) {
                        TagState(s.id, s.path, s.title, s.artist, s.album, false, 0, false, m)
                    } else {
                        TagState(s.id, s.path, info.title, info.artist, info.album, info.hasCover, Lrc.kind(info.lyrics), true, m)
                    }
                }
                if (i % 15 == 0) main.post { progress = i + 1 }
            }
            main.post {
                states = out
                progress = out.size
                scanning = false
                val bad = out.count { it.ok && it.missing().isNotEmpty() }
                message = "Đã quét ${out.size} file, $bad file còn thiếu thông tin."
                saveAsync()
            }
        }
    }

    fun suggestAll() {
        if (scanning || suggesting || applying) return
        val targets = states.filter { it.ok && it.missing().isNotEmpty() }
        if (targets.isEmpty()) { message = "Không có bài nào thiếu thông tin."; return }
        suggesting = true
        cancel = false
        progress = 0
        total = targets.size
        proposals.clear()
        thread {
            for ((i, st) in targets.withIndex()) {
                if (cancel) break
                val p = try { suggestOne(st) } catch (e: Throwable) { null }
                main.post {
                    if (p != null) proposals.add(p)
                    progress = i + 1
                }
                Thread.sleep(350)
            }
            main.post {
                suggesting = false
                message = "Đã tạo ${proposals.size} đề xuất. Kiểm tra rồi bấm Áp dụng."
                saveAsync()
            }
        }
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("[^a-z0-9]"), "")

    fun similar(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    /** Đoán (nghệ sĩ, tên bài) từ tên file dạng "Nghệ sĩ - Tên bài.mp3". */
    fun guess(path: String): Pair<String, String> {
        val name = File(path).nameWithoutExtension.replace('_', ' ')
            .replace(Regex("^\\d+[.\\-\\s]+"), "").trim()
        val parts = name.split(" - ", limit = 2)
        return if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair("", name)
    }

    private fun suggestOne(st: TagState): Proposal? {
        val song = Engine.pathMap()[st.path] ?: Engine.songById(st.id) ?: return null
        val (gArtist, gTitle) = guess(st.path)
        val baseTitle = st.title.ifBlank { gTitle }
        val baseArtist = st.artist.ifBlank { gArtist }
        val needMeta = st.title.isBlank() || st.artist.isBlank() || st.album.isBlank() || !st.hasCover

        var hit: Net.Track? = null
        if (needMeta && baseTitle.isNotBlank()) {
            hit = Net.searchTracks("$baseTitle $baseArtist".trim()).firstOrNull { similar(it.title, baseTitle) }
        }
        val newTitle = if (st.title.isBlank()) (hit?.title ?: baseTitle.takeIf { it.isNotBlank() }) else null
        val newArtist = if (st.artist.isBlank()) (hit?.artist ?: baseArtist.takeIf { it.isNotBlank() }) else null
        val newAlbum = if (st.album.isBlank()) hit?.album?.takeIf { it.isNotBlank() } else null
        val art = if (!st.hasCover) hit?.artUrl else null

        var lyr: Net.Lyr? = null
        if (st.lyricKind != 2) {
            val t = newTitle ?: baseTitle
            val a = newArtist ?: baseArtist
            if (t.isNotBlank()) {
                val r = Net.lyrics(t, a, newAlbum ?: st.album, song.durationMs / 1000)
                if (r != null && (r.synced || st.lyricKind == 0)) lyr = r
            }
        }
        if (newTitle == null && newArtist == null && newAlbum == null && art == null && lyr == null) return null
        return Proposal(st.id, newTitle ?: Engine.title(song), newTitle, newArtist, newAlbum, art, lyr, st.path)
    }

    private fun applyOne(p: Proposal): String? {
        val song = Engine.pathMap()[p.path] ?: Engine.songById(p.songId) ?: return "Không tìm thấy bài hát"
        val cover = p.artUrl?.let { Net.bytes(it) }
        val err = Engine.applyTags(song, p.title, p.artist, p.album, p.lyrics?.text, cover)
        if (err == null) {
            main.post {
                proposals.remove(p)
                states = states.map {
                    if (it.path != p.path && it.id != p.songId) it else it.copy(
                        title = p.title ?: it.title,
                        artist = p.artist ?: it.artist,
                        album = p.album ?: it.album,
                        hasCover = it.hasCover || cover != null,
                        lyricKind = p.lyrics?.let { l -> if (l.synced) 2 else 1 } ?: it.lyricKind
                    )
                }
                saveAsync()
            }
        }
        return err
    }

    fun apply(p: Proposal) {
        if (applying) return
        applying = true
        thread {
            val err = applyOne(p)
            main.post {
                applying = false
                message = if (err == null) "Đã ghi tag vào file." else "Không ghi được: $err"
            }
        }
    }

    fun applyAll() {
        if (applying || proposals.isEmpty()) return
        applying = true
        val list = proposals.toList()
        thread {
            var ok = 0
            var lastErr: String? = null
            for (p in list) {
                val err = applyOne(p)
                if (err == null) ok++ else { lastErr = err; if (!Tags.canWrite()) break }
            }
            main.post {
                applying = false
                message = "Đã ghi $ok/${list.size} bài." + (lastErr?.let { " Lỗi: $it" } ?: "")
            }
        }
    }
}
