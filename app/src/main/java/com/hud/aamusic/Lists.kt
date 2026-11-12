package com.hud.aamusic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Yêu thích, playlist, bài đã ẩn khỏi danh sách và thư mục nhạc. Lưu trong lists.json. */
object Lists {
    var favorites by mutableStateOf<List<String>>(emptyList())
    var playlists by mutableStateOf<Map<String, List<String>>>(emptyMap())
    var hidden by mutableStateOf<List<String>>(emptyList())
    var folders by mutableStateOf<List<String>>(emptyList())

    // Hộp thoại đang mở
    var pickFor by mutableStateOf<Song?>(null)
    var confirmDelete by mutableStateOf<Song?>(null)
    var creatingPlaylist by mutableStateOf(false)

    fun key(s: Song): String = if (s.path.isNotEmpty()) s.path else s.id
    private fun file() = File(Engine.app.filesDir, "lists.json")

    private fun strList(a: JSONArray?): List<String> {
        val r = ArrayList<String>()
        if (a != null) for (i in 0 until a.length()) r += a.optString(i)
        return r
    }

    fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            favorites = strList(o.optJSONArray("fav"))
            hidden = strList(o.optJSONArray("hidden"))
            folders = strList(o.optJSONArray("folders"))
            val pl = LinkedHashMap<String, List<String>>()
            o.optJSONObject("playlists")?.let { p -> for (k in p.keys()) pl[k] = strList(p.optJSONArray(k)) }
            playlists = pl
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("fav", JSONArray(favorites))
        o.put("hidden", JSONArray(hidden))
        o.put("folders", JSONArray(folders))
        val p = JSONObject()
        for ((k, v) in playlists) p.put(k, JSONArray(v))
        o.put("playlists", p)
        try { file().writeText(o.toString()) } catch (e: Exception) { }
    }

    // ---- Yêu thích ----
    fun isFav(s: Song) = key(s) in favorites

    fun toggleFav(s: Song) {
        val k = key(s)
        favorites = if (k in favorites) favorites - k else favorites + k
        save()
        Engine.onExternalChange?.invoke()
    }

    fun favSongs(): List<Song> {
        val m = Engine.pathMap()
        return favorites.mapNotNull { m[it] }.filter { visible(it) }
    }

    // ---- Playlist ----
    fun createPlaylist(name: String, first: Song? = null) {
        val n = name.trim()
        if (n.isEmpty()) return
        val cur = playlists[n] ?: emptyList()
        val next = if (first != null && key(first) !in cur) cur + key(first) else cur
        playlists = playlists + (n to next)
        save()
    }

    fun addTo(name: String, s: Song) {
        val cur = playlists[name] ?: emptyList()
        if (key(s) in cur) return
        playlists = playlists + (name to (cur + key(s)))
        save()
    }

    fun removeFrom(name: String, s: Song) {
        playlists = playlists + (name to ((playlists[name] ?: emptyList()) - key(s)))
        save()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        save()
    }

    fun songsOf(name: String): List<Song> {
        val m = Engine.pathMap()
        return (playlists[name] ?: emptyList()).mapNotNull { m[it] }.filter { visible(it) }
    }

    // ---- Ẩn bài, thư mục ----
    fun hide(s: Song) {
        val k = key(s)
        if (k !in hidden) hidden = hidden + k
        save()
        Engine.refilter()
    }

    fun unhideAll() {
        hidden = emptyList()
        save()
        Engine.refilter()
    }

    fun addFolder(path: String) {
        if (path !in folders) folders = folders + path
        save()
        Engine.refilter()
    }

    fun removeFolder(path: String) {
        folders = folders - path
        save()
        Engine.refilter()
    }

    /** Gỡ bài khỏi mọi danh sách (khi đã xoá file). */
    fun forget(s: Song) {
        val k = key(s)
        favorites = favorites - k
        hidden = hidden - k
        playlists = playlists.mapValues { it.value - k }
        save()
    }

    fun visible(s: Song): Boolean {
        if (key(s) in hidden) return false
        if (folders.isEmpty() || s.path.isEmpty()) return true
        return folders.any { s.path.startsWith(it.trimEnd('/') + "/") }
    }
}
