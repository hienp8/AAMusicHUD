package com.hud.aamusic

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.abs
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
fun GradBtn(text: String, modifier: Modifier = Modifier, icon: String? = null, onClick: () -> Unit) {
    Row(
        modifier.pressable(onClick).clip(RoundedCornerShape(24.dp)).background(AccentBrush)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Ic(icon, Color.White, 20.dp)
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SoftBtn(text: String, modifier: Modifier = Modifier, icon: String? = null, onClick: () -> Unit) {
    Row(
        modifier.pressable(onClick).clip(RoundedCornerShape(24.dp)).background(Color(0xFF141925))
            .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Ic(icon, Color.White, 20.dp)
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    val fg = if (selected) Color(0xFF1A1208) else Color.White
    Row(
        Modifier.pressable(onClick).clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentA else Color(0xFF111620))
            .border(1.dp, if (selected) AccentA else Color(0xFF2A3140), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (icon != null) Ic(icon, fg, 22.dp)
        Text(text, fontSize = 16.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
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

private val GlassBg = Color(0xB3151922)
private val GlassBorder = Color(0x22FFFFFF)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

/** Thẻ cài đặt có thể mở ra: biểu tượng cam bên trái, mũi tên bên phải (kiểu Amusic). */
@Composable
fun CSection(title: String, startOpen: Boolean = false, icon: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF0F1420))
            .border(1.dp, Color(0xFF232A38), RoundedCornerShape(26.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Ic(icon, AccentA, 28.dp)
                Spacer(Modifier.width(18.dp))
            }
            Text(
                title, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Ic(if (open) "chevD" else "chevR", Dim, 24.dp)
        }
        if (open) {
            Column(
                Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { content() }
        }
    }
}

@Composable
fun IconBack(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.62f, h * 0.18f); lineTo(w * 0.34f, h * 0.50f); lineTo(w * 0.62f, h * 0.82f)
        }
        drawPath(p, color, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconPower(color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val sw = w * 0.11f
        drawArc(color, -60f, 300f, false, Offset(w * 0.18f, w * 0.22f), Size(w * 0.64f, w * 0.64f), style = Stroke(width = sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, w * 0.08f), Offset(w * 0.5f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Thanh tiêu đề: nút quay lại vuông bo góc, tiêu đề lớn, các nút công cụ bên phải. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).pressable { onBack() }.clip(RoundedCornerShape(15.dp)).background(Color(0xFF111620))
                .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) { Ic("chevL", Color.White, 24.dp) }
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(10.dp))
        LogoMark(40.dp)
    }
}

// ------------------------------------------------------------------ Cuộn nhanh + hàng chữ cái

private const val ALPHABET = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

fun foldText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')

fun sortKey(s: String): String = foldText(s.trim()).lowercase()

fun letterOf(s: String): Char {
    val c = foldText(s.trim()).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Chữ cái đầu và chỉ số mục đầu tiên của chữ đó (danh sách phải đã sắp xếp theo ABC). */
fun alphaIndex(keys: List<String>): List<Pair<Char, Int>> {
    val out = ArrayList<Pair<Char, Int>>()
    val seen = HashSet<Char>()
    for ((i, k) in keys.withIndex()) {
        val c = letterOf(k)
        if (seen.add(c)) out += c to i
    }
    return out
}

/** Danh sách cuộn có thanh kéo nhanh và (nếu có) hàng chữ cái ở mép phải. */
@Composable
fun ScrollList(
    letters: List<Pair<Char, Int>> = emptyList(),
    bottom: Dp = 12.dp,
    spacing: Dp = 0.dp,
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val showLetters = letters.size >= 3
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = state,
            contentPadding = PaddingValues(top = 4.dp, bottom = bottom, end = if (showLetters) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
        FastScroller(state, letters)
    }
}

@Composable
private fun BoxScope.FastScroller(state: LazyListState, letters: List<Pair<Char, Int>>) {
    val scope = rememberCoroutineScope()
    var bubble by remember { mutableStateOf<Char?>(null) }
    val total = state.layoutInfo.totalItemsCount
    if (total < 20) return
    val active = state.isScrollInProgress || bubble != null
    val thumbAlpha by animateFloatAsState(if (active) 1f else 0.35f, label = "thumb")

    fun pick(y: Float, h: Int) {
        if (h <= 0) return
        val idx = ((y / h) * ALPHABET.length).toInt().coerceIn(0, ALPHABET.length - 1)
        val ch = ALPHABET[idx]
        val target = letters.firstOrNull { it.first == ch } ?: letters.firstOrNull { it.first > ch } ?: letters.last()
        bubble = ch
        scope.launch { state.scrollToItem(target.second) }
    }

    // Thanh trượt nhanh: kéo hoặc chạm để nhảy tới vị trí (ẩn khi đã có hàng chữ cái)
    if (letters.size < 3) Box(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(18.dp)
            .pointerInput(total) {
                val h = size.height
                detectVerticalDragGestures(onDragEnd = { bubble = null }, onDragCancel = { bubble = null }) { change, _ ->
                    change.consume()
                    val f = if (h > 0) (change.position.y / h).coerceIn(0f, 1f) else 0f
                    scope.launch { state.scrollToItem((f * (total - 1)).toInt()) }
                }
            }
            .pointerInput(total) {
                val h = size.height
                detectTapGestures { off ->
                    val f = if (h > 0) (off.y / h).coerceIn(0f, 1f) else 0f
                    scope.launch { state.scrollToItem((f * (total - 1)).toInt()) }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val vis = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val frac = if (total - vis <= 0) 0f else (state.firstVisibleItemIndex.toFloat() / (total - vis)).coerceIn(0f, 1f)
            val th = 56.dp.toPx()
            val tw = 4.dp.toPx()
            drawRoundRect(
                AccentA.copy(alpha = thumbAlpha), Offset(size.width - tw - 4.dp.toPx(), frac * (size.height - th)),
                Size(tw, th), CornerRadius(tw / 2f, tw / 2f)
            )
        }
    }

    // Hàng chữ cái
    if (letters.size >= 3) {
        val avail = letters.map { it.first }.toSet()
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).fillMaxHeight(0.8f).width(20.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color(0x33151922))
                .pointerInput(letters) {
                    val h = size.height
                    detectTapGestures { off -> pick(off.y, h) }
                }
                .pointerInput(letters) {
                    val h = size.height
                    detectVerticalDragGestures(onDragEnd = { bubble = null }, onDragCancel = { bubble = null }) { change, _ ->
                        change.consume()
                        pick(change.position.y, h)
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (c in ALPHABET) Text(c.toString(), fontSize = 9.5.sp, color = if (c in avail) AccentA else Dim)
        }
    }

    bubble?.let {
        Box(
            Modifier.align(Alignment.Center).size(76.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xCC000000)),
            Alignment.Center
        ) { Text(it.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AccentA) }
    }
}

// ------------------------------------------------------------------ Gốc ứng dụng

@Composable
fun Root() {
    val dens = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density * 0.9f, minOf(dens.fontScale, 1f))
    ) {
        RootBody()
    }
}

@Composable
fun RootBody() {
    // 0 = Đang phát (mặc định), 1 = Thư viện, 2 = Âm thanh, 3 = Cài đặt, 4 = Hàng đợi
    var screen by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(false) }
    BackHandler(enabled = screen != 0) { screen = 0 }
    BackHandler(enabled = hud) { hud = false }
    val cur = Engine.current
    val artVer = Engine.artVersion
    val tint by produceState(Card2, cur?.id, artVer) {
        value = if (cur == null) Card2 else withContext(Dispatchers.IO) {
            val b = Engine.loadArt(cur, 96)
            if (b == null) Card2 else {
                val dom = Palette.from(b).generate().getDominantColor(0xFF2A3140.toInt())
                Color(ColorUtils.blendARGB(dom, android.graphics.Color.BLACK, 0.62f))
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Bg))).systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        0 -> NowPlayingScreen({ screen = 1 }, { hud = true })
                        1 -> LibraryScreen { screen = 0 }
                        2 -> EqScreen { screen = 0 }
                        3 -> SettingsScreen { screen = 0 }
                        else -> QueueScreen { screen = 0 }
                    }
                }
                if (screen != 0 && Engine.current != null) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { MiniPlayer { screen = 0 } }
                }
                BottomNav(screen) { screen = it }
            }
            if (LibraryCheck.visible) ScanScreen()
            if (hud) LockLyricsScreen(onClose = { hud = false }, onOpenApp = { hud = false })
        }
    }
    Engine.editing?.let { TagEditor(it) }
    Lists.pickFor?.let { PlaylistPicker(it) }
    Lists.confirmDelete?.let { DeleteConfirm(it) }
    if (Lists.creatingPlaylist) NewPlaylistDialog()
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF131824))
            .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(24.dp)).clickable { onOpen() }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s, Modifier.size(56.dp), 128, 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(Engine.artist(s), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 14.sp)
            }
            Box(Modifier.pressable { Engine.player.seekToPreviousMediaItem() }.padding(10.dp)) {
                IconSkip(false, Color.White, 22.dp)
            }
            Box(
                Modifier.size(54.dp).pressable { Engine.togglePlay() }.clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A303C)),
                contentAlignment = Alignment.Center
            ) { if (Engine.isPlaying) IconPause(Color.White, 26.dp) else IconPlay(Color.White, 26.dp) }
            Box(Modifier.pressable { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }.padding(10.dp)) {
                IconSkip(true, Color.White, 22.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF232A38))) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(AccentBrush))
        }
    }
}

// ------------------------------------------------------------------ Thư viện

@Composable
fun LibraryScreen(onBack: () -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Pair<String, String>?>(null) }
    val all = Engine.songs
    val ev = Engine.editsVersion
    val sorted = remember(all, ev) { all.sortedBy { sortKey(Engine.title(it)) } }
    val q = query.trim().lowercase()
    fun match(s: Song) = q.isEmpty() || Engine.title(s).lowercase().contains(q) ||
        Engine.artist(s).lowercase().contains(q) || Engine.album(s).lowercase().contains(q)
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) treeToPath(uri)?.let { Lists.addFolder(it) }
    }
    BackHandler(enabled = filter != null) { filter = null }
    val tabs = listOf(
        Triple(0, "Bài hát", "music"), Triple(7, "Online", "globe"), Triple(1, "Thư mục", "folder"),
        Triple(2, "Yêu thích", "heart"), Triple(3, "Playlist", "listmusic"), Triple(4, "Album", "disc"),
        Triple(5, "Nghệ sĩ", "user"), Triple(6, "Radio", "radio")
    )

    CollapsingScaffold(header = {
        ScreenHeader("Thư viện", onBack) {
            IconBtn("scan") { LibraryCheck.visible = true }
            Spacer(Modifier.width(10.dp))
            IconBtn("refresh") { Engine.refreshLibrary() }
        }
        if (Engine.libraryMsg.isNotEmpty()) {
            Text(Engine.libraryMsg, color = AccentA, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (sub != 7 || filter != null) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Tìm bài hát, nghệ sĩ, album...", color = Dim) },
                leadingIcon = { Ic("search", Dim, 24.dp) },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentA, unfocusedBorderColor = Color(0xFF2A3140),
                    focusedContainerColor = Color(0xFF111620), unfocusedContainerColor = Color(0xFF111620)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for ((id, t, ic) in tabs) Pill(t, sub == id && filter == null, icon = ic) { sub = id; filter = null }
        }
        Spacer(Modifier.height(6.dp))
    }) {
        Column(Modifier.fillMaxSize()) {
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
                val label = if (f.first == "folder") java.io.File(f.second).name else f.second
                Text("← $label", color = AccentA, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { filter = null }.padding(16.dp))
                SongList(
                    sorted.filter {
                        when (f.first) {
                            "album" -> Engine.album(it) == f.second
                            "artist" -> Engine.artist(it) == f.second
                            else -> java.io.File(it.path).parent == f.second
                        }
                    },
                    true, null, true
                )
            }
            sub == 0 -> SongList(sorted.filter { match(it) }, true, null, true)
            sub == 1 -> FolderTab(all, { filter = "folder" to it }) { pickFolder.launch(null) }
            sub == 2 -> {
                val fav = Lists.favSongs().filter { match(it) }
                if (fav.isEmpty()) EmptyHint("Chưa có bài yêu thích. Chạm ♡ ở màn hình Đang phát hoặc ở từng bài.")
                else SongList(fav, true)
            }
            sub == 3 -> PlaylistList { filter = "playlist" to it }
            sub == 4 -> GroupList(sorted.filter { match(it) }.groupBy { Engine.album(it) }) { filter = "album" to it }
            sub == 5 -> GroupList(sorted.filter { match(it) }.groupBy { Engine.artist(it) }) { filter = "artist" to it }
            sub == 7 -> OnlineTab()
            else -> {
                val radio = Engine.streams()
                if (radio.isEmpty()) EmptyHint("Chưa có radio. Thêm ở Cài đặt (mỗi dòng: Tên|URL).")
                else SongList(radio, false)
            }
        }
        }
    }
}

/** Duyệt theo thư mục và chọn thư mục nguồn của thư viện. */
@Composable
fun FolderTab(all: List<Song>, onOpen: (String) -> Unit, pick: () -> Unit) {
    val groups = remember(all) { all.filter { it.path.isNotEmpty() }.groupBy { java.io.File(it.path).parent ?: "" } }
    ScrollList {
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradBtn("＋ Chọn thư mục nguồn") { pick() }
                if (Lists.hidden.isNotEmpty()) SoftBtn("Hiện lại ${Lists.hidden.size} bài ẩn") { Lists.unhideAll() }
            }
        }
        if (Lists.folders.isNotEmpty()) {
            item {
                Text("Chỉ hiện nhạc trong các thư mục đã chọn:", color = Dim, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(Lists.folders) { fd ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fd, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("✕", color = AccentB, modifier = Modifier.pressable { Lists.removeFolder(fd) }.padding(10.dp))
                }
            }
        } else {
            item {
                Text("Đang hiện nhạc từ toàn bộ máy. Chọn thư mục để chỉ hiện nhạc trong đó.", color = Dim,
                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item {
            Text("Thư mục trong thư viện", fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp))
        }
        items(groups.keys.sorted()) { dir ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(dir) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Card2), Alignment.Center) {
                    Text("📁", fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(java.io.File(dir).name.ifEmpty { dir }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${groups[dir]?.size ?: 0} bài • $dir", color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    ScrollList {
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
    val keys = remember(groups) { groups.keys.sortedBy { sortKey(it) } }
    ScrollList(letters = alphaIndex(keys)) {
        items(keys) { k ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(k) }.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(groups[k]?.firstOrNull(), Modifier.size(56.dp), 128, 14.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(k, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Text("${groups[k]?.size ?: 0} bài", color = Dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SongList(list: List<Song>, header: Boolean, playlist: String? = null, alpha: Boolean = false) {
    if (list.isEmpty()) {
        EmptyHint("Không có bài hát. Hãy cấp quyền truy cập nhạc ở Cài đặt.")
        return
    }
    val shift = if (header) 1 else 0
    val letters = if (alpha) alphaIndex(list.map { Engine.title(it) }).map { it.first to it.second + shift } else emptyList()
    ScrollList(letters = letters) {
        if (header) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradBtn("Phát tất cả", icon = "play") { Engine.player.shuffleModeEnabled = false; Engine.play(list, 0) }
                    SoftBtn("Ngẫu nhiên", icon = "shuffle") { Engine.playShuffled(list) }
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
        Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 14.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s, Modifier.size(52.dp), 128, 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Engine.title(s), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) AccentA else Color.White, fontWeight = FontWeight.Medium, fontSize = 17.sp
            )
            Text(
                "${Engine.artist(s)} · ${Engine.album(s)}", maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = Dim, fontSize = 14.sp
            )
        }
        val fav = Lists.isFav(s)
        Box(Modifier.pressable { Lists.toggleFav(s) }.padding(horizontal = 5.dp, vertical = 8.dp)) {
            Ic("heart", if (fav) AccentB else Dim, 22.dp, filled = fav)
        }
        Box {
            Box(Modifier.pressable { menu = true }.padding(horizontal = 5.dp, vertical = 8.dp)) { Ic("more", Dim, 22.dp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (Lists.isFav(s)) "♥ Bỏ yêu thích" else "♡ Thêm vào yêu thích") },
                    onClick = { menu = false; Lists.toggleFav(s) }
                )
                DropdownMenuItem(text = { Text("▶ Phát tiếp theo") }, onClick = { menu = false; Engine.playNext(s) })
                DropdownMenuItem(text = { Text("☰ Thêm vào hàng đợi") }, onClick = { menu = false; Engine.addToQueue(s) })
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
fun NowPlayingScreen(onLibrary: () -> Unit, onHud: () -> Unit) {
    val s = Engine.current
    var showLyrics by remember { mutableStateOf(false) }
    val hasLyrics = Engine.lyricLines.isNotEmpty() || Engine.plainLyrics.isNotEmpty()
    LaunchedEffect(Unit) {
        while (true) {
            Engine.tick()
            delay(250)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { LogoMark(38.dp) }
        if (s == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chưa phát bài nào", color = Dim)
                    Spacer(Modifier.height(14.dp))
                    GradBtn("Mở thư viện", icon = "library") { onLibrary() }
                }
            }
        } else {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsPanel(onClose = { showLyrics = false }, onHud = onHud)
                } else {
                    val side = if (maxWidth * 0.94f < maxHeight) maxWidth * 0.94f else maxHeight
                    val dx = remember { Animatable(0f) }
                    val swipeScope = rememberCoroutineScope()
                    Box(
                        Modifier.size(side)
                            .graphicsLayer {
                                translationX = dx.value
                                alpha = 1f - (abs(dx.value) / 1200f).coerceIn(0f, 0.5f)
                            }
                            .shadow(24.dp, RoundedCornerShape(30.dp))
                            .clip(RoundedCornerShape(30.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(30.dp))
                            .pointerInput(Unit) {
                                val thr = size.width * 0.22f
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val v = dx.value
                                        swipeScope.launch {
                                            if (v < -thr) {
                                                if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem()
                                            } else if (v > thr) {
                                                Engine.player.seekToPreviousMediaItem()
                                            }
                                            dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                                        }
                                    },
                                    onDragCancel = { swipeScope.launch { dx.animateTo(0f) } }
                                ) { change, amount ->
                                    change.consume()
                                    swipeScope.launch { dx.snapTo(dx.value + amount) }
                                }
                            }
                            .clickable(enabled = hasLyrics) { showLyrics = true }
                    ) {
                        Art(s, Modifier.fillMaxSize(), 720, 30.dp)
                        if (hasLyrics) {
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                                    .clip(RoundedCornerShape(18.dp)).background(Color(0xB3000000))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Ic("lines", AccentA, 18.dp)
                                Text("Nhấn để xem lời", fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            // Chuyển Ảnh bìa / Lời bài hát và nút HUD toàn màn hình
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x66000000))
                        .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(20.dp)).padding(4.dp)
                ) {
                    SegBtn("disc", "Ảnh bìa", !showLyrics) { showLyrics = false }
                    SegBtn("lines", "Lời bài hát", showLyrics) { showLyrics = true }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.pressable { onHud() }.clip(RoundedCornerShape(20.dp)).background(Color(0x66000000))
                        .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Ic("expand", Color.White, 20.dp)
                    Text("HUD", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Engine.title(s), fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${Engine.artist(s)} · ${Engine.album(s)}", color = Color(0xFFCFD3D8), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                val fav = Lists.isFav(s)
                Box(
                    Modifier.size(54.dp).pressable { Lists.toggleFav(s) }.clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111620)).border(1.dp, Color(0xFF2A3140), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) { Ic("heart", if (fav) AccentB else Color.White, 26.dp, filled = fav) }
            }
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
                Text(fmt(Engine.positionMs), color = Dim, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(fmt(Engine.durationMs), color = Dim, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonButton(50.dp, Engine.shuffle, { Engine.toggleShuffle() }) {
                    IconShuffle(if (Engine.shuffle) AccentA else Dim, 24.dp)
                }
                NeonButton(58.dp, false, { Engine.player.seekToPreviousMediaItem() }) {
                    IconSkip(false, Color.White, 26.dp)
                }
                PlayOrb(Engine.isPlaying) { Engine.togglePlay() }
                NeonButton(58.dp, false, { if (Engine.player.hasNextMediaItem()) Engine.player.seekToNextMediaItem() }) {
                    IconSkip(true, Color.White, 26.dp)
                }
                val rep = Engine.repeatMode != Player.REPEAT_MODE_OFF
                NeonButton(50.dp, rep, { Engine.cycleRepeat() }) {
                    IconRepeat(if (rep) AccentA else Dim, 24.dp)
                    if (Engine.repeatMode == Player.REPEAT_MODE_ONE) {
                        Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SegBtn(icon: String, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick).clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(Color(0xFF2A303C)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Ic(icon, if (selected) Color.White else Dim, 20.dp)
        Text(text, fontSize = 15.sp, color = if (selected) Color.White else Dim, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun HeaderChip(icon: String, text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick).clip(RoundedCornerShape(16.dp)).background(Color(0x33000000))
            .border(1.dp, if (active) AccentA else Color(0xFF2A3140), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Ic(icon, if (active) AccentA else Color.White, 17.dp)
        Text(text, fontSize = 13.sp, color = if (active) AccentA else Color.White, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

/** Khung lời bài hát thay cho ảnh bìa. "Sửa đồng bộ" = bấm đầu từng dòng để ghi thời gian; "±0.0s" = chỉnh độ lệch. */
@Composable
fun LyricsPanel(onClose: () -> Unit, @Suppress("UNUSED_PARAMETER") onHud: () -> Unit) {
    var offsetOpen by remember { mutableStateOf(false) }
    val off = Engine.lyricOffsetMs
    val editing = Engine.syncEditing
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xCC0A0D14))
            .border(1.dp, Color(0x2AFFFFFF), RoundedCornerShape(28.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Lời bài hát", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            HeaderChip("timer", if (editing) "Đang sửa" else "Sửa đồng bộ", editing) {
                if (editing) Engine.cancelSync() else { offsetOpen = false; Engine.startSync() }
            }
            if (!editing) {
                HeaderChip("clock", if (off == 0) "±0.0s" else "%+.1fs".format(off / 1000f), offsetOpen || off != 0) { offsetOpen = !offsetOpen }
            }
            Box(Modifier.pressable { onClose() }.padding(5.dp)) { Ic("x", Dim, 20.dp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (editing) {
                SyncEditor()
            } else {
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) { LyricsView() }
                if (offsetOpen) Box(Modifier.align(Alignment.TopCenter).padding(top = 2.dp)) { OffsetPanel() }
                if (Engine.syncMsg.isNotEmpty()) {
                    Text(
                        Engine.syncMsg, color = AccentA, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
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
                else "Chưa có lời.\nVào menu ⋮ của bài, chọn Sửa thông tin để tải hoặc dán lời.",
                color = if (Engine.plainLyrics.isNotEmpty()) Color.White else Dim,
                textAlign = TextAlign.Center, lineHeight = 26.sp
            )
        }
        return
    }
    val off = Engine.lyricOffsetMs
    val pos = Engine.positionMs + off
    val idx = lines.indexOfLast { it.timeMs <= pos + 250 }.coerceAtLeast(0)
    val state = rememberLazyListState()
    // Đặt dòng đang hát ở khoảng 28% chiều cao khung (không phụ thuộc số dòng phụ như phiên âm)
    LaunchedEffect(idx) {
        val vh = state.layoutInfo.viewportSize.height
        state.animateScrollToItem(idx, -(vh * 0.28f).toInt())
    }
    LazyColumn(
        Modifier.fillMaxSize(), state = state,
        contentPadding = PaddingValues(top = 8.dp, bottom = 240.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lines) { i, l ->
            val cur = i == idx
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp))
                    .then(if (cur) Modifier.background(Color(0x26FFFFFF)) else Modifier)
                    .clickable { Engine.player.seekTo((l.timeMs - off).coerceAtLeast(0L)) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    l.text.ifEmpty { "♪" }, textAlign = TextAlign.Center,
                    fontSize = if (cur) 25.sp else 19.sp,
                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    color = if (cur) AccentA else Color(0xFFB0B6C0)
                )
                // Dòng phụ: phiên âm (nghiêng) và bản dịch
                for (x in l.extras) {
                    val vn = isVietnameseText(x)
                    Text(
                        x, textAlign = TextAlign.Center,
                        fontSize = if (cur) 15.sp else 13.sp,
                        fontStyle = if (vn) FontStyle.Normal else FontStyle.Italic,
                        color = if (vn) (if (cur) Color.White else Color(0xFF8B93A1)) else (if (cur) Color(0xFFFFB074) else Color(0xFF7A808C))
                    )
                }
            }
        }
    }
}

fun fmtLrc(ms: Long): String = "%02d:%02d.%02d".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10)

/** Bảng chỉnh độ lệch thời gian lời: hiện khi bấm vào ±0.0s. */
@Composable
fun OffsetPanel() {
    val off = Engine.lyricOffsetMs
    Column(
        Modifier.padding(horizontal = 10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF2121722))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (d in listOf(-2000, -1000, -500, 500, 1000, 2000)) OffsetChip(d)
        }
        Text("+ lời hiện sớm hơn · − chậm hơn", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (off != 0) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftBtn("Đặt lại") { Engine.resetLyricOffset() }
                GradBtn("Lưu vào file", icon = "download") { Engine.bakeLyricOffset() }
            }
        }
        if (Engine.lyricSaveMsg.isNotEmpty()) {
            Text(Engine.lyricSaveMsg, color = AccentA, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Sửa đồng bộ: phát bài, bấm vào đầu mỗi dòng đúng lúc bắt đầu hát, app ghi thời gian đang phát cho dòng đó. */
@Composable
fun SyncEditor() {
    val rows = Engine.syncRows
    val cursor = Engine.syncCursor
    val state = rememberLazyListState()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(cursor) {
        if (cursor in rows.indices) state.animateScrollToItem((cursor - 1).coerceAtLeast(0))
    }
    val missing = rows.count { it.timeMs < 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftBtn(if (Engine.isPlaying) "Tạm dừng" else "Phát") { Engine.togglePlay() }
            SoftBtn("Lùi 3s") { Engine.player.seekTo((Engine.player.currentPosition - 3000L).coerceAtLeast(0L)) }
            SoftBtn("Hoàn tác") { Engine.undoSync() }
            GradBtn(if (saving) "Đang lưu..." else "Lưu", icon = "download") {
                if (!saving) { saving = true; Engine.saveSync { saving = false } }
            }
            SoftBtn("Huỷ") { Engine.cancelSync() }
        }
        Text(
            if (missing > 0) "Phát bài và bấm vào đầu mỗi dòng đúng lúc bắt đầu hát. Còn $missing dòng chưa đánh dấu."
            else "Đã đánh dấu đủ các dòng. Bấm Lưu để ghi vào file, hoặc bấm lại một dòng để đánh dấu lại.",
            fontSize = 12.sp, color = Dim, modifier = Modifier.padding(vertical = 6.dp)
        )
        if (Engine.syncMsg.isNotEmpty()) Text(Engine.syncMsg, color = AccentA, fontSize = 12.sp)
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(rows) { i, r ->
                val isCur = i == cursor
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isCur) Modifier.background(Color(0x33FF7A18)).border(1.dp, AccentA, RoundedCornerShape(14.dp))
                            else Modifier.background(Color(0x14FFFFFF))
                        )
                        .clickable { Engine.stampRow(i) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (r.timeMs >= 0) fmtLrc(r.timeMs) else "--:--.--",
                        color = if (r.timeMs >= 0) AccentA else Dim, fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.width(68.dp)
                    )
                    Column {
                        Text(r.text.ifEmpty { "♪" }, fontSize = 15.sp)
                        for (x in r.extras) Text(x, fontSize = 12.sp, color = Dim)
                    }
                }
            }
        }
    }
}

@Composable
fun OffsetChip(delta: Int) {
    val neg = delta < 0
    val sec = abs(delta) / 1000f
    val txt = (if (neg) "−" else "+") + (if (abs(delta) % 1000 == 0) "${abs(delta) / 1000}" else "0,5") + "s"
    val colors = if (neg) listOf(Color(0xFF3B4C9B), Color(0xFF28356A)) else listOf(Color(0xFFB0426A), Color(0xFF6E2A45))
    Box(
        Modifier.pressable { Engine.adjustLyricOffset(delta) }
            .clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(colors))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) { Text(txt, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

/** Dòng tiếng Việt có ít nhất một chữ cái chỉ có trong tiếng Việt (để phân biệt với phiên âm pinyin). */
fun isVietnameseText(s: String): Boolean =
    s.any { it in "ăâđêôơưĂÂĐÊÔƠƯạảãấầẩẫậắằẳẵặẹẻẽếềểễệỉĩịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ" }