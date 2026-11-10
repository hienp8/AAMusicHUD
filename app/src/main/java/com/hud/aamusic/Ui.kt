package com.hud.aamusic

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val Accent = Color(0xFFFF6D00)
private val Dim = Color(0xFF9AA0A6)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Color(0xFF0F1115),
            surface = Color(0xFF171A20),
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF242932),
            onSurfaceVariant = Color(0xFFCFD3D8)
        ),
        content = content
    )
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (tab != 1) MiniPlayer { tab = 1 }
                    NavigationBar {
                        val items = listOf("🎵" to "Thư viện", "▶" to "Đang phát", "🎚" to "EQ", "⚙" to "Cài đặt")
                        items.forEachIndexed { i, it ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(it.first) },
                                label = { Text(it.second, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> LibraryScreen()
                    1 -> NowPlayingScreen()
                    2 -> EqScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
    Engine.editing?.let { TagEditor(it) }
}

// ------------------------------------------------------------------ Ảnh bìa

@Composable
fun Art(song: Song?, modifier: Modifier = Modifier, px: Int = 256) {
    val ver = Engine.artVersion
    val bmp by produceState<Bitmap?>(null, song?.id, ver) {
        value = withContext(Dispatchers.IO) { Engine.loadArt(song, px) }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF242932)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", fontSize = 28.sp, color = Dim)
        }
    }
}

// ------------------------------------------------------------------ Mini player

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val s = Engine.current ?: return
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s, Modifier.size(44.dp), 128)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(Engine.artist(s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
        }
        IconButton(onClick = { Engine.togglePlay() }) {
            Text(if (Engine.isPlaying) "⏸" else "▶", fontSize = 20.sp)
        }
        IconButton(onClick = { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }) {
            Text("⏭", fontSize = 20.sp)
        }
    }
}

// ------------------------------------------------------------------ Thư viện

@Composable
fun LibraryScreen() {
    var sub by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Pair<String, String>?>(null) }
    val all = Engine.songs
    val q = query.trim().lowercase()
    fun match(s: Song) = q.isEmpty() || Engine.title(s).lowercase().contains(q) ||
        Engine.artist(s).lowercase().contains(q) || Engine.album(s).lowercase().contains(q)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Tìm trong thư viện") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        )
        TabRow(selectedTabIndex = sub) {
            listOf("Bài hát", "Album", "Nghệ sĩ", "Radio").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i; filter = null }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        val f = filter
        when {
            f != null -> {
                TextButton(onClick = { filter = null }) { Text("← ${f.second}") }
                SongList(all.filter { if (f.first == "album") Engine.album(it) == f.second else Engine.artist(it) == f.second })
            }
            sub == 0 -> SongList(all.filter { match(it) })
            sub == 1 -> GroupList(all.filter { match(it) }.groupBy { Engine.album(it) }) { filter = "album" to it }
            sub == 2 -> GroupList(all.filter { match(it) }.groupBy { Engine.artist(it) }) { filter = "artist" to it }
            else -> {
                val radio = Engine.streams()
                if (radio.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Chưa có radio. Thêm ở Cài đặt (mỗi dòng: Tên|URL).", color = Dim,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    }
                } else SongList(radio)
            }
        }
    }
}

@Composable
fun GroupList(groups: Map<String, List<Song>>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups.keys.sorted()) { k ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(k) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(groups[k]?.firstOrNull(), Modifier.size(52.dp), 128)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${groups[k]?.size ?: 0} bài", color = Dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SongList(list: List<Song>) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Không có bài hát. Hãy cấp quyền truy cập nhạc ở Cài đặt.", color = Dim,
                textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { _, s -> s.id }) { i, s -> SongRow(s) { Engine.play(list, i) } }
    }
}

@Composable
fun SongRow(s: Song, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val playing = Engine.current?.id == s.id
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s, Modifier.size(50.dp), 128)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) Accent else Color.White, fontWeight = FontWeight.Medium
            )
            Text(
                "${Engine.artist(s)} • ${Engine.album(s)}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Sửa thông tin / ảnh / lời") },
                    onClick = { menu = false; if (!s.streaming) Engine.editing = s }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Đang phát

@Composable
fun NowPlayingScreen() {
    val s = Engine.current
    if (s == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Chưa phát bài nào", color = Dim) }
        return
    }
    var showLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            Engine.tick()
            delay(250)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (showLyrics) LyricsView() else Art(s, Modifier.fillMaxWidth().aspectRatio(1f), 720)
        }
        Spacer(Modifier.height(12.dp))
        Text(Engine.title(s), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Engine.artist(s)} • ${Engine.album(s)}", color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val dur = max(Engine.durationMs, 1L)
        Slider(
            value = Engine.positionMs.toFloat().coerceIn(0f, dur.toFloat()),
            onValueChange = { Engine.seeking = true; Engine.positionMs = it.toLong() },
            onValueChangeFinished = { Engine.player.seekTo(Engine.positionMs); Engine.seeking = false },
            valueRange = 0f..dur.toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(Engine.positionMs), color = Dim, fontSize = 12.sp)
            Text(fmt(Engine.durationMs), color = Dim, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Engine.toggleShuffle() }) {
                Text("🔀", fontSize = 22.sp, color = if (Engine.shuffle) Accent else Dim)
            }
            IconButton(onClick = { Engine.player.seekToPreviousMediaItem() }, modifier = Modifier.size(56.dp)) {
                Text("⏮", fontSize = 28.sp)
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Accent).clickable { Engine.togglePlay() },
                Alignment.Center
            ) {
                Text(if (Engine.isPlaying) "⏸" else "▶", fontSize = 32.sp, color = Color.Black)
            }
            IconButton(onClick = { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }, modifier = Modifier.size(56.dp)) {
                Text("⏭", fontSize = 28.sp)
            }
            TextButton(onClick = { Engine.cycleRepeat() }) {
                Text(
                    if (Engine.repeatMode == Player.REPEAT_MODE_ONE) "🔂" else "🔁", fontSize = 22.sp,
                    color = if (Engine.repeatMode != Player.REPEAT_MODE_OFF) Accent else Dim
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { showLyrics = !showLyrics }) {
                Text(if (showLyrics) "🖼 Ảnh bìa" else "🎤 Lời bài hát")
            }
            OutlinedButton(onClick = { if (!s.streaming) Engine.editing = s }) { Text("✏ Sửa thông tin") }
        }
    }
}

@Composable
fun LyricsView() {
    val lines = Engine.lyricLines
    if (lines.isEmpty()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (Engine.plainLyrics.isNotEmpty()) Engine.plainLyrics
                else "Chưa có lời.\nBấm ✏ Sửa thông tin, rồi chọn Tải lời bài hát.",
                color = if (Engine.plainLyrics.isNotEmpty()) Color.White else Dim,
                textAlign = TextAlign.Center, lineHeight = 26.sp
            )
        }
        return
    }
    val pos = Engine.positionMs
    val idx = lines.indexOfLast { it.timeMs <= pos + 250 }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(idx) { state.animateScrollToItem((idx - 3).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = state, horizontalAlignment = Alignment.CenterHorizontally) {
        itemsIndexed(lines) { i, l ->
            val cur = i == idx
            Text(
                l.text.ifEmpty { "♪" }, textAlign = TextAlign.Center,
                fontSize = if (cur) 24.sp else 18.sp,
                fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                color = if (cur) Accent else Dim,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { Engine.player.seekTo(l.timeMs) }
            )
        }
    }
}

// ------------------------------------------------------------------ Equalizer

@Composable
fun EqScreen() {
    val info = Engine.eqInfo
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Equalizer", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = Engine.eqEnabled, onCheckedChange = { Engine.changeEq(it) })
        }
        if (info == null) {
            Text("Hãy phát một bài hát để equalizer sẵn sàng.", color = Dim, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                info.presets.forEachIndexed { i, n ->
                    OutlinedButton(onClick = { Engine.usePreset(i) }, modifier = Modifier.padding(end = 6.dp)) { Text(n) }
                }
            }
            info.freqsHz.forEachIndexed { b, hz ->
                val lv = Engine.bandLevels.getOrNull(b) ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz",
                        modifier = Modifier.width(70.dp), color = Dim, fontSize = 13.sp
                    )
                    Slider(
                        value = lv.toFloat(), onValueChange = { Engine.setBand(b, it.toInt()) },
                        valueRange = info.min.toFloat()..info.max.toFloat(), modifier = Modifier.weight(1f)
                    )
                    Text("${lv / 100} dB", modifier = Modifier.width(52.dp), color = Dim, fontSize = 13.sp, textAlign = TextAlign.End)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Bass booster", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = Engine.bassStrength.toFloat(), onValueChange = { Engine.setBass(it.toInt()) },
            valueRange = 0f..1000f
        )
        Text("Mức: ${Engine.bassStrength / 10}%", color = Dim)
        Spacer(Modifier.height(12.dp))
        Text(
            "Equalizer và bass booster áp dụng lên âm thanh phát bởi app này, kể cả khi nghe qua Android Auto. " +
                "Một số máy không hỗ trợ bass booster.",
            color = Dim, fontSize = 12.sp
        )
    }
}

// ------------------------------------------------------------------ Cài đặt

private fun csvCounts(store: PointStore): String {
    val (a, s) = try { store.load() } catch (e: Exception) { Pair(emptyList<Pt>(), emptyList<Pt>()) }
    return "Đã nhập: ${a.size} camera/cảnh báo, ${s.size} biển giới hạn tốc độ"
}

private fun importCsv(ctx: Context, store: PointStore, uri: Uri, kind: String) {
    try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            store.fileFor(kind).outputStream().use { out -> input.copyTo(out) }
        }
    } catch (e: Exception) {
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = Engine.prefs
    val scope = rememberCoroutineScope()
    val store = remember { PointStore(ctx) }
    var tts by remember { mutableStateOf(prefs.getBoolean("tts", true)) }
    var online by remember { mutableStateOf(prefs.getBoolean("online", true)) }
    var streams by remember { mutableStateOf(prefs.getString("streams", "") ?: "") }
    var csvInfo by remember { mutableStateOf(csvCounts(store)) }
    val pickAlerts = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importCsv(ctx, store, uri, "alerts"); csvInfo = csvCounts(store) }
    }
    val pickSigns = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importCsv(ctx, store, uri, "signs"); csvInfo = csvCounts(store) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Cài đặt", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Text("Trạng thái cảnh báo giao thông", fontWeight = FontWeight.SemiBold)
        Text(Diag.service, fontSize = 13.sp, color = Dim)
        Text(Diag.gps, fontSize = 13.sp, color = Dim)
        Text(Diag.osmCam, fontSize = 13.sp, color = Dim)
        Text(Diag.osmLim, fontSize = 13.sp, color = Dim)
        OutlinedButton(onClick = {
            scope.launch {
                Diag.test = "Đang kiểm tra..."
                Diag.test = withContext(Dispatchers.IO) { Overpass.test() }
            }
        }) { Text("Kiểm tra kết nối OSM") }
        if (Diag.test.isNotEmpty()) Text(Diag.test, color = Accent, fontSize = 13.sp)
        Divider()

        Button(onClick = { (ctx as? MainActivity)?.requestPerms() }) { Text("Cấp quyền (vị trí, nhạc, thông báo)") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = tts, onCheckedChange = { tts = it; prefs.edit().putBoolean("tts", it).apply() })
            Text("Đọc cảnh báo bằng giọng nói (TTS)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = online, onCheckedChange = { online = it; prefs.edit().putBoolean("online", it).apply() })
            Text("Tải thêm dữ liệu OpenStreetMap trực tuyến")
        }
        Row {
            Button(onClick = { ctx.startForegroundServiceSafe() }) { Text("Bật dịch vụ") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                Engine.player.stop()
                ctx.stopService(Intent(ctx, MusicService::class.java))
            }) { Text("Dừng dịch vụ") }
        }
        Divider()

        Text("Dữ liệu CSV (offline)", fontWeight = FontWeight.SemiBold)
        Text(csvInfo, fontSize = 13.sp, color = Dim)
        Row {
            OutlinedButton(onClick = { pickAlerts.launch(arrayOf("*/*")) }) { Text("Nhập camera/cảnh báo") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { pickSigns.launch(arrayOf("*/*")) }) { Text("Nhập biển giới hạn") }
        }
        TextButton(onClick = { store.clear(); csvInfo = csvCounts(store) }) { Text("Xoá dữ liệu CSV đã nhập") }
        Divider()

        Text("Radio online (mỗi dòng: Tên|URL)", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = streams, onValueChange = { streams = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(onClick = { prefs.edit().putString("streams", streams).apply() }) { Text("Lưu radio") }
        Divider()

        Text("Hiển thị trên Android Auto", fontWeight = FontWeight.SemiBold)
        Text(
            "Không cần AA XLauncher. Trong Android Auto: Cài đặt > chạm 10 lần vào dòng Phiên bản > " +
                "menu ⋮ > Cài đặt dành cho nhà phát triển > bật \"Nguồn không xác định\". " +
                "Khi đó app này và mọi app hỗ trợ Android Auto cài ngoài store đều hiện trên xe.",
            fontSize = 13.sp, color = Dim
        )
    }
}

private fun Context.startForegroundServiceSafe() {
    try {
        ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
    } catch (e: Exception) {
    }
}

// ------------------------------------------------------------------ Sửa thông tin

@Composable
fun TagEditor(s: Song) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun clean(x: String) = if (x.startsWith("Không rõ")) "" else x
    var title by remember(s.id) { mutableStateOf(Engine.title(s)) }
    var artist by remember(s.id) { mutableStateOf(clean(Engine.artist(s))) }
    var album by remember(s.id) { mutableStateOf(clean(Engine.album(s))) }
    var results by remember(s.id) { mutableStateOf<List<Net.Track>>(emptyList()) }
    var artUrl by remember(s.id) { mutableStateOf<String?>(null) }
    var status by remember(s.id) { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { Engine.saveArt(s, it.readBytes()) } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            status = if (ok) "Đã đặt ảnh bìa từ máy" else "Không đọc được ảnh"
        }
    }

    AlertDialog(
        onDismissRequest = { Engine.editing = null },
        title = { Text("Sửa thông tin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Tên bài") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Nghệ sĩ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                OutlinedButton(onClick = {
                    scope.launch {
                        status = "Đang tìm..."
                        val r = withContext(Dispatchers.IO) { Net.searchTracks("$title $artist".trim()) }
                        results = r
                        status = if (r.isEmpty()) "Không tìm thấy (hoặc không có mạng)" else "Chọn một kết quả:"
                    }
                }) { Text("🔎 Tìm tên, album, ảnh trên internet") }

                results.take(8).forEach { t ->
                    Text(
                        "${t.title} — ${t.artist}\n${t.album}", fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            title = t.title; artist = t.artist; album = t.album; artUrl = t.artUrl
                            status = "Đã chọn. Bấm Lưu để áp dụng (kèm ảnh bìa)."
                        }.padding(vertical = 6.dp)
                    )
                    Divider()
                }

                OutlinedButton(onClick = {
                    scope.launch {
                        status = "Đang tải lời..."
                        val r = withContext(Dispatchers.IO) { Net.lyrics(title, artist, album, s.durationMs / 1000) }
                        if (r == null) {
                            status = "Không tìm thấy lời bài hát này"
                        } else {
                            withContext(Dispatchers.IO) { Engine.saveLyrics(s, r.text) }
                            status = if (r.synced) "Đã tải lời đồng bộ ✓" else "Chỉ có lời thường (không chạy theo nhạc)"
                        }
                    }
                }) { Text("🎤 Tải lời bài hát (LRCLIB)") }

                OutlinedButton(onClick = { pick.launch("image/*") }) { Text("🖼 Chọn ảnh bìa từ máy") }
                if (status.isNotEmpty()) Text(status, color = Accent, fontSize = 13.sp)
                Text(
                    "Thay đổi được lưu trong app, không ghi vào file nhạc gốc.",
                    color = Dim, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    status = "Đang lưu..."
                    val u = artUrl
                    if (u != null) {
                        val b = withContext(Dispatchers.IO) { Net.bytes(u) }
                        if (b != null) withContext(Dispatchers.IO) { Engine.saveArt(s, b) }
                    }
                    Engine.saveEdit(s, title, artist, album)
                    Engine.editing = null
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = { Engine.editing = null }) { Text("Đóng") } }
    )
}
