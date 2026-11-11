package com.hud.aamusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Thanh trượt dọc

private fun posToValue(y: Float, h: Float, pad: Float, min: Float, max: Float): Float {
    val usable = (h - 2f * pad).coerceAtLeast(1f)
    val f = 1f - ((y - pad) / usable).coerceIn(0f, 1f)
    return min + f * (max - min)
}

@Composable
fun VSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(min, max) {
                val pad = 14.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { cb(posToValue(it.y, size.height.toFloat(), pad, min, max)) }
                ) { change, _ ->
                    change.consume()
                    cb(posToValue(change.position.y, size.height.toFloat(), pad, min, max))
                }
            }
            .pointerInput(min, max) {
                val pad = 14.dp.toPx()
                detectTapGestures { cb(posToValue(it.y, size.height.toFloat(), pad, min, max)) }
            }
    ) {
        val w = size.width
        val h = size.height
        val pad = 14.dp.toPx()
        val tw = 6.dp.toPx()
        val cx = w / 2f
        val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val y = pad + (h - 2f * pad) * (1f - frac)
        val zeroFrac = ((0f - min) / (max - min)).coerceIn(0f, 1f)
        val zy = pad + (h - 2f * pad) * (1f - zeroFrac)
        drawRoundRect(Color(0xFF2A303C), Offset(cx - tw / 2f, pad), Size(tw, h - 2f * pad), CornerRadius(tw / 2f, tw / 2f))
        drawRoundRect(AccentA, Offset(cx - tw / 2f, y), Size(tw, h - pad - y), CornerRadius(tw / 2f, tw / 2f))
        drawLine(Color(0x55FFFFFF), Offset(cx - 16.dp.toPx(), zy), Offset(cx + 16.dp.toPx(), zy), 1.dp.toPx())
        drawCircle(Color.White, 12.dp.toPx(), Offset(cx, y))
    }
}

// ------------------------------------------------------------------ Equalizer + Bass

@Composable
fun EqScreen() {
    val info = Engine.eqInfo
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Âm thanh", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = Engine.eqEnabled, onCheckedChange = { Engine.changeEq(it) })
        }
        Section("Equalizer") {
            if (info == null) {
                Text("Hãy phát một bài hát để equalizer sẵn sàng.", color = Dim)
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    info.presets.forEachIndexed { i, n -> Pill(n, false) { Engine.usePreset(i) } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    info.freqsHz.forEachIndexed { b, hz ->
                        val lv = Engine.bandLevels.getOrNull(b) ?: 0
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${lv / 100} dB", fontSize = 12.sp, color = AccentA)
                            VSlider(
                                value = lv.toFloat(), min = info.min.toFloat(), max = info.max.toFloat(),
                                onChange = { Engine.setBand(b, it.toInt()) },
                                modifier = Modifier.height(230.dp).width(52.dp)
                            )
                            Text(
                                if (hz >= 1000) "%.1fk".format(hz / 1000f) else "$hz",
                                fontSize = 12.sp, color = Dim
                            )
                        }
                    }
                }
                SoftBtn("Đặt lại về 0") { info.freqsHz.indices.forEach { Engine.setBand(it, 0) } }
            }
        }
        Section("Bass booster") {
            Text("Mức: ${Engine.bassStrength / 10}%", color = Dim)
            Slider(
                value = Engine.bassStrength.toFloat(), onValueChange = { Engine.setBass(it.toInt()) },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentA, inactiveTrackColor = Card2)
            )
            Text("Một số máy không hỗ trợ bass booster.", color = Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(170.dp))
    }
}

// ------------------------------------------------------------------ Cài đặt

private fun csvCounts(store: PointStore): String {
    val (a, s) = try { store.load() } catch (e: Exception) { Pair(emptyList<Pt>(), emptyList<Pt>()) }
    return "Đang dùng: ${a.size} camera/cảnh báo, ${s.size} biển giới hạn tốc độ"
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
    val act = ctx as? MainActivity
    val prefs = Engine.prefs
    val scope = rememberCoroutineScope()
    val store = remember { PointStore(ctx) }
    var tts by remember { mutableStateOf(prefs.getBoolean("tts", true)) }
    var online by remember { mutableStateOf(prefs.getBoolean("online", true)) }
    var streams by remember { mutableStateOf(prefs.getString("streams", "") ?: "") }
    var urlA by remember { mutableStateOf(DataSync.urlAlerts()) }
    var urlS by remember { mutableStateOf(DataSync.urlSigns()) }
    var csvInfo by remember { mutableStateOf("Đang đọc dữ liệu...") }
    var canWrite by remember { mutableStateOf(Tags.canWrite()) }

    LaunchedEffect(Unit) {
        while (true) {
            canWrite = Tags.canWrite()
            delay(1000)
        }
    }
    LaunchedEffect(Diag.sync) { csvInfo = withContext(Dispatchers.IO) { csvCounts(store) } }

    val pickAlerts = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { importCsv(ctx, store, uri, "alerts") }
            csvInfo = withContext(Dispatchers.IO) { csvCounts(store) }
        }
    }
    val pickSigns = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { importCsv(ctx, store, uri, "signs") }
            csvInfo = withContext(Dispatchers.IO) { csvCounts(store) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Cài đặt", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

        Section("Quyền truy cập") {
            SoftBtn("Cấp quyền vị trí, nhạc, thông báo") { act?.requestPerms() }
            Text(
                if (canWrite) "✓ Đã cho phép ghi tag vào file nhạc" else "✗ Chưa cho phép ghi tag vào file nhạc",
                color = if (canWrite) Color(0xFF4CD964) else AccentB, fontSize = 13.sp
            )
            if (!canWrite) {
                GradBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }
            }
        }

        Section("Cảnh báo giao thông") {
            Text(Diag.service, fontSize = 13.sp, color = Dim)
            Text(Diag.gps, fontSize = 13.sp, color = Dim)
            Text(Diag.osmCam, fontSize = 13.sp, color = Dim)
            Text(Diag.osmLim, fontSize = 13.sp, color = Dim)
            SoftBtn("Kiểm tra kết nối OSM") {
                scope.launch {
                    Diag.test = "Đang kiểm tra..."
                    Diag.test = withContext(Dispatchers.IO) { Overpass.test() }
                }
            }
            if (Diag.test.isNotEmpty()) Text(Diag.test, color = AccentA, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tts, onCheckedChange = { tts = it; prefs.edit().putBoolean("tts", it).apply() })
                Text("Đọc cảnh báo bằng giọng nói (TTS)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = online, onCheckedChange = { online = it; prefs.edit().putBoolean("online", it).apply() })
                Text("Tải thêm dữ liệu OpenStreetMap trực tuyến")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradBtn("Bật dịch vụ") { ctx.startServiceSafe() }
                SoftBtn("Dừng dịch vụ") {
                    Engine.player.stop()
                    ctx.stopService(Intent(ctx, MusicService::class.java))
                }
            }
        }

        Section("Dữ liệu camera / biển báo (offline)") {
            Text(csvInfo, fontSize = 13.sp, color = Dim)
            Text("Nguồn tự động cập nhật (file CSV trên GitHub của bạn):", fontSize = 12.sp, color = Dim)
            OutlinedTextField(urlA, { urlA = it }, label = { Text("URL camera/cảnh báo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(urlS, { urlS = it }, label = { Text("URL biển giới hạn tốc độ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            GradBtn("Cập nhật dữ liệu ngay") {
                prefs.edit().putString("url_alerts", urlA.trim()).putString("url_signs", urlS.trim()).apply()
                DataSync.syncAsync(true)
            }
            Text(Diag.sync, fontSize = 12.sp, color = AccentA)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SoftBtn("Nhập CSV camera") { pickAlerts.launch(arrayOf("*/*")) }
                SoftBtn("Nhập CSV biển") { pickSigns.launch(arrayOf("*/*")) }
                SoftBtn("Xoá CSV đã nhập") {
                    store.clear()
                    scope.launch { csvInfo = withContext(Dispatchers.IO) { csvCounts(store) } }
                }
            }
        }

        Section("Radio online (mỗi dòng: Tên|URL)") {
            OutlinedTextField(value = streams, onValueChange = { streams = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
            SoftBtn("Lưu radio") { prefs.edit().putString("streams", streams).apply() }
        }

        Section("Hiển thị trên Android Auto") {
            Text(
                "Không cần AA XLauncher. Trong Android Auto: Cài đặt > chạm 10 lần vào dòng Phiên bản > " +
                    "menu ⋮ > Cài đặt dành cho nhà phát triển > bật \"Nguồn không xác định\". " +
                    "Khi đó app này và mọi app hỗ trợ Android Auto cài ngoài store đều hiện trên xe.",
                fontSize = 13.sp, color = Dim
            )
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF3A1620))
                .clickable { act?.quitApp() }.padding(16.dp),
            contentAlignment = Alignment.Center
        ) { Text("⏻  Thoát hoàn toàn", color = Color(0xFFFF6B81), fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(170.dp))
    }
}

private fun Context.startServiceSafe() {
    try {
        ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java))
    } catch (e: Exception) {
    }
}

// ------------------------------------------------------------------ Kiểm tra thư viện

@Composable
fun ScanScreen() {
    val act = LocalContext.current as? MainActivity
    val busy = LibraryCheck.scanning || LibraryCheck.suggesting || LibraryCheck.applying
    val states = LibraryCheck.states
    val bad = states.filter { it.ok && it.missing().isNotEmpty() }

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kiểm tra tag", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SoftBtn("✕ Đóng") { LibraryCheck.visible = false }
        }
        Spacer(Modifier.height(8.dp))
        if (!Tags.canWrite()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF3A1620)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Chưa có quyền ghi vào file nhạc. Bạn vẫn quét và xem đề xuất được, nhưng cần cấp quyền để ghi tag.", fontSize = 13.sp)
                GradBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            GradBtn(if (LibraryCheck.scanning) "Đang quét..." else "Quét thư viện") { LibraryCheck.scan() }
            SoftBtn("Tìm đề xuất (internet)") { LibraryCheck.suggestAll() }
            if (busy) SoftBtn("Dừng") { LibraryCheck.stop() }
        }
        if (busy && LibraryCheck.total > 0) {
            val frac = (LibraryCheck.progress.toFloat() / LibraryCheck.total).coerceIn(0f, 1f)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Card2)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(AccentBrush))
            }
            Text("${LibraryCheck.progress}/${LibraryCheck.total}", color = Dim, fontSize = 12.sp)
        }
        if (LibraryCheck.message.isNotEmpty()) {
            Text(LibraryCheck.message, color = AccentA, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (states.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Thiếu tên ${states.count { it.ok && it.title.isBlank() }}", false) {}
                Pill("Thiếu nghệ sĩ ${states.count { it.ok && it.artist.isBlank() }}", false) {}
                Pill("Thiếu album ${states.count { it.ok && it.album.isBlank() }}", false) {}
                Pill("Thiếu bìa ${states.count { it.ok && !it.hasCover }}", false) {}
                Pill("Thiếu lời ${states.count { it.ok && it.lyricKind == 0 }}", false) {}
                Pill("Chưa đồng bộ ${states.count { it.ok && it.lyricKind == 1 }}", false) {}
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (LibraryCheck.proposals.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${LibraryCheck.proposals.size} đề xuất", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        GradBtn("Áp dụng tất cả") { LibraryCheck.applyAll() }
                    }
                }
                items(LibraryCheck.proposals.toList()) { p ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardC).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(p.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.summary(), color = Dim, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradBtn("Áp dụng") { LibraryCheck.apply(p) }
                            SoftBtn("Bỏ qua") { LibraryCheck.proposals.remove(p) }
                        }
                    }
                }
            }
            if (bad.isNotEmpty()) {
                item { Text("Bài còn thiếu thông tin (${bad.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(bad.take(300)) { st ->
                    val song = Engine.songById(st.id)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC).padding(10.dp)) {
                        Text(
                            song?.let { Engine.title(it) } ?: st.title.ifBlank { "(không tên)" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("Thiếu: " + st.missing().joinToString(", "), color = AccentB, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Sửa thông tin (ghi vào tag trong file)

@Composable
fun TagEditor(s: Song) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    fun clean(x: String) = if (x.startsWith("Không rõ")) "" else x
    var title by remember(s.id) { mutableStateOf(Engine.title(s)) }
    var artist by remember(s.id) { mutableStateOf(clean(Engine.artist(s))) }
    var album by remember(s.id) { mutableStateOf(clean(Engine.album(s))) }
    var lyricsText by remember(s.id) { mutableStateOf("") }
    var lyricsDirty by remember(s.id) { mutableStateOf(false) }
    var results by remember(s.id) { mutableStateOf<List<Net.Track>>(emptyList()) }
    var artUrl by remember(s.id) { mutableStateOf<String?>(null) }
    var picked by remember(s.id) { mutableStateOf<ByteArray?>(null) }
    var status by remember(s.id) { mutableStateOf("") }
    var hasCover by remember(s.id) { mutableStateOf(false) }

    LaunchedEffect(s.id) {
        val info = withContext(Dispatchers.IO) { if (s.path.isNotEmpty()) Tags.read(s.path) else null }
        if (info != null) {
            if (info.title.isNotBlank()) title = info.title
            if (info.artist.isNotBlank()) artist = info.artist
            if (info.album.isNotBlank()) album = info.album
            lyricsText = info.lyrics
            hasCover = info.hasCover
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            }
            if (b != null) { picked = b; artUrl = null; status = "Đã chọn ảnh bìa. Bấm Lưu để ghi vào file." }
            else status = "Không đọc được ảnh"
        }
    }

    AlertDialog(
        onDismissRequest = { Engine.editing = null },
        containerColor = CardC,
        title = { Text("Sửa thông tin", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!Tags.canWrite()) {
                    Text("Chưa có quyền ghi vào file: thay đổi chỉ lưu trong app.", color = AccentB, fontSize = 12.sp)
                    SoftBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Tên bài") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Nghệ sĩ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                val lk = Lrc.kind(lyricsText)
                Text(
                    "Lời bài hát: " + when (lk) { 2 -> "có, đồng bộ theo thời gian"; 1 -> "có (không đồng bộ)"; else -> "chưa có" } +
                        " • Bìa: " + if (hasCover || picked != null || artUrl != null) "có" else "chưa có",
                    fontSize = 12.sp, color = Dim
                )

                SoftBtn("🔎 Tìm tên, album, bìa trên internet") {
                    scope.launch {
                        status = "Đang tìm..."
                        val r = withContext(Dispatchers.IO) { Net.searchTracks("$title $artist".trim()) }
                        results = r
                        status = if (r.isEmpty()) "Không tìm thấy (hoặc không có mạng)" else "Chọn một kết quả:"
                    }
                }
                results.take(8).forEach { t ->
                    Text(
                        "${t.title} — ${t.artist}\n${t.album}", fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card2)
                            .clickable {
                                title = t.title; artist = t.artist; album = t.album; artUrl = t.artUrl; picked = null
                                status = "Đã chọn. Bấm Lưu để ghi vào file (kèm bìa)."
                            }.padding(10.dp)
                    )
                }

                SoftBtn("🎤 Tải lời bài hát (LRCLIB)") {
                    scope.launch {
                        status = "Đang tải lời..."
                        val r = withContext(Dispatchers.IO) { Net.lyrics(title, artist, album, s.durationMs / 1000) }
                        if (r == null) {
                            status = "Không tìm thấy lời bài hát này"
                        } else {
                            lyricsText = r.text
                            lyricsDirty = true
                            status = if (r.synced) "Đã tải lời đồng bộ. Bấm Lưu để ghi vào file." else "Chỉ có lời thường (không chạy theo nhạc). Bấm Lưu để ghi."
                        }
                    }
                }
                SoftBtn("🖼 Chọn ảnh bìa từ máy") { pick.launch("image/*") }
                if (status.isNotEmpty()) Text(status, color = AccentA, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    status = "Đang lưu..."
                    val u = artUrl
                    val cover: ByteArray? = picked ?: if (u != null) withContext(Dispatchers.IO) { Net.bytes(u) } else null
                    val err = withContext(Dispatchers.IO) {
                        Engine.applyTags(s, title, artist, album, if (lyricsDirty) lyricsText else null, cover)
                    }
                    if (err == null) {
                        Engine.editing = null
                    } else {
                        status = "Chưa ghi được vào file: $err. Đã lưu trong app."
                    }
                }
            }) { Text("Lưu vào file", color = AccentA) }
        },
        dismissButton = { TextButton(onClick = { Engine.editing = null }) { Text("Đóng") } }
    )
}
