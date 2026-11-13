package com.hud.aamusic

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URLEncoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun LabeledSlider(label: String, value: String, v: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = AccentA, fontSize = 13.sp)
        }
        Slider(
            value = v.coerceIn(min, max), onValueChange = onChange, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentA, inactiveTrackColor = Card2)
        )
    }
}

@Composable
fun EqScreen(onBack: () -> Unit) {
    val info = Engine.eqInfo
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Âm thanh", onBack) {
            Switch(checked = Engine.eqEnabled, onCheckedChange = { Engine.changeEq(it) })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Section("Equalizer 10 dải") {
                if (info == null) {
                    Text("Equalizer chưa sẵn sàng.", color = Dim)
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
                                Text("${lv / 100}", fontSize = 10.sp, color = AccentA)
                                VSlider(
                                    value = lv.toFloat(), min = info.min.toFloat(), max = info.max.toFloat(),
                                    onChange = { Engine.setBand(b, it.toInt()) },
                                    modifier = Modifier.height(210.dp).width(30.dp)
                                )
                                Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", fontSize = 10.sp, color = Dim)
                            }
                        }
                    }
                    SoftBtn("Đặt lại về 0") { info.freqsHz.indices.forEach { Engine.setBand(it, 0) } }
                }
            }
            Section("Bass booster") {
                LabeledSlider("Mức bass", "${Engine.bassStrength / 10}%", Engine.bassStrength.toFloat(), 0f, 1000f) { Engine.setBass(it.toInt()) }
                Text("Một số máy không hỗ trợ bass booster.", color = Dim, fontSize = 12.sp)
            }
            Section("Nâng cao") {
                LabeledSlider("Tiền khuếch đại (preamp)", "%+.1f dB".format(Engine.preamp / 100f), Engine.preamp.toFloat(), -1200f, 1200f) {
                    Engine.changePreamp(it.toInt())
                }
                CheckRow("Chống vỡ tiếng (limiter)", Engine.limiterOn) { Engine.setLimiter(it) }
                LabeledSlider("Âm trường (mở rộng stereo)", "${Engine.virtStrength / 10}%", Engine.virtStrength.toFloat(), 0f, 1000f) {
                    Engine.setVirtualizer(it.toInt())
                }
                LabeledSlider("Tăng âm lượng", "+%.1f dB".format(Engine.loudGain / 100f), Engine.loudGain.toFloat(), 0f, 1200f) {
                    Engine.setLoudness(it.toInt())
                }
                Text(
                    "Tiền khuếch đại và limiter dùng bộ xử lý âm thanh của Android 9 trở lên; tăng âm lượng có nén nhẹ để không vỡ tiếng. " +
                        "Máy không hỗ trợ thì mục tương ứng không có tác dụng.",
                    color = Dim, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ Cài đặt

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    val prefs = Engine.prefs
    val scope = rememberCoroutineScope()
    var lockLyrics by remember { mutableStateOf(prefs.getBoolean("lock_lyrics", true)) }
    var notifExtra by remember { mutableStateOf(prefs.getString("notif_extra", "shuffle") ?: "shuffle") }
    var streams by remember { mutableStateOf(prefs.getString("streams", "") ?: "") }
    var canWrite by remember { mutableStateOf(Tags.canWrite()) }
    var capEnabled by remember { mutableStateOf(prefs.getBoolean("cap_enabled", false)) }
    var capPkg by remember { mutableStateOf(prefs.getString("cap_pkg", "") ?: "") }
    var capLabel by remember { mutableStateOf(prefs.getString("cap_label", "") ?: "") }
    var capX by remember { mutableStateOf(prefs.getInt("cap_x", 4)) }
    var capY by remember { mutableStateOf(prefs.getInt("cap_y", 8)) }
    var capS by remember { mutableStateOf(prefs.getInt("cap_size", 92)) }
    var capBand by remember { mutableStateOf(prefs.getBoolean("cap_band", true)) }
    var capRx by remember { mutableStateOf(prefs.getInt("cap_rx", 4)) }
    var capRy by remember { mutableStateOf(prefs.getInt("cap_ry", 14)) }
    var capRw by remember { mutableStateOf(prefs.getInt("cap_rw", 92)) }
    var pickingApp by remember { mutableStateOf(false) }
    var rootInfo by remember { mutableStateOf("") }

    fun putInt(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    fun setDioto() {
        capX = 4; capY = 8; capS = 92; capRx = 4; capRy = 14; capRw = 92
        prefs.edit().putInt("cap_x", 4).putInt("cap_y", 8).putInt("cap_size", 92)
            .putInt("cap_rx", 4).putInt("cap_ry", 14).putInt("cap_rw", 92).apply()
    }

    LaunchedEffect(Unit) {
        while (true) {
            canWrite = Tags.canWrite()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cài đặt", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CSection("Quyền truy cập", true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    GradBtn("Cấp quyền nhạc, thông báo") { act?.requestPerms() }
                    SoftBtn("Bật dịch vụ") { ctx.startServiceSafe() }
                }
                Text(
                    if (canWrite) "✓ Đã cho phép ghi tag vào file nhạc" else "✗ Chưa cho phép ghi tag vào file nhạc",
                    color = if (canWrite) Color(0xFF4CD964) else AccentB, fontSize = 13.sp
                )
                if (!canWrite) GradBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }
            }

            CSection("Android Auto, thông báo, màn hình khoá") {
                CheckRow("Hiện lời đồng bộ thay tên nghệ sĩ (Android Auto, thông báo, màn hình khoá)", lockLyrics) {
                    lockLyrics = it; prefs.edit().putBoolean("lock_lyrics", it).apply()
                }
                Text("Nút phụ trên thông báo (ngoài ❤ ⏮ ⏯ ⏭, Android chỉ cho tối đa 5 nút):", fontSize = 12.sp, color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("🔀 Ngẫu nhiên", notifExtra == "shuffle") { notifExtra = "shuffle"; prefs.edit().putString("notif_extra", "shuffle").apply() }
                    Pill("🔁 Lặp lại", notifExtra == "repeat") { notifExtra = "repeat"; prefs.edit().putString("notif_extra", "repeat").apply() }
                }
                Text(
                    "Hiện app trên xe: Android Auto > chạm 10 lần vào Phiên bản > menu ⋮ > Cài đặt dành cho nhà phát triển > " +
                        "bật \"Nguồn không xác định\".",
                    fontSize = 12.sp, color = Dim
                )
            }

            CSection("Hình DiOTO trên Android Auto (cần root)") {
                Text(
                    "DiOTO được mở trên màn hình ảo, AA Music chụp lại giao diện rồi hiện thay ảnh bìa trên xe. " +
                        "Khi ngắt kết nối, app đó sẽ bị đóng.",
                    fontSize = 12.sp, color = Dim
                )
                CheckRow("Bật khi kết nối Android Auto", capEnabled) { capEnabled = it; prefs.edit().putBoolean("cap_enabled", it).apply() }
                Text("App: " + (if (capPkg.isEmpty()) "(chưa chọn)" else "$capLabel ($capPkg)"), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    GradBtn("Chọn app") { pickingApp = true }
                    SoftBtn("Kiểm tra root") {
                        scope.launch {
                            rootInfo = "Đang kiểm tra..."
                            val r = withContext(Dispatchers.IO) { Root.run("id").second.trim() }
                            rootInfo = r.ifBlank { "Không có phản hồi (chưa cấp quyền root cho app?)" }
                        }
                    }
                    SoftBtn("Mẫu DiOTO") { setDioto() }
                }
                if (rootInfo.isNotEmpty()) Text(rootInfo, fontSize = 12.sp, color = AccentA)

                Text("Ảnh vuông (hiển thị đầy đủ):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CropSlider("Trái", capX, 0, 100) { capX = it; putInt("cap_x", it) }
                CropSlider("Trên", capY, 0, 100) { capY = it; putInt("cap_y", it) }
                CropSlider("Cỡ", capS, 20, 100) { capS = it; putInt("cap_size", it) }

                CheckRow("Dải ngang cho chế độ thu nhỏ (khi xe hiện thông báo)", capBand) {
                    capBand = it; prefs.edit().putBoolean("cap_band", it).apply()
                }
                if (capBand) {
                    Text(
                        "Dải ngang (rộng gấp đôi cao) dùng để xem trước vùng quan trọng khi Android Auto thu nhỏ ảnh. " +
                            "Ảnh full gửi lên xe chỉ lấy khung vuông (cam), không ghép dải này nữa để tránh hiện \"cửa sổ chia đôi\".",
                        fontSize = 12.sp, color = Dim
                    )
                    CropSlider("Trái", capRx, 0, 100) { capRx = it; putInt("cap_rx", it) }
                    CropSlider("Trên", capRy, 0, 100) { capRy = it; putInt("cap_ry", it) }
                    CropSlider("Rộng", capRw, 20, 100) { capRw = it; putInt("cap_rw", it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradBtn("Thử chụp") { ScreenCapture.start(ctx, capPkg, true) }
                    SoftBtn("Dừng") { ScreenCapture.manual = false; ScreenCapture.stop() }
                }
                Text(Diag.cap, fontSize = 12.sp, color = AccentA)
                CapPreview(capX, capY, capS, capBand, capRx, capRy, capRw)
            }

            CSection("Radio online") {
                Text("Mỗi dòng: Tên|URL", fontSize = 12.sp, color = Dim)
                OutlinedTextField(value = streams, onValueChange = { streams = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                SoftBtn("Lưu radio") { prefs.edit().putString("streams", streams).apply() }
            }

            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A1620))
                    .clickable { act?.quitApp() }.padding(16.dp),
                contentAlignment = Alignment.Center
            ) { Text("⏻  Thoát hoàn toàn", color = Color(0xFFFF6B81), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingApp) {
        AppPicker(
            onPick = { pkg, label ->
                capPkg = pkg
                capLabel = label
                prefs.edit().putString("cap_pkg", pkg).putString("cap_label", label).apply()
                if ("dioto" in (label + pkg).lowercase()) setDioto()
                pickingApp = false
            },
            onDismiss = { pickingApp = false }
        )
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
    BackHandler { LibraryCheck.visible = false }
    val busy = LibraryCheck.scanning || LibraryCheck.suggesting || LibraryCheck.applying
    val states = LibraryCheck.states
    val bad = states.filter { it.ok && it.missing().isNotEmpty() }
    var filter by remember { mutableStateOf("all") }
    val shown = when (filter) {
        "title" -> states.filter { it.ok && it.title.isBlank() }
        "artist" -> states.filter { it.ok && it.artist.isBlank() }
        "album" -> states.filter { it.ok && it.album.isBlank() }
        "cover" -> states.filter { it.ok && !it.hasCover }
        "lyric0" -> states.filter { it.ok && it.lyricKind == 0 }
        "lyric1" -> states.filter { it.ok && it.lyricKind == 1 }
        else -> bad
    }

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
                Pill("Tất cả ${bad.size}", filter == "all") { filter = "all" }
                Pill("Thiếu tên ${states.count { it.ok && it.title.isBlank() }}", filter == "title") { filter = if (filter == "title") "all" else "title" }
                Pill("Thiếu nghệ sĩ ${states.count { it.ok && it.artist.isBlank() }}", filter == "artist") { filter = if (filter == "artist") "all" else "artist" }
                Pill("Thiếu album ${states.count { it.ok && it.album.isBlank() }}", filter == "album") { filter = if (filter == "album") "all" else "album" }
                Pill("Thiếu bìa ${states.count { it.ok && !it.hasCover }}", filter == "cover") { filter = if (filter == "cover") "all" else "cover" }
                Pill("Chưa có lời ${states.count { it.ok && it.lyricKind == 0 }}", filter == "lyric0") { filter = if (filter == "lyric0") "all" else "lyric0" }
                Pill("Có lời, chưa đồng bộ ${states.count { it.ok && it.lyricKind == 1 }}", filter == "lyric1") { filter = if (filter == "lyric1") "all" else "lyric1" }
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
                        Text(
                            p.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { (Engine.pathMap()[p.path] ?: Engine.songById(p.songId))?.let { Engine.editing = it } }
                        )
                        Text(p.summary(), color = Dim, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradBtn("Áp dụng") { LibraryCheck.apply(p) }
                            SoftBtn("Bỏ qua") { LibraryCheck.dismiss(p) }
                        }
                    }
                }
            }
            if (shown.isNotEmpty()) {
                item { Text("Danh sách (${shown.size} bài)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(shown.take(300)) { st ->
                    val song = Engine.pathMap()[st.path] ?: Engine.songById(st.id)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC)
                            .clickable { song?.let { Engine.editing = it } }.padding(10.dp)
                    ) {
                        Text(
                            java.io.File(st.path).name,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("Thiếu: " + st.missing().joinToString(", "), color = AccentB, fontSize = 12.sp)
                        Text("Chạm để sửa hoặc tìm thủ công", color = Dim, fontSize = 11.sp)
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
    val clip = LocalClipboardManager.current
    fun clean(x: String) = if (x.startsWith("Không rõ")) "" else x
    var title by remember(s.id) { mutableStateOf(Engine.title(s)) }
    var artist by remember(s.id) { mutableStateOf(clean(Engine.artist(s))) }
    var album by remember(s.id) { mutableStateOf(clean(Engine.album(s))) }
    var lyricsText by remember(s.id) { mutableStateOf("") }
    var lyricsDirty by remember(s.id) { mutableStateOf(false) }
    var results by remember(s.id) { mutableStateOf<List<Net.Track>>(emptyList()) }
    var artUrl by remember(s.id) { mutableStateOf<String?>(null) }
    var artLink by remember(s.id) { mutableStateOf("") }
    var picked by remember(s.id) { mutableStateOf<ByteArray?>(null) }
    var status by remember(s.id) { mutableStateOf("") }
    var hasCover by remember(s.id) { mutableStateOf(false) }
    var query by remember(s.id) {
        mutableStateOf(
            if (s.path.isNotEmpty()) LibraryCheck.guess(s.path).let { "${it.second} ${it.first}".trim() } else Engine.title(s)
        )
    }

    LaunchedEffect(s.id) {
        val info = withContext(Dispatchers.IO) { if (s.path.isNotEmpty()) Tags.read(s.path) else null }
        if (info != null) {
            if (info.title.isNotBlank()) title = info.title
            if (info.artist.isNotBlank()) artist = info.artist
            if (info.album.isNotBlank()) album = info.album
            lyricsText = info.lyrics
            hasCover = info.hasCover
            if (info.title.isNotBlank()) query = "${info.title} ${info.artist}".trim()
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            }
            if (b != null) { picked = b; artUrl = null; artLink = ""; status = "Đã chọn ảnh bìa. Bấm Lưu để ghi vào file." }
            else status = "Không đọc được ảnh"
        }
    }

    fun openWeb(q: String, images: Boolean) {
        val u = "https://www.google.com/search?" + (if (images) "tbm=isch&" else "") + "q=" + URLEncoder.encode(q, "UTF-8")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: Exception) {
            status = "Không mở được trình duyệt"
        }
    }

    fun clipText(): String = clip.getText()?.text ?: ""
    fun firstLine(x: String) = x.trim().lines().firstOrNull()?.trim() ?: ""

    Dialog(
        onDismissRequest = { Engine.editing = null },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sửa thông tin", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SoftBtn("Đóng") { Engine.editing = null }
                    Spacer(Modifier.width(8.dp))
                    GradBtn("Lưu vào file") {
                        scope.launch {
                            status = "Đang lưu..."
                            val link = artLink.trim()
                            val src = if (link.isNotEmpty()) link else artUrl
                            var cover: ByteArray? = picked
                            if (cover == null && src != null) {
                                cover = withContext(Dispatchers.IO) { Net.bytes(src) }
                                if (cover == null) status = "Không tải được ảnh từ link, các phần khác vẫn được lưu."
                            }
                            val err = withContext(Dispatchers.IO) {
                                Engine.applyTags(s, title, artist, album, if (lyricsDirty) lyricsText else null, cover)
                            }
                            if (err == null) {
                                Engine.editing = null
                            } else {
                                status = "Chưa ghi được vào file: $err. Đã lưu trong app."
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!Tags.canWrite()) {
                        Text("Chưa có quyền ghi vào file: thay đổi chỉ lưu trong app.", color = AccentB, fontSize = 12.sp)
                        SoftBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }
                    }
                    if (status.isNotEmpty()) Text(status, color = AccentA, fontSize = 13.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(title, { title = it }, label = { Text("Tên bài") }, singleLine = true, modifier = Modifier.weight(1f))
                        SoftBtn("📋") { clipText().takeIf { it.isNotBlank() }?.let { title = firstLine(it) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(artist, { artist = it }, label = { Text("Nghệ sĩ") }, singleLine = true, modifier = Modifier.weight(1f))
                        SoftBtn("📋") { clipText().takeIf { it.isNotBlank() }?.let { artist = firstLine(it) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.weight(1f))
                        SoftBtn("📋") { clipText().takeIf { it.isNotBlank() }?.let { album = firstLine(it) } }
                    }

                    Text("Tìm thông tin, album, ảnh bìa", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(
                        query, { query = it }, label = { Text("Từ khoá tìm (sửa tay nếu không ra)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        GradBtn("🔎 Tìm trên internet") {
                            scope.launch {
                                status = "Đang tìm..."
                                val q0 = query.trim()
                                val r = withContext(Dispatchers.IO) { Net.searchLoose(q0) }
                                results = r
                                val exact = r.any { LibraryCheck.similar(q0, it.title) }
                                status = when {
                                    r.isEmpty() -> "Không tìm thấy gì. Hãy sửa từ khoá hoặc dùng nút tìm thủ công trên trình duyệt."
                                    exact -> "Chọn một kết quả bên dưới:"
                                    else -> "Không có kết quả khớp hoàn toàn. Đây là vài kết quả gần giống để bạn chọn:"
                                }
                            }
                        }
                        SoftBtn("🌐 Tìm thủ công") { openWeb(query.trim(), false) }
                        SoftBtn("🌐 Tìm ảnh bìa") { openWeb(query.trim() + " album cover", true) }
                    }
                    results.take(12).forEach { t ->
                        Text(
                            "${t.title} — ${t.artist}\n${t.album}", fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card2)
                                .clickable {
                                    title = t.title; artist = t.artist; album = t.album; artUrl = t.artUrl; picked = null
                                    status = "Đã chọn. Bấm Lưu vào file để ghi (kèm ảnh bìa)."
                                }.padding(12.dp)
                        )
                    }

                    Text(
                        "Ảnh bìa: " + if (hasCover || picked != null || artUrl != null || artLink.isNotBlank()) "có" else "chưa có",
                        fontSize = 12.sp, color = Dim, modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            artLink, { artLink = it; picked = null }, label = { Text("Link ảnh bìa (dán từ trình duyệt)") },
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        SoftBtn("📋") { clipText().takeIf { it.isNotBlank() }?.let { artLink = firstLine(it); picked = null } }
                    }
                    SoftBtn("🖼 Chọn ảnh bìa từ máy") { pick.launch("image/*") }

                    Text("Lời bài hát", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        when (Lrc.kind(lyricsText)) { 2 -> "Đang có lời đồng bộ theo thời gian"; 1 -> "Đang có lời thường (chưa đồng bộ)"; else -> "Chưa có lời" },
                        fontSize = 12.sp, color = Dim
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        GradBtn("🎤 Tải lời tự động") {
                            scope.launch {
                                status = "Đang tải lời..."
                                val r = withContext(Dispatchers.IO) {
                                    Net.lyrics(title.ifBlank { query }, artist, album, s.durationMs / 1000)
                                }
                                if (r == null) {
                                    status = "Không tìm thấy lời. Dùng nút tìm lời trên web rồi dán vào ô bên dưới."
                                } else {
                                    lyricsText = r.text
                                    lyricsDirty = true
                                    status = if (r.synced) "Đã tải lời đồng bộ. Bấm Lưu vào file để ghi." else "Chỉ có lời thường (không chạy theo nhạc). Bấm Lưu để ghi."
                                }
                            }
                        }
                        SoftBtn("🌐 Tìm lời trên web") { openWeb("${title.ifBlank { query }} $artist lời bài hát", false) }
                        SoftBtn("📋 Dán lời") {
                            clipText().takeIf { it.isNotBlank() }?.let { lyricsText = it; lyricsDirty = true }
                        }
                    }
                    OutlinedTextField(
                        value = lyricsText,
                        onValueChange = { lyricsText = it; lyricsDirty = true },
                        label = { Text("Nội dung lời (sửa tay; lời đồng bộ có dạng [mm:ss.xx] dòng lời)") },
                        minLines = 14,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp)
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Thành phần bổ sung

fun treeToPath(uri: Uri): String? = try {
    val id = DocumentsContract.getTreeDocumentId(uri)
    val parts = id.split(":", limit = 2)
    val base = if (parts[0] == "primary") "/storage/emulated/0" else "/storage/${parts[0]}"
    val rel = parts.getOrNull(1) ?: ""
    if (rel.isEmpty()) base else "$base/$rel"
} catch (e: Exception) {
    null
}

@Composable
fun CropSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label $value%", fontSize = 12.sp, color = Dim, modifier = Modifier.width(80.dp))
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AccentA, inactiveTrackColor = Card2)
        )
    }
}

/** Hình chụp mới nhất, khung vuông (cam), khung dải ngang (hồng) để xem trước, và hai kiểu hiển thị trên xe.
 *  Ảnh full chỉ lấy khung vuông (không ghép dải) để tránh hiện cửa sổ chia đôi. */
@Composable
fun CapPreview(xp: Int, yp: Int, sp: Int, band: Boolean, rx: Int, ry: Int, rw: Int) {
    val bmp = ScreenCapture.preview
    if (bmp == null) {
        Text("Chưa có hình. Bấm \"Thử chụp\" để xem trước.", color = Dim, fontSize = 12.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).aspectRatio(bmp.width.toFloat() / bmp.height)) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cw = w * sp / 100f
                    val x0 = (w * xp / 100f).coerceIn(0f, (w - cw).coerceAtLeast(0f))
                    val y0 = (h * yp / 100f).coerceIn(0f, (h - cw).coerceAtLeast(0f))
                    drawRect(AccentA, Offset(x0, y0), Size(cw, cw), style = Stroke(width = 3.dp.toPx()))
                    if (band) {
                        val bw = w * rw / 100f
                        val bh = bw / 2f
                        val bx = (w * rx / 100f).coerceIn(0f, (w - bw).coerceAtLeast(0f))
                        val by = (h * ry / 100f).coerceIn(0f, (h - bh).coerceAtLeast(0f))
                        drawRect(AccentB, Offset(bx, by), Size(bw, bh), style = Stroke(width = 3.dp.toPx()))
                    }
                }
            }
            val art = ScreenCapture.previewArt
            if (art != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Ảnh gửi lên xe", fontSize = 11.sp, color = Dim)
                        Image(
                            bitmap = art.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Khi thu nhỏ (cắt giữa)", fontSize = 11.sp, color = Dim)
                        Box(Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(10.dp))) {
                            // Nếu bật dải ngang: hiện vùng hồng (để xem trước phần quan trọng khi thu nhỏ)
                            // Không ghép vào ảnh full nữa để tránh bị hiện "cửa sổ chia đôi"
                            if (band) {
                                val bw = (bmp.width * rw / 100f).toInt().coerceIn(32, bmp.width)
                                val bh = (bw / 2).coerceIn(16, bmp.height)
                                val bx = (bmp.width * rx / 100f).toInt().coerceIn(0, bmp.width - bw)
                                val by = (bmp.height * ry / 100f).toInt().coerceIn(0, (bmp.height - bh).coerceAtLeast(0))
                                val bandBmp = try {
                                    Bitmap.createScaledBitmap(
                                        Bitmap.createBitmap(bmp, bx, by, bw, bh),
                                        512, 256, true
                                    )
                                } catch (e: Exception) { null }
                                if (bandBmp != null) {
                                    Image(
                                        bitmap = bandBmp.asImageBitmap(), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        bitmap = art.asImageBitmap(), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Image(
                                    bitmap = art.asImageBitmap(), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppPicker(onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardC,
        title = { Text("Chọn app", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Tìm app...") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.height(320.dp)) {
                    items(apps.filter { q.isBlank() || it.second.contains(q, true) || it.first.contains(q, true) }) { a ->
                        Text(
                            "${a.second}\n${a.first}", fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(a.first, a.second) }.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
fun PlaylistPicker(s: Song) {
    var name by remember(s.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Lists.pickFor = null },
        containerColor = CardC,
        title = { Text("Thêm vào playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lists.playlists.keys.forEach { n ->
                    SoftBtn("$n  (${Lists.playlists[n]?.size ?: 0})", Modifier.fillMaxWidth()) {
                        Lists.addTo(n, s)
                        Lists.pickFor = null
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Hoặc tạo playlist mới") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                GradBtn("Tạo và thêm bài") {
                    if (name.isNotBlank()) {
                        Lists.createPlaylist(name, s)
                        Lists.pickFor = null
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { Lists.pickFor = null }) { Text("Đóng") } }
    )
}

@Composable
fun NewPlaylistDialog() {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Lists.creatingPlaylist = false },
        containerColor = CardC,
        title = { Text("Playlist mới", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Tên playlist") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { Lists.createPlaylist(name); Lists.creatingPlaylist = false }) { Text("Tạo", color = AccentA) }
        },
        dismissButton = { TextButton(onClick = { Lists.creatingPlaylist = false }) { Text("Hủy") } }
    )
}

@Composable
fun DeleteConfirm(s: Song) {
    val scope = rememberCoroutineScope()
    var msg by remember(s.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Lists.confirmDelete = null },
        containerColor = CardC,
        title = { Text("Xóa file khỏi máy?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Engine.title(s), fontWeight = FontWeight.SemiBold)
                Text(s.path, fontSize = 11.sp, color = Dim)
                Text("File sẽ bị xóa vĩnh viễn và không thể khôi phục.", fontSize = 13.sp, color = AccentB)
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = AccentA)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val err = withContext(Dispatchers.IO) { Engine.deleteSong(s) }
                    if (err == null) Lists.confirmDelete = null else msg = err
                }
            }) { Text("Xóa", color = AccentB) }
        },
        dismissButton = { TextButton(onClick = { Lists.confirmDelete = null }) { Text("Hủy") } }
    )
}

/** Lời bài hát toàn màn hình, dùng trong LyricsActivity (hiện được trên màn hình khoá). */
@Composable
fun LockLyricsScreen(onClose: () -> Unit, onOpenApp: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        while (true) {
            Engine.tick()
            delay(250)
        }
    }
    val s = Engine.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                s?.let { Engine.title(it) } ?: "AA Music", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(s?.let { Engine.artist(it) } ?: "", color = Dim, fontSize = 13.sp)
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) { LyricsView() }
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonButton(52.dp, false, { Engine.player.seekToPreviousMediaItem() }) { IconSkip(false, Color.White, 24.dp) }
                PlayOrb(Engine.isPlaying) { Engine.togglePlay() }
                NeonButton(52.dp, false, { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }) {
                    IconSkip(true, Color.White, 24.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftBtn("Mở ứng dụng") { onOpenApp() }
                SoftBtn("Đóng") { onClose() }
            }
        }
    }
}


// ------------------------------------------------------------------ Hàng đợi

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val ver = Engine.queueVersion
    val cur = Engine.current
    val q = remember(ver, cur?.id) { Engine.queue() }
    val curIdx = remember(ver, cur?.id) { Engine.player.currentMediaItemIndex }
    Column(Modifier.fillMaxSize().background(Bg)) {
        ScreenHeader("Hàng đợi (${q.size})", onBack) { SoftBtn("Xoá hết") { Engine.clearQueue() } }
        if (q.isEmpty()) {
            EmptyHint("Hàng đợi trống. Dùng menu ⋮ của bài để Phát tiếp theo hoặc Thêm vào hàng đợi.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                itemsIndexed(q) { i, s ->
                    val isCur = i == curIdx
                    Row(
                        Modifier.fillMaxWidth().background(if (isCur) Color(0x22FF7A18) else Color.Transparent)
                            .clickable { Engine.jumpTo(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", color = Dim, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                        Art(s, Modifier.size(44.dp), 96, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isCur) AccentA else Color.White, fontWeight = FontWeight.Medium
                            )
                            Text(Engine.artist(s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
                        }
                        Text("▲", color = Dim, modifier = Modifier.pressable { Engine.moveInQueue(i, i - 1) }.padding(10.dp))
                        Text("▼", color = Dim, modifier = Modifier.pressable { Engine.moveInQueue(i, i + 1) }.padding(10.dp))
                        Text("✕", color = AccentB, modifier = Modifier.pressable { Engine.removeFromQueue(i) }.padding(10.dp))
                    }
                }
            }
        }
    }
}
