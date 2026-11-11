package com.hud.aamusic

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

// ------------------------------------------------------------------ Thiết kế chung

val Bg = Color(0xFF0B0D12)
val CardC = Color(0xFF151922)
val Card2 = Color(0xFF1E2430)
val AccentA = Color(0xFFFF7A18)
val AccentB = Color(0xFFFF3D81)
val Dim = Color(0xFF8B93A1)
val AccentBrush = Brush.horizontalGradient(listOf(AccentA, AccentB))

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentA,
            onPrimary = Color.Black,
            secondary = AccentB,
            background = Bg,
            surface = CardC,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Card2,
            onSurfaceVariant = Color(0xFFCFD3D8)
        ),
        content = content
    )
}

@Composable
fun GradBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(AccentBrush).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun SoftBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(Card2).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentA else Card2)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardC).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

// ------------------------------------------------------------------ Gốc ứng dụng

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> LibraryScreen()
                1 -> NowPlayingScreen()
                2 -> EqScreen()
                else -> SettingsScreen()
            }
            Column(Modifier.align(Alignment.BottomCenter)) {
                if (tab != 1) MiniPlayer { tab = 1 }
                FloatingNav(tab) { tab = it }
            }
            if (LibraryCheck.visible) ScanScreen()
        }
    }
    Engine.editing?.let { TagEditor(it) }
}

@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf("🎵" to "Thư viện", "▶" to "Đang phát", "🎚" to "EQ", "⚙" to "Cài đặt")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(30.dp)).background(Color(0xF2151922)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, item ->
            val sel = tab == i
            Row(
                Modifier.clip(RoundedCornerShape(24.dp))
                    .then(if (sel) Modifier.background(AccentBrush) else Modifier)
                    .clickable { onTab(i) }
                    .padding(horizontal = if (sel) 16.dp else 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.first, fontSize = 18.sp)
                if (sel) {
                    Spacer(Modifier.width(6.dp))
                    Text(item.second, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Ảnh bìa

@Composable
fun Art(song: Song?, modifier: Modifier = Modifier, px: Int = 256, radius: Dp = 12.dp) {
    val ver = Engine.artVersion
    val bmp by produceState<Bitmap?>(null, song?.id, ver) {
        value = withContext(Dispatchers.IO) { Engine.loadArt(song, px) }
    }
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Card2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", fontSize = 26.sp, color = Dim)
        }
    }
}

// ------------------------------------------------------------------ Mini player

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val s = Engine.current ?: return
    val frac = if (Engine.durationMs > 0) (Engine.positionMs.toFloat() / Engine.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xF21E2430)).clickable { onOpen() }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s, Modifier.size(46.dp), 128, 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(Engine.artist(s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp)
            }
            Text(
                "⏮", fontSize = 20.sp,
                modifier = Modifier.clickable { Engine.player.seekToPreviousMediaItem() }.padding(8.dp)
            )
            Text(
                if (Engine.isPlaying) "⏸" else "▶", fontSize = 22.sp,
                modifier = Modifier.clickable { Engine.togglePlay() }.padding(8.dp)
            )
            Text(
                "⏭", fontSize = 20.sp,
                modifier = Modifier.clickable { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }.padding(8.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Card2)) {
            Box(Modifier.fillMaxWidth(frac).height(3.dp).background(AccentBrush))
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

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Thư viện", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SoftBtn("🩺 Kiểm tra tag") { LibraryCheck.visible = true }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Tìm bài hát, nghệ sĩ, album...", color = Dim) },
            shape = RoundedCornerShape(26.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentA, unfocusedBorderColor = Card2,
                focusedContainerColor = CardC, unfocusedContainerColor = CardC
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Bài hát", "Album", "Nghệ sĩ", "Radio").forEachIndexed { i, t ->
                Pill(t, sub == i && filter == null) { sub = i; filter = null }
            }
        }
        Spacer(Modifier.height(8.dp))
        val f = filter
        when {
            f != null -> {
                Text("← ${f.second}", color = AccentA, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { filter = null }.padding(16.dp))
                SongList(all.filter { if (f.first == "album") Engine.album(it) == f.second else Engine.artist(it) == f.second }, true)
            }
            sub == 0 -> SongList(all.filter { match(it) }, true)
            sub == 1 -> GroupList(all.filter { match(it) }.groupBy { Engine.album(it) }) { filter = "album" to it }
            sub == 2 -> GroupList(all.filter { match(it) }.groupBy { Engine.artist(it) }) { filter = "artist" to it }
            else -> {
                val radio = Engine.streams()
                if (radio.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Chưa có radio. Thêm ở Cài đặt (mỗi dòng: Tên|URL).", color = Dim,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    }
                } else SongList(radio, false)
            }
        }
    }
}

@Composable
fun GroupList(groups: Map<String, List<Song>>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
        items(groups.keys.sorted()) { k ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(k) }.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(groups[k]?.firstOrNull(), Modifier.size(56.dp), 128, 14.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${groups[k]?.size ?: 0} bài", color = Dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SongList(list: List<Song>, header: Boolean) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Không có bài hát. Hãy cấp quyền truy cập nhạc ở Cài đặt.", color = Dim,
                textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
        if (header) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradBtn("▶ Phát tất cả") { Engine.player.shuffleModeEnabled = false; Engine.play(list, 0) }
                    SoftBtn("🔀 Ngẫu nhiên") { Engine.playShuffled(list) }
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s -> SongRow(s) { Engine.play(list, i) } }
    }
}

@Composable
fun SongRow(s: Song, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val playing = Engine.current?.id == s.id
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s, Modifier.size(56.dp), 128, 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) AccentA else Color.White, fontWeight = FontWeight.Medium
            )
            Text(
                "${Engine.artist(s)} • ${Engine.album(s)}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 12.sp
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Dim) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Sửa thông tin / bìa / lời") },
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
    val hasLyrics = Engine.lyricLines.isNotEmpty() || Engine.plainLyrics.isNotEmpty()
    LaunchedEffect(Unit) {
        while (true) {
            Engine.tick()
            delay(250)
        }
    }
    val ver = Engine.artVersion
    val tint by produceState(Card2, s.id, ver) {
        value = withContext(Dispatchers.IO) {
            val b = Engine.loadArt(s, 96)
            if (b == null) Card2 else {
                val dom = Palette.from(b).generate().getDominantColor(0xFF2A3140.toInt())
                Color(ColorUtils.blendARGB(dom, android.graphics.Color.BLACK, 0.55f))
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Bg)))) {
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                if (showLyrics) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0x66000000)).padding(12.dp)) {
                        LyricsView()
                        Text(
                            "✕", fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopEnd).clickable { showLyrics = false }.padding(8.dp)
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .shadow(24.dp, RoundedCornerShape(28.dp))
                            .clickable(enabled = hasLyrics) { showLyrics = true }
                    ) {
                        Art(s, Modifier.fillMaxSize(), 720, 28.dp)
                        if (hasLyrics) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(Color(0x99000000))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) { Text("Nhấn để xem lời bài hát", fontSize = 11.sp, color = Color.White) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                Engine.title(s), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Text(
                "${Engine.artist(s)} • ${Engine.album(s)}", color = Color(0xFFCFD3D8), maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 14.sp
            )
            val dur = max(Engine.durationMs, 1L)
            Slider(
                value = Engine.positionMs.toFloat().coerceIn(0f, dur.toFloat()),
                onValueChange = { Engine.seeking = true; Engine.positionMs = it.toLong() },
                onValueChangeFinished = { Engine.player.seekTo(Engine.positionMs); Engine.seeking = false },
                valueRange = 0f..dur.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = AccentA, inactiveTrackColor = Color(0x44FFFFFF)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(Engine.positionMs), color = Dim, fontSize = 12.sp)
                Text(fmt(Engine.durationMs), color = Dim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔀", fontSize = 22.sp, color = if (Engine.shuffle) AccentA else Dim,
                    modifier = Modifier.clickable { Engine.toggleShuffle() }.padding(10.dp)
                )
                Text(
                    "⏮", fontSize = 30.sp,
                    modifier = Modifier.clickable { Engine.player.seekToPreviousMediaItem() }.padding(10.dp)
                )
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(AccentBrush).clickable { Engine.togglePlay() },
                    Alignment.Center
                ) { Text(if (Engine.isPlaying) "⏸" else "▶", fontSize = 32.sp, color = Color.White) }
                Text(
                    "⏭", fontSize = 30.sp,
                    modifier = Modifier.clickable { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }.padding(10.dp)
                )
                Text(
                    if (Engine.repeatMode == Player.REPEAT_MODE_ONE) "🔂" else "🔁", fontSize = 22.sp,
                    color = if (Engine.repeatMode != Player.REPEAT_MODE_OFF) AccentA else Dim,
                    modifier = Modifier.clickable { Engine.cycleRepeat() }.padding(10.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftBtn(if (showLyrics) "🖼 Ảnh bìa" else "🎤 Lời bài hát") { showLyrics = !showLyrics }
                SoftBtn("✏ Sửa thông tin") { if (!s.streaming) Engine.editing = s }
            }
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
                color = if (cur) AccentA else Color(0xFFB0B6C0),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { Engine.player.seekTo(l.timeMs) }
            )
        }
    }
}
