package com.hud.aamusic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.widget.Toast
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.border
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
    CollapsingScaffold(header = {
        ScreenHeader("Âm thanh", onBack) {
            Switch(checked = Engine.eqEnabled, onCheckedChange = { Engine.changeEq(it) })
        }
    }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
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
    var dsKey by remember { mutableStateOf(prefs.getString("deepseek_key", "") ?: "") }
    var dsAuto by remember { mutableStateOf(prefs.getBoolean("ds_auto", true)) }
    var aiUrl by remember { mutableStateOf(prefs.getString("ai_url", "") ?: "") }
    var aiModel by remember { mutableStateOf(prefs.getString("ai_model", "") ?: "") }
    var dsTest by remember { mutableStateOf("") }
    var gFirst by remember { mutableStateOf(prefs.getBoolean("name_google_first", false)) }
    var jamendoId by remember { mutableStateOf(prefs.getString("jamendo_id", "") ?: "") }
    var webSrc by remember { mutableStateOf(prefs.getBoolean("web_src", true)) }
    var streams by remember { mutableStateOf(prefs.getString("streams", "") ?: "") }
    var canWrite by remember { mutableStateOf(Tags.canWrite()) }
    var capEnabled by remember { mutableStateOf(prefs.getBoolean("cap_enabled", false)) }
    var capAlways by remember { mutableStateOf(prefs.getBoolean("cap_always", false)) }
    var capPrivate by remember { mutableStateOf(prefs.getBoolean("cap_private", true)) }
    var capPkg by remember { mutableStateOf(prefs.getString("cap_pkg", "") ?: "") }
    var capLabel by remember { mutableStateOf(prefs.getString("cap_label", "") ?: "") }
    var capX by remember { mutableStateOf(prefs.getInt("cap_x", 4)) }
    var capY by remember { mutableStateOf(prefs.getInt("cap_y", 8)) }
    var capS by remember { mutableStateOf(prefs.getInt("cap_size", 92)) }
    var capBand by remember { mutableStateOf(prefs.getBoolean("cap_band", true)) }
    var capKey by remember { mutableStateOf(prefs.getBoolean("cap_key", true)) }
    var capKeyS by remember { mutableStateOf(prefs.getInt("cap_key_s", 40)) }
    var dimAuto by remember { mutableStateOf(prefs.getBoolean("dim_auto", false)) }
    var keepOn by remember { mutableStateOf(prefs.getBoolean("keep_on_screen_off", true)) }
    var fxOff by remember { mutableStateOf(prefs.getBoolean("fx_off_in_car", true)) }
    var pyExt by remember { mutableStateOf(prefs.getBoolean("lyric_pinyin_ext", true)) }
    var audioInfo by remember { mutableStateOf("") }
    var capRx by remember { mutableStateOf(prefs.getInt("cap_rx", 26)) }
    var capRy by remember { mutableStateOf(prefs.getInt("cap_ry", 23)) }
    var capRw by remember { mutableStateOf(prefs.getInt("cap_rw", 70)) }
    var pickingApp by remember { mutableStateOf(false) }
    var rootInfo by remember { mutableStateOf("") }

    fun putInt(k: String, v: Int) { prefs.edit().putInt(k, v).apply() }
    fun setAi(url: String, model: String) {
        aiUrl = url
        aiModel = model
        prefs.edit().putString("ai_url", url).putString("ai_model", model).apply()
    }
    fun setWyn() {
        capX = 26; capY = 14; capS = 72; capRx = 26; capRy = 23; capRw = 70
        prefs.edit().putInt("cap_x", 26).putInt("cap_y", 14).putInt("cap_size", 72)
            .putInt("cap_rx", 26).putInt("cap_ry", 23).putInt("cap_rw", 70).apply()
    }

    LaunchedEffect(Unit) {
        while (true) {
            canWrite = Tags.canWrite()
            delay(1000)
        }
    }

    CollapsingScaffold(header = { ScreenHeader("Cài đặt", onBack) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CSection("Android Auto, thông báo, màn hình khoá", icon = "car") {
                CheckRow("Hiện lời đồng bộ thay tên nghệ sĩ (Android Auto, thông báo, màn hình khoá)", lockLyrics) {
                    lockLyrics = it; prefs.edit().putBoolean("lock_lyrics", it).apply()
                }
                if (Diag.audio.isNotEmpty()) Text(Diag.audio, fontSize = 12.sp, color = AccentA)
                CheckRow("Tắt hiệu ứng âm thanh (EQ, bass, âm trường) khi kết nối Android Auto để ra đủ các loa", fxOff) {
                    fxOff = it; prefs.edit().putBoolean("fx_off_in_car", it).apply(); Engine.refreshFx()
                }
                CheckRow("Lời tiếng Trung trên Android Auto và thông báo: hiện pinyin thay cho chữ Hán", pyExt) {
                    pyExt = it; prefs.edit().putBoolean("lyric_pinyin_ext", it).apply()
                }
                SoftBtn("Kiểm tra âm thanh") { audioInfo = Engine.audioReport() }
                if (audioInfo.isNotEmpty()) Text(audioInfo, fontSize = 11.sp, color = Dim)
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

            CSection("Hình app tốc độ trên Android Auto (cần root)", icon = "phone") {
                Text(
                    "App tốc độ được mở trên màn hình ảo, AA Music chụp lại giao diện rồi hiện thay ảnh bìa trên xe. " +
                        "Khi ngắt kết nối, app đó sẽ bị đóng.",
                    fontSize = 12.sp, color = Dim
                )
                CheckRow("Bật khi kết nối Android Auto", capEnabled) { capEnabled = it; prefs.edit().putBoolean("cap_enabled", it).apply() }
                CheckRow("Chụp cả khi chưa kết nối Android Auto (để xem trên màn hình khoá điện thoại)", capAlways) {
                    capAlways = it; prefs.edit().putBoolean("cap_always", it).apply()
                }
                CheckRow("Màn hình ảo riêng tư (giữ hình app tốc độ khi khoá máy, không hiện đồng hồ)", capPrivate) {
                    capPrivate = it; prefs.edit().putBoolean("cap_private", it).apply()
                }
                Text("Đổi hai mục trên xong, bấm Dừng rồi Thử chụp (hoặc ngắt và nối lại Android Auto) để áp dụng.", fontSize = 11.sp, color = Dim)
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
                    SoftBtn("Mẫu mặc định") { setWyn() }
                }
                if (rootInfo.isNotEmpty()) Text(rootInfo, fontSize = 12.sp, color = AccentA)

                CheckRow("Chọn theo dải ngang (khuyến nghị)", capBand) {
                    capBand = it; prefs.edit().putBoolean("cap_band", it).apply()
                }
                if (capBand) {
                    Text(
                        "Chọn dải ngang chứa thông tin chính (rộng gấp đôi cao). Ảnh gửi lên xe là hình vuông lấy quanh dải này, " +
                            "dải nằm đúng nửa giữa. Khi Android Auto thu nhỏ còn nửa ảnh (cắt giữa) thì dải vẫn đầy đủ, " +
                            "và ảnh không bị ghép chồng.",
                        fontSize = 12.sp, color = Dim
                    )
                    CropSlider("Trái", capRx, 0, 100) { capRx = it; putInt("cap_rx", it) }
                    CropSlider("Trên", capRy, 0, 100) { capRy = it; putInt("cap_ry", it) }
                    CropSlider("Rộng", capRw, 20, 100) { capRw = it; putInt("cap_rw", it) }
                } else {
                    Text("Ảnh vuông (không dùng dải ngang):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    CropSlider("Trái", capX, 0, 100) { capX = it; putInt("cap_x", it) }
                    CropSlider("Trên", capY, 0, 100) { capY = it; putInt("cap_y", it) }
                    CropSlider("Cỡ", capS, 20, 100) { capS = it; putInt("cap_size", it) }
                }
                CheckRow("Nền trong suốt (xoá màu nền của app để hình rõ hơn trên xe)", capKey) {
                    capKey = it; prefs.edit().putBoolean("cap_key", it).apply()
                }
                if (capKey) CropSlider("Độ nhạy", capKeyS, 10, 120) { capKeyS = it; putInt("cap_key_s", it) }
                CheckRow("Khi tắt màn hình: tự bật lại ở chế độ đèn nền tắt để hình trên xe không bị đứng (máy vẫn khoá)", keepOn) {
                    keepOn = it; prefs.edit().putBoolean("keep_on_screen_off", it).apply()
                }
                CheckRow("Tự bật màn hình đen khi bắt đầu chụp (giữ app tốc độ chạy khi không khoá máy)", dimAuto) {
                    dimAuto = it; prefs.edit().putBoolean("dim_auto", it).apply()
                }
                SoftBtn("🌑 Bật màn hình đen ngay") { ctx.startActivity(Intent(ctx, DimActivity::class.java)) }
                Text(
                    "Khi khoá máy, nhiều app tốc độ ngừng cập nhật nên hình trên xe bị đứng. Màn hình đen giữ máy chưa khoá " +
                        "nhưng tắt đèn nền; giữ lâu một chỗ trên màn hình để thoát.",
                    fontSize = 11.sp, color = Dim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradBtn("Thử chụp") { ScreenCapture.start(ctx, capPkg, true) }
                    SoftBtn("Dừng") { ScreenCapture.manual = false; ScreenCapture.stop() }
                }
                Text(Diag.cap, fontSize = 12.sp, color = AccentA)
                CapPreview(capX, capY, capS, capBand, capRx, capRy, capRw)
            }

            CSection("Radio online", icon = "radio") {
                Text("Mỗi dòng: Tên|URL", fontSize = 12.sp, color = Dim)
                OutlinedTextField(value = streams, onValueChange = { streams = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                SoftBtn("Lưu radio") { prefs.edit().putString("streams", streams).apply() }
            }

            CSection("Nâng cao (quyền truy cập, lời bài hát)", icon = "slidersh") {
                Text("Quyền truy cập", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    GradBtn("Cấp quyền nhạc, thông báo") { act?.requestPerms() }
                    SoftBtn("Bật dịch vụ") { ctx.startServiceSafe() }
                }
                Text(
                    if (canWrite) "✓ Đã cho phép ghi tag vào file nhạc" else "✗ Chưa cho phép ghi tag vào file nhạc",
                    color = if (canWrite) Color(0xFF4CD964) else AccentB, fontSize = 13.sp
                )
                if (!canWrite) GradBtn("Cấp quyền Quản lý tất cả tệp") { act?.requestAllFiles() }

                Text("Lời bài hát", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                CheckRow("Tìm thêm trên Kugeci và Lyricsify khi chạy đề xuất trong Kiểm tra tag", webSrc) {
                    webSrc = it; prefs.edit().putBoolean("web_src", it).apply()
                }
                Text("AI: thêm phiên âm pinyin, dịch lời tiếng Trung và dịch tên bài / ca sĩ sang tiếng Việt", fontSize = 12.sp, color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Pill("DeepSeek", aiUrl.isEmpty() || aiUrl.contains("deepseek")) { setAi("https://api.deepseek.com/chat/completions", "deepseek-chat") }
                    Pill("Gemini (miễn phí có hạn mức)", aiUrl.contains("generativelanguage")) {
                        setAi("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.0-flash")
                    }
                    Pill("Groq (miễn phí có hạn mức)", aiUrl.contains("groq")) {
                        setAi("https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile")
                    }
                }
                OutlinedTextField(
                    aiUrl, { aiUrl = it; prefs.edit().putString("ai_url", it.trim()).apply() },
                    label = { Text("URL (tương thích OpenAI, để trống = DeepSeek)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    aiModel, { aiModel = it; prefs.edit().putString("ai_model", it.trim()).apply() },
                    label = { Text("Model (để trống = deepseek-chat)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dsKey, { dsKey = it; prefs.edit().putString("deepseek_key", it.trim()).apply() },
                    label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                CheckRow("Dịch tên bài / ca sĩ bằng Google dịch (không cần API key, bỏ qua AI)", gFirst) {
                    gFirst = it; prefs.edit().putBoolean("name_google_first", it).apply()
                }
                CheckRow("Tự gửi AI khi dán lời tiếng Trung", dsAuto) {
                    dsAuto = it; prefs.edit().putBoolean("ds_auto", it).apply()
                }
                SoftBtn("Kiểm tra AI") {
                    scope.launch {
                        dsTest = "Đang kiểm tra..."
                        val (r, m) = withContext(Dispatchers.IO) { DeepSeek.annotate("[00:01.00]你好，世界\n[00:03.00]我爱你") }
                        dsTest = if (r != null) "OK: $m\n$r" else m
                    }
                }
                if (dsTest.isNotEmpty()) Text(dsTest, fontSize = 12.sp, color = AccentA)
                Text(
                    "HTTP 402 nghĩa là tài khoản DeepSeek chưa có số dư (DeepSeek không tặng sẵn tiền): cần nạp tiền tại " +
                        "platform.deepseek.com hoặc chọn Gemini / Groq miễn phí. Nội dung được gửi tới dịch vụ bạn chọn.",
                    fontSize = 11.sp, color = Dim
                )

                Text("Nhạc online (Jamendo, nhạc Creative Commons)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                OutlinedTextField(
                    jamendoId, { jamendoId = it; prefs.edit().putString("jamendo_id", it.trim()).apply() },
                    label = { Text("Jamendo Client ID (miễn phí tại developer.jamendo.com)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CSection("Giới thiệu ứng dụng (About)", icon = "info") {
                Text("AA Music", fontWeight = FontWeight.SemiBold)
                Text(
                    "Trình nghe nhạc cho điện thoại và Android Auto: thư viện nhạc, lời đồng bộ, Equalizer 10 dải, " +
                        "sửa tag, đổi tên file, hình app tốc độ làm ảnh bìa trên xe, nhạc Creative Commons từ Jamendo.",
                    fontSize = 13.sp, color = Dim
                )
            }

            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF3A1620))
                    .clickable { act?.quitApp() }.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPower(Color(0xFFFF6B81), 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Thoát hoàn toàn", color = Color(0xFFFF6B81), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingApp) {
        AppPicker(
            onPick = { pkg, label ->
                capPkg = pkg
                capLabel = label
                prefs.edit().putString("cap_pkg", pkg).putString("cap_label", label).apply()
                if ("wyn" in (label + pkg).lowercase()) setWyn()
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
    var renameApply by remember { mutableStateOf(Engine.prefs.getBoolean("rename_on_apply", true)) }
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
            GradBtn(if (LibraryCheck.scanning) "Đang quét..." else "Quét thư viện") { LibraryCheck.scanFull() }
            SoftBtn("Tìm đề xuất (internet)") { LibraryCheck.suggestAll() }
            if (busy) SoftBtn("Dừng") { LibraryCheck.stop() }
        }
        CheckRow("Đổi tên file thành \"Tên bài - Ca sĩ\" khi áp dụng đề xuất (tiếng Trung: chỉ tên tiếng Việt)", renameApply) {
            renameApply = it; Engine.prefs.edit().putBoolean("rename_on_apply", it).apply()
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
        ScrollList(bottom = 40.dp, spacing = 8.dp) {
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
    var useNewCover by remember(s.id) { mutableStateOf(true) }
    var dsBusy by remember(s.id) { mutableStateOf(false) }
    var dsMsg by remember(s.id) { mutableStateOf("") }
    var renameFile by remember(s.id) { mutableStateOf(true) }
    var viNames by remember(s.id) { mutableStateOf(true) }
    var syncOpen by remember(s.id) { mutableStateOf(false) }

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
            if (b != null) { picked = b; artUrl = null; artLink = ""; useNewCover = true; status = "Đã chọn ảnh bìa. Bấm Lưu để ghi vào file." }
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

    /** Từ khoá tìm tự động: tên bài + ca sĩ; nếu là tiếng Trung thì dùng tên tiếng Trung (dễ ra kết quả hơn). */
    fun searchQuery(): String {
        val t = title.trim()
        val a = artist.trim()
        return if (Chinese.hasHan(t) || Chinese.hasHan(a)) "${Chinese.zhPart(t)} ${Chinese.zhPart(a)}".trim() else "$t $a".trim()
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
                            var cover: ByteArray? = if (useNewCover) picked else null
                            if (useNewCover && cover == null && src != null) {
                                cover = withContext(Dispatchers.IO) { Net.bytes(src) }
                                if (cover == null) status = "Không tải được ảnh từ link, các phần khác vẫn được lưu."
                            }
                            // Tên bài / ca sĩ tiếng Trung: tag ghi "Tiếng Việt (中文)", tên file chỉ dùng tiếng Việt
                            var tTitle = title.trim()
                            var tArtist = artist.trim()
                            var fileTitle = Chinese.viPart(tTitle) ?: tTitle
                            var fileArtist = Chinese.viPart(tArtist) ?: tArtist
                            val needT = Chinese.viPart(tTitle) == null && tTitle.any { Chinese.isHan(it) }
                            val needA = Chinese.viPart(tArtist) == null && tArtist.any { Chinese.isHan(it) }
                            if (viNames && (needT || needA)) {
                                status = "Đang dịch tên bài và ca sĩ sang tiếng Việt..."
                                val (vi, msg) = withContext(Dispatchers.IO) { DeepSeek.translateNames(tTitle, tArtist) }
                                if (vi != null) {
                                    if (needT) { fileTitle = vi.first; tTitle = "${vi.first} ($title)" }
                                    if (needA) { fileArtist = vi.second; tArtist = "${vi.second} ($artist)" }
                                } else {
                                    status = "Không dịch được tên ($msg), giữ nguyên tên gốc."
                                }
                            }
                            val err = withContext(Dispatchers.IO) {
                                Engine.applyTags(s, tTitle, tArtist, album, if (lyricsDirty) lyricsText else null, cover)
                            }
                            if (err == null) {
                                var note = ""
                                if (renameFile) {
                                    val base = if (fileArtist.isBlank()) fileTitle else "$fileTitle - $fileArtist"
                                    val e2 = withContext(Dispatchers.IO) { Engine.renameSongFile(s, base) }
                                    if (e2 != null) note = " (chưa đổi tên file: $e2)"
                                }
                                if (note.isEmpty()) Engine.editing = null else status = "Đã ghi tag.$note"
                            } else {
                                status = "Chưa ghi được vào file: $err. Đã lưu trong app."
                            }
                        }
                    }
                }
                if (syncOpen) Box(Modifier.weight(1f)) {
                    LyricSyncPanel(
                        s, lyricsText,
                        onApply = { t ->
                            lyricsText = t
                            lyricsDirty = true
                            syncOpen = false
                            status = "Đã áp dụng lời đồng bộ. Bấm Lưu vào file để ghi."
                        },
                        onClose = { syncOpen = false }
                    )
                } else Column(
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
                    CheckRow("Đổi tên file thành \"Tên bài - Ca sĩ\" khi lưu", renameFile) { renameFile = it }
                    if (title.any { Chinese.isHan(it) } || artist.any { Chinese.isHan(it) }) {
                        CheckRow("Tên bài / ca sĩ tiếng Trung: ghi tiếng Việt kèm tiếng Trung trong ngoặc (AI hoặc Google dịch)", viNames) { viNames = it }
                    }

                    Text("Tìm thông tin, album, ảnh bìa", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        GradBtn("🔎 Tìm trên internet") {
                            scope.launch {
                                status = "Đang tìm..."
                                val q0 = searchQuery()
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
                        GradBtn("✨ Đề xuất (tên tiếng Trung)") {
                            scope.launch {
                                status = "Đang tìm các bài của ca sĩ bằng tên tiếng Trung..."
                                val guessA = if (s.path.isNotEmpty()) LibraryCheck.guess(s.path).first else ""
                                val guessT = if (s.path.isNotEmpty()) LibraryCheck.guess(s.path).second else ""
                                val aq = Chinese.zhPart(artist.trim()).ifBlank { guessA }
                                val tq = Chinese.zhPart(title.trim()).ifBlank { guessT }
                                val (list, msg) = withContext(Dispatchers.IO) { Net.suggestChinese(aq, tq) }
                                results = list
                                status = msg
                            }
                        }
                        SoftBtn("🌐 Tìm thủ công") { openWeb(searchQuery(), false) }
                        SoftBtn("🌐 Tìm ảnh bìa") { openWeb(searchQuery() + " album cover", true) }
                    }
                    results.take(40).forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card2)
                                .clickable {
                                    title = t.title; artist = t.artist; album = t.album
                                    artUrl = t.artUrl; picked = null; artLink = ""; useNewCover = t.artUrl != null
                                    status = "Đã chọn. Xem ảnh bìa bên dưới, chọn dùng hay không rồi bấm Lưu vào file."
                                    // Kết quả tiếng Trung: đổi sang "Tên Việt (中文)", viết hoa chữ đầu mỗi từ
                                    val needT = Chinese.hasHan(t.title) && Chinese.viPart(t.title) == null
                                    val needA = Chinese.hasHan(t.artist) && Chinese.viPart(t.artist) == null
                                    if (viNames && (needT || needA)) {
                                        status = "Đang đổi tên sang tiếng Việt..."
                                        scope.launch {
                                            val (vi, msg) = withContext(Dispatchers.IO) { DeepSeek.translateNames(t.title, t.artist) }
                                            if (vi != null) {
                                                if (needT) title = "${vi.first} (${t.title})"
                                                if (needA) artist = "${vi.second} (${t.artist})"
                                                status = "Đã chọn và đổi tên sang tiếng Việt. Kiểm tra rồi bấm Lưu vào file."
                                            } else {
                                                status = "Đã chọn. Không dịch được tên ($msg), giữ tên gốc."
                                            }
                                        }
                                    }
                                }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RemoteImage(t.thumbUrl, Modifier.size(60.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("${t.title} — ${t.artist}\n${t.album}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }

                    val candidateUrl = artLink.trim().ifEmpty { artUrl }
                    if (picked != null || candidateUrl != null) {
                        Text("So sánh ảnh bìa", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Hiện tại", fontSize = 11.sp, color = Dim)
                                Art(s, Modifier.fillMaxWidth().aspectRatio(1f), 256, 12.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Ảnh mới", fontSize = 11.sp, color = Dim)
                                val pk = picked
                                if (pk != null) PickedImage(pk, Modifier.fillMaxWidth().aspectRatio(1f))
                                else RemoteImage(candidateUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                            }
                        }
                        CheckRow("Dùng ảnh bìa mới (bỏ chọn để giữ ảnh hiện tại)", useNewCover) { useNewCover = it }
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
                        SoftBtn("📋") { clipText().takeIf { it.isNotBlank() }?.let { artLink = firstLine(it); picked = null; useNewCover = true } }
                    }
                    SoftBtn("🖼 Chọn ảnh bìa từ máy") { pick.launch("image/*") }

                    Text("Lời bài hát", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        when (Lrc.kind(lyricsText)) { 2 -> "Đang có lời đồng bộ theo thời gian"; 1 -> "Đang có lời thường (chưa đồng bộ)"; else -> "Chưa có lời" },
                        fontSize = 12.sp, color = Dim
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SoftBtn("🌐 Tìm lời trên web") { openWeb("${searchQuery()} lời bài hát", false) }
                        SoftBtn("📋 Dán lời") {
                            val t = clipText()
                            if (t.isNotBlank()) {
                                lyricsText = t
                                lyricsDirty = true
                                val key = Engine.prefs.getString("deepseek_key", "").orEmpty()
                                if (Chinese.isChinese(t) && key.isNotBlank() && Engine.prefs.getBoolean("ds_auto", true) && !dsBusy) {
                                    dsBusy = true
                                    dsMsg = "Lời tiếng Trung: đang gửi AI..."
                                    scope.launch {
                                        val (res, msg) = withContext(Dispatchers.IO) { DeepSeek.annotate(t) }
                                        if (res != null) { lyricsText = res; lyricsDirty = true }
                                        dsMsg = msg
                                        dsBusy = false
                                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        SoftBtn("⏱ Sửa đồng bộ") {
                            if (lyricsText.isBlank()) {
                                dsMsg = "Chưa có lời: hãy dán lời bài hát vào ô bên dưới trước."
                                Toast.makeText(ctx, dsMsg, Toast.LENGTH_LONG).show()
                            } else {
                                syncOpen = true
                            }
                        }
                        // Nút phiên âm + dịch (AI) nằm cuối hàng như trước
                        GradBtn(if (dsBusy) "⏳ Đang gửi AI..." else "🈶 Phiên âm + dịch (AI)") {
                            if (!dsBusy) {
                                if (lyricsText.isBlank()) {
                                    dsMsg = "Chưa có lời: hãy dán lời bài hát vào ô bên dưới trước."
                                    Toast.makeText(ctx, dsMsg, Toast.LENGTH_LONG).show()
                                } else {
                                    dsBusy = true
                                    dsMsg = "Đang gửi AI, có thể mất vài chục giây..."
                                    Toast.makeText(ctx, "Đang gửi AI...", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        val (res, msg) = withContext(Dispatchers.IO) { DeepSeek.annotate(lyricsText) }
                                        if (res != null) {
                                            lyricsText = res
                                            lyricsDirty = true
                                            dsMsg = "$msg. Lời mới đã nằm trong ô bên dưới, kiểm tra rồi bấm Lưu vào file."
                                        } else {
                                            dsMsg = msg
                                        }
                                        dsBusy = false
                                        Toast.makeText(ctx, dsMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                    if (dsMsg.isNotEmpty()) Text(dsMsg, color = AccentA, fontSize = 13.sp)
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

/** Hình chụp mới nhất, khung vuông (cam), khung dải ngang (hồng) và hai kiểu hiển thị trên xe. */
@Composable
fun CapPreview(xp: Int, yp: Int, sp: Int, band: Boolean, rx: Int, ry: Int, rw: Int) {
    val bmp = ScreenCapture.preview
    if (bmp == null) {
        Text("Chưa có hình. Bấm \"Thử chụp\" để xem trước.", color = Dim, fontSize = 12.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).aspectRatio(bmp.width.toFloat() / bmp.height)) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().clipToBounds()) {
                    val w = size.width
                    val h = size.height
                    if (band) {
                        val bw = w * rw / 100f
                        val bh = bw / 2f
                        val bx = (w * rx / 100f).coerceIn(0f, (w - bw).coerceAtLeast(0f))
                        val by = (h * ry / 100f).coerceIn(0f, (h - bh).coerceAtLeast(0f))
                        val sy = by + bh / 2f - bw / 2f   // hình vuông gửi lên xe, dải nằm đúng giữa
                        drawRect(AccentA, Offset(bx, sy), Size(bw, bw), style = Stroke(width = 3.dp.toPx()))
                        drawRect(AccentB, Offset(bx, by), Size(bw, bh), style = Stroke(width = 3.dp.toPx()))
                    } else {
                        val cw = w * sp / 100f
                        val x0 = (w * xp / 100f).coerceIn(0f, (w - cw).coerceAtLeast(0f))
                        val y0 = (h * yp / 100f).coerceIn(0f, (h - cw).coerceAtLeast(0f))
                        drawRect(AccentA, Offset(x0, y0), Size(cw, cw), style = Stroke(width = 3.dp.toPx()))
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
    var showImage by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                s?.let { Engine.title(it) } ?: "AA Music", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(s?.let { Engine.artist(it) } ?: "", color = Dim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Pill("Lời bài hát", !showImage) { showImage = false }
                Pill("Hình app", showImage) { showImage = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
                if (showImage) {
                    val f = ScreenCapture.liveFrame
                    if (f != null) {
                        Image(bitmap = f.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        EmptyHint("Chưa có hình. Bật mục Hình app tốc độ trong Cài đặt, rồi kết nối Android Auto hoặc bật \"Chụp cả khi chưa kết nối\".")
                    }
                } else {
                    LyricsView()
                }
            }
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
    val shuffle = Engine.shuffle
    val q = remember(ver, cur?.id) { Engine.queue() }
    val order = remember(ver, cur?.id, shuffle) { Engine.queueOrder() }
    val curIdx = remember(ver, cur?.id) { Engine.player.currentMediaItemIndex }
    CollapsingScaffold(header = {
        ScreenHeader("Hàng đợi (${q.size})", onBack) { SoftBtn("Xoá hết") { Engine.clearQueue() } }
        if (shuffle) {
            Text(
                "Đang phát ngẫu nhiên: danh sách theo đúng thứ tự sẽ phát",
                color = AccentA, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
    }) {
        if (q.isEmpty()) {
            EmptyHint("Hàng đợi trống. Dùng menu ⋮ của bài để Phát tiếp theo hoặc Thêm vào hàng đợi.")
        } else {
            ScrollList(bottom = 12.dp) {
                itemsIndexed(order) { k, i ->
                    val s = q.getOrNull(i) ?: return@itemsIndexed
                    val isCur = i == curIdx
                    Row(
                        Modifier.fillMaxWidth().background(if (isCur) Color(0x22FF7A18) else Color.Transparent)
                            .clickable { Engine.jumpTo(i) }.padding(start = 12.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${k + 1}", color = Dim, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                        Art(s, Modifier.size(44.dp), 96, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                                color = if (isCur) AccentA else Color.White, fontWeight = FontWeight.Medium
                            )
                            Text(Engine.artist(s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
                        }
                        if (!shuffle) {
                            Text("▲", color = Dim, modifier = Modifier.pressable { Engine.moveInQueue(i, i - 1) }.padding(horizontal = 7.dp, vertical = 10.dp))
                            Text("▼", color = Dim, modifier = Modifier.pressable { Engine.moveInQueue(i, i + 1) }.padding(horizontal = 7.dp, vertical = 10.dp))
                        }
                        Text("✕", color = AccentB, modifier = Modifier.pressable { Engine.removeFromQueue(i) }.padding(horizontal = 9.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Ảnh từ internet / từ máy (xem trước)

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) { Net.thumb(url) }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Card2), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(if (url == null) "—" else "…", color = Dim)
        }
    }
}

@Composable
fun PickedImage(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bmp = remember(bytes) {
        try {
            val o = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        } catch (e: Throwable) {
            null
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Card2), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}


// ------------------------------------------------------------------ Nhạc online (Jamendo)

@Composable
private fun SmallIconBtn(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).pressable(onClick).clip(RoundedCornerShape(14.dp)).background(Color(0xFF161B26))
            .border(1.dp, Color(0xFF232A38), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) { Ic(icon, Color.White, 20.dp) }
}

@Composable
private fun OnlineRow(n: Int, t: OnlineTrack, onPlay: () -> Unit, onQueue: () -> Unit, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$n", color = AccentA, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(34.dp))
        RemoteImage(t.imageUrl, Modifier.size(58.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                Text("  ·  Jamendo", color = AccentB, fontSize = 13.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp))
        SmallIconBtn("play", onPlay)
        Spacer(Modifier.width(6.dp))
        SmallIconBtn("listplus", onQueue)
        if (t.downloadAllowed) {
            Spacer(Modifier.width(6.dp))
            SmallIconBtn("download", onDownload)
        }
    }
}

@Composable
private fun SectionTitle(icon: String, text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, AccentA, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun OnlineTab() {
    val scope = rememberCoroutineScope()
    val prefs = Engine.prefs
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var chart by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var resTitle by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var hist by remember { mutableStateOf((prefs.getString("online_hist", "") ?: "").split("\n").filter { it.isNotBlank() }) }
    var dlFor by remember { mutableStateOf<OnlineTrack?>(null) }
    val hasKey = prefs.getString("jamendo_id", "").orEmpty().isNotBlank()

    fun load(label: String, call: () -> Pair<List<OnlineTrack>?, String>) {
        msg = "Đang tải..."
        scope.launch {
            val (list, m) = withContext(Dispatchers.IO) { call() }
            results = list ?: emptyList()
            resTitle = label
            msg = m
        }
    }

    fun doSearch(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        q = t
        hist = (listOf(t) + hist.filter { it != t }).take(8)
        prefs.edit().putString("online_hist", hist.joinToString("\n")).apply()
        load("KẾT QUẢ CHO \"${t.uppercase()}\"") { Online.search(t) }
    }

    LaunchedEffect(hasKey) {
        if (hasKey && chart.isEmpty()) {
            val (l, m) = withContext(Dispatchers.IO) { Online.popular() }
            if (l != null) chart = l else msg = m
        }
    }

    fun playList(list: List<OnlineTrack>, i: Int) = Engine.playOnline(list.map { Online.toSong(it) }, i)
    fun queue(t: OnlineTrack) {
        val sg = Online.toSong(t)
        Engine.registerOnline(sg)
        Engine.addToQueue(sg)
        msg = "Đã thêm vào hàng đợi: ${t.title}"
    }

    ScrollList(bottom = 12.dp) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    q, { q = it }, singleLine = true,
                    placeholder = { Text("Tìm bài hát, nghệ sĩ trên Jamendo...", color = Dim, maxLines = 1) },
                    leadingIcon = { Ic("search", Dim, 24.dp) },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentA, unfocusedBorderColor = Color(0xFF2A3140),
                        focusedContainerColor = Color(0xFF111620), unfocusedContainerColor = Color(0xFF111620)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                GradBtn("Tìm", icon = "search") { doSearch(q) }
            }
        }
        if (!hasKey) {
            item {
                Text(
                    "Cần Jamendo Client ID (miễn phí tại developer.jamendo.com): nhập ở Cài đặt > Nâng cao.",
                    color = AccentB, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        item {
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Tất cả", true) { }
                Pill("Jamendo · nhạc Creative Commons", false) { }
            }
        }
        if (hist.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Ic("history", Dim, 22.dp)
                    for (h in hist) {
                        Box(
                            Modifier.pressable { doSearch(h) }.clip(RoundedCornerShape(18.dp)).background(Color(0xFF111620))
                                .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 9.dp)
                        ) { Text(h, fontSize = 15.sp, color = Color(0xFFCFD3D8), maxLines = 1) }
                    }
                    Text("Xoá", color = Dim, fontSize = 15.sp, modifier = Modifier.pressable {
                        hist = emptyList(); prefs.edit().remove("online_hist").apply()
                    }.padding(8.dp))
                }
            }
        }
        if (msg.isNotEmpty()) {
            item { Text(msg, color = AccentA, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (results.isNotEmpty()) {
            item {
                SectionTitle("music", resTitle) {
                    GradBtn("Phát tất cả", icon = "play") { playList(results, 0) }
                }
            }
            itemsIndexed(results) { i, t ->
                OnlineRow(i + 1, t, { playList(results, i) }, { queue(t) }, { dlFor = t })
            }
        } else {
            item { SectionTitle("listmusic", "THỂ LOẠI GỢI Ý") }
            item {
                val genres = listOf(
                    Triple("Pop", "pop", listOf(Color(0xFFB0426A), Color(0xFF3A1B33))),
                    Triple("Rock", "rock", listOf(Color(0xFF8A3B1E), Color(0xFF2B1712))),
                    Triple("Electronic", "electronic", listOf(Color(0xFF3B4C9B), Color(0xFF181F3F))),
                    Triple("Chill", "chillout", listOf(Color(0xFF1F7A74), Color(0xFF10302F))),
                    Triple("Jazz", "jazz", listOf(Color(0xFF8A6A1E), Color(0xFF2E2510))),
                    Triple("Hip-hop", "hiphop", listOf(Color(0xFF6A2B9B), Color(0xFF241238))),
                    Triple("Cổ điển", "classical", listOf(Color(0xFF4B5563), Color(0xFF1B2028)))
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((name, tag, cols) in genres) {
                        Box(
                            Modifier.size(width = 150.dp, height = 96.dp).pressable { load("THỂ LOẠI: ${name.uppercase()}") { Online.popular(tag) } }
                                .clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(cols))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp)).padding(14.dp)
                        ) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart))
                            Box(Modifier.align(Alignment.TopEnd)) { Ic("music", Color(0xCCFFFFFF), 22.dp) }
                        }
                    }
                }
            }
            if (chart.isNotEmpty()) {
                item {
                    SectionTitle("flame", "BẢNG XẾP HẠNG TUẦN") {
                        SoftBtn("Phát tất cả", icon = "play") { playList(chart, 0) }
                    }
                }
                itemsIndexed(chart) { i, t ->
                    OnlineRow(i + 1, t, { playList(chart, i) }, { queue(t) }, { dlFor = t })
                }
            }
        }
    }

    val d = dlFor
    if (d != null) {
        AlertDialog(
            onDismissRequest = { dlFor = null },
            containerColor = CardC,
            title = { Text("Tải về: ${d.title}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chọn chất lượng. File lưu vào thư mục Music/AA Music.", fontSize = 12.sp, color = Dim)
                    for ((fmt, label) in Online.QUALITIES) {
                        SoftBtn(label, Modifier.fillMaxWidth()) {
                            dlFor = null
                            msg = "Đang tải ${d.title}..."
                            scope.launch(Dispatchers.IO) {
                                val err = Online.download(d, fmt) { m -> msg = m }
                                msg = err ?: "Đã tải xong: ${d.title}. Bấm nút làm mới ở đầu màn hình nếu chưa thấy trong thư viện."
                            }
                        }
                    }
                    if (d.license.isNotEmpty()) Text("Giấy phép: ${d.license}", fontSize = 11.sp, color = Dim)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dlFor = null }) { Text("Đóng") } }
        )
    }
}
