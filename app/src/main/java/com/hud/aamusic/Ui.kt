package com.hud.aamusic

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

/** Hiệu ứng nhấn: nút co lại rồi nảy về (spring). */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.90f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "press")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun GradBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.pressable(onClick).clip(RoundedCornerShape(24.dp)).background(AccentBrush)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun SoftBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.pressable(onClick).clip(RoundedCornerShape(24.dp)).background(Card2)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pressable(onClick).clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentA else Card2)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ Biểu tượng vẽ tay (phong cách hình học)

@Composable
fun IconPlay(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.30f, h * 0.16f); lineTo(w * 0.30f, h * 0.84f); lineTo(w * 0.86f, h * 0.50f); close()
        }
        drawPath(p, color)
    }
}

@Composable
fun IconPause(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.05f, w * 0.05f)
        drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.18f, h * 0.64f), r)
        drawRoundRect(color, Offset(w * 0.58f, h * 0.18f), Size(w * 0.18f, h * 0.64f), r)
    }
}

@Composable
fun IconSkip(next: Boolean, color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (next) {
                moveTo(w * 0.20f, h * 0.20f); lineTo(w * 0.20f, h * 0.80f); lineTo(w * 0.66f, h * 0.50f)
            } else {
                moveTo(w * 0.80f, h * 0.20f); lineTo(w * 0.80f, h * 0.80f); lineTo(w * 0.34f, h * 0.50f)
            }
            close()
        }
        drawPath(p, color)
        val barLeft = if (next) w * 0.72f else w * 0.18f
        drawRoundRect(color, Offset(barLeft, h * 0.20f), Size(w * 0.10f, h * 0.60f), CornerRadius(w * 0.03f, w * 0.03f))
    }
}

@Composable
fun IconShuffle(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val st = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val a = Path().apply {
            moveTo(w * 0.10f, h * 0.30f); lineTo(w * 0.36f, h * 0.30f); lineTo(w * 0.62f, h * 0.70f); lineTo(w * 0.78f, h * 0.70f)
        }
        val b = Path().apply {
            moveTo(w * 0.10f, h * 0.70f); lineTo(w * 0.36f, h * 0.70f); lineTo(w * 0.62f, h * 0.30f); lineTo(w * 0.78f, h * 0.30f)
        }
        drawPath(a, color, style = st)
        drawPath(b, color, style = st)
        val ha = Path().apply { moveTo(w * 0.78f, h * 0.58f); lineTo(w * 0.78f, h * 0.82f); lineTo(w * 0.94f, h * 0.70f); close() }
        val hb = Path().apply { moveTo(w * 0.78f, h * 0.18f); lineTo(w * 0.78f, h * 0.42f); lineTo(w * 0.94f, h * 0.30f); close() }
        drawPath(ha, color)
        drawPath(hb, color)
    }
}

@Composable
fun IconRepeat(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color, Offset(w * 0.12f, h * 0.28f), Size(w * 0.76f, h * 0.46f),
            CornerRadius(w * 0.18f, w * 0.18f), style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        )
        val head = Path().apply { moveTo(w * 0.58f, h * 0.08f); lineTo(w * 0.58f, h * 0.48f); lineTo(w * 0.80f, h * 0.28f); close() }
        drawPath(head, color)
    }
}

// ------------------------------------------------------------------ Nút phong cách tương lai

/** Nút tròn kính tối, viền neon; phát sáng khi bật hoặc đang nhấn, co nảy khi nhấn, rung nhẹ. */
@Composable
fun NeonButton(dp: Dp, active: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "neonScale")
    val glow by animateFloatAsState(if (active || pressed) 1f else 0f, tween(250), label = "neonGlow")
    val ringBrush: Brush = if (active) {
        Brush.sweepGradient(listOf(AccentA, AccentB, Color(0xFF7C4DFF), AccentA))
    } else {
        Brush.linearGradient(listOf(Color(0xFF3A4254), Color(0xFF2A303C)))
    }
    Box(
        Modifier.size(dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawCircle(AccentA.copy(alpha = 0.30f * glow), radius = size.minDimension / 2f + 7.dp.toPx() * glow)
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2B3345), Color(0xFF141925))))
            .border(1.6.dp, ringBrush, CircleShape)
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Nút phát/tạm dừng: vòng neon quay và nhịp sáng khi đang phát. */
@Composable
fun PlayOrb(playing: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = 450f), label = "orbScale")
    val inf = rememberInfiniteTransition(label = "orbInf")
    val angle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), label = "orbAngle")
    val pulse by inf.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "orbPulse")
    Box(
        Modifier.size(92.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (playing) drawCircle(AccentB.copy(alpha = 0.22f), radius = size.minDimension / 2f * pulse + 6.dp.toPx())
                rotate(if (playing) angle else 0f) {
                    drawCircle(
                        Brush.sweepGradient(listOf(AccentA, AccentB, Color(0xFF7C4DFF), AccentA)),
                        radius = size.minDimension / 2f - 2.dp.toPx(),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
            .padding(8.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2E364A), Color(0xFF121722))))
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (playing) IconPause(Color.White, 34.dp) else IconPlay(Color.White, 34.dp)
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
    Lists.pickFor?.let { PlaylistPicker(it) }
    Lists.confirmDelete?.let { DeleteConfirm(it) }
    if (Lists.creatingPlaylist) NewPlaylistDialog()
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
                Modifier.pressable { onTab(i) }.clip(RoundedCornerShape(24.dp))
                    .then(if (sel) Modifier.background(AccentBrush) else Modifier)
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
            Box(Modifier.pressable { Engine.player.seekToPreviousMediaItem() }.padding(10.dp)) {
                IconSkip(false, Color.White, 20.dp)
            }
            Box(Modifier.pressable { Engine.togglePlay() }.padding(10.dp)) {
                if (Engine.isPlaying) IconPause(Color.White, 24.dp) else IconPlay(Color.White, 24.dp)
            }
            Box(Modifier.pressable { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }.padding(10.dp)) {
                IconSkip(true, Color.White, 20.dp)
            }
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
            listOf("Bài hát", "Yêu thích", "Playlist", "Album", "Nghệ sĩ", "Radio").forEachIndexed { i, t ->
                Pill(t, sub == i && filter == null) { sub = i; filter = null }
            }
        }
        Spacer(Modifier.height(8.dp))
        val f = filter
        when {
            f != null && f.first == "playlist" -> {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("← ${f.second}", color = AccentA, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).clickable { filter = null })
                    SoftBtn("🗑 Xoá playlist") { Lists.deletePlaylist(f.second); filter = null }
                }
                SongList(Lists.songsOf(f.second), true, f.second)
            }
            f != null -> {
                Text("← ${f.second}", color = AccentA, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { filter = null }.padding(16.dp))
                SongList(all.filter { if (f.first == "album") Engine.album(it) == f.second else Engine.artist(it) == f.second }, true)
            }
            sub == 0 -> SongList(all.filter { match(it) }, true)
            sub == 1 -> {
                val fav = Lists.favSongs().filter { match(it) }
                if (fav.isEmpty()) EmptyHint("Chưa có bài yêu thích. Chạm ♡ ở màn hình Đang phát hoặc dùng menu ⋮ của bài.")
                else SongList(fav, true)
            }
            sub == 2 -> PlaylistList { filter = "playlist" to it }
            sub == 3 -> GroupList(all.filter { match(it) }.groupBy { Engine.album(it) }) { filter = "album" to it }
            sub == 4 -> GroupList(all.filter { match(it) }.groupBy { Engine.artist(it) }) { filter = "artist" to it }
            else -> {
                val radio = Engine.streams()
                if (radio.isEmpty()) EmptyHint("Chưa có radio. Thêm ở Cài đặt (mỗi dòng: Tên|URL).")
                else SongList(radio, false)
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun PlaylistList(onOpen: (String) -> Unit) {
    val pl = Lists.playlists
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 170.dp)) {
        item {
            Row(Modifier.padding(16.dp)) { GradBtn("＋ Tạo playlist mới") { Lists.creatingPlaylist = true } }
        }
        if (pl.isEmpty()) {
            item { Text("Chưa có playlist. Thêm bài qua menu ⋮ của từng bài.", color = Dim, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        items(pl.keys.toList()) { name ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(AccentBrush), Alignment.Center) {
                    Text("♪", fontSize = 24.sp, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${pl[name]?.size ?: 0} bài", color = Dim, fontSize = 12.sp)
                }
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
fun SongList(list: List<Song>, header: Boolean, playlist: String? = null) {
    if (list.isEmpty()) {
        EmptyHint("Không có bài hát. Hãy cấp quyền truy cập nhạc ở Cài đặt.")
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
        itemsIndexed(list, key = { _, s -> s.id }) { i, s -> SongRow(s, playlist) { Engine.play(list, i) } }
    }
}

@Composable
fun SongRow(s: Song, playlist: String? = null, onClick: () -> Unit) {
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
        if (Lists.isFav(s)) Text("♥", color = AccentB, fontSize = 16.sp, modifier = Modifier.padding(end = 2.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Dim) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (Lists.isFav(s)) "♥ Bỏ yêu thích" else "♡ Thêm vào yêu thích") },
                    onClick = { menu = false; Lists.toggleFav(s) }
                )
                if (!s.streaming) {
                    DropdownMenuItem(text = { Text("＋ Thêm vào playlist") }, onClick = { menu = false; Lists.pickFor = s })
                }
                if (playlist != null) {
                    DropdownMenuItem(text = { Text("➖ Bỏ khỏi playlist này") }, onClick = { menu = false; Lists.removeFrom(playlist, s) })
                }
                if (!s.streaming) {
                    DropdownMenuItem(text = { Text("✏ Sửa thông tin / bìa / lời") }, onClick = { menu = false; Engine.editing = s })
                    DropdownMenuItem(text = { Text("🙈 Xóa khỏi danh sách") }, onClick = { menu = false; Lists.hide(s) })
                    DropdownMenuItem(text = { Text("🗑 Xóa file khỏi máy") }, onClick = { menu = false; Lists.confirmDelete = s })
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Engine.title(s), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                val fav = Lists.isFav(s)
                Box(Modifier.pressable { Lists.toggleFav(s) }.padding(8.dp)) {
                    Text(if (fav) "♥" else "♡", fontSize = 28.sp, color = if (fav) AccentB else Dim)
                }
            }
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
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonButton(48.dp, Engine.shuffle, { Engine.toggleShuffle() }) {
                    IconShuffle(if (Engine.shuffle) AccentA else Dim, 24.dp)
                }
                NeonButton(56.dp, false, { Engine.player.seekToPreviousMediaItem() }) {
                    IconSkip(false, Color.White, 26.dp)
                }
                PlayOrb(Engine.isPlaying) { Engine.togglePlay() }
                NeonButton(56.dp, false, { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }) {
                    IconSkip(true, Color.White, 26.dp)
                }
                val rep = Engine.repeatMode != Player.REPEAT_MODE_OFF
                NeonButton(48.dp, rep, { Engine.cycleRepeat() }) {
                    IconRepeat(if (rep) AccentA else Dim, 24.dp)
                    if (Engine.repeatMode == Player.REPEAT_MODE_ONE) {
                        Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            val repText = when (Engine.repeatMode) {
                Player.REPEAT_MODE_ALL -> "tất cả"
                Player.REPEAT_MODE_ONE -> "một bài"
                else -> "tắt"
            }
            Text(
                "Ngẫu nhiên: ${if (Engine.shuffle) "BẬT" else "tắt"}   •   Lặp: $repText",
                color = if (Engine.shuffle || Engine.repeatMode != Player.REPEAT_MODE_OFF) AccentA else Dim,
                fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)
            )
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
