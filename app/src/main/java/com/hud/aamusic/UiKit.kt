package com.hud.aamusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun circ(cx: Float, cy: Float, r: Float): String {
    fun f(x: Float) = "%.2f".format(Locale.US, x)
    return "M${f(cx + r)} ${f(cy)} a${f(r)} ${f(r)} 0 1 1 ${f(-2 * r)} 0 a${f(r)} ${f(r)} 0 1 1 ${f(2 * r)} 0z"
}

/** Biểu tượng nét mảnh (lưới 24x24, nét 2, đầu tròn) vẽ bằng đường dẫn, không cần thư viện biểu tượng. */
private val ICONS: Map<String, List<String>> = mapOf(
    "music" to listOf("M9 18V5l12-2v13", circ(6f, 18f, 3f), circ(18f, 16f, 3f)),
    "globe" to listOf(
        circ(12f, 12f, 10f), "M2 12h20",
        "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
    ),
    "folder" to listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z"),
    "heart" to listOf("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z"),
    "listmusic" to listOf("M21 15V6", circ(18.5f, 15.5f, 2.5f), "M12 12H3", "M16 6H3", "M12 18H3"),
    "disc" to listOf(circ(12f, 12f, 10f), circ(12f, 12f, 2f)),
    "user" to listOf("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circ(12f, 7f, 4f)),
    "radio" to listOf(
        "M4.9 19.1C1 15.2 1 8.8 4.9 4.9", "M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5", circ(12f, 12f, 2f),
        "M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5", "M19.1 4.9C23 8.8 23 15.1 19.1 19"
    ),
    "search" to listOf(circ(11f, 11f, 8f), "M21 21l-4.3-4.3"),
    "shuffle" to listOf(
        "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22", "M18 2l4 4-4 4",
        "M2 6h1.9c1.5 0 2.9.9 3.6 2.2", "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8", "M18 14l4 4-4 4"
    ),
    "scan" to listOf(
        "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2",
        circ(12f, 12f, 3f), "M16 16l-1.9-1.9"
    ),
    "refresh" to listOf(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5"
    ),
    "download" to listOf("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3"),
    "listplus" to listOf("M11 12H3", "M16 6H3", "M16 18H3", "M18 9v6", "M21 12h-6"),
    "chevR" to listOf("M9 18l6-6-6-6"),
    "chevL" to listOf("M15 18l-6-6 6-6"),
    "chevD" to listOf("M6 9l6 6 6-6"),
    "library" to listOf("M16 6l4 14", "M12 6v14", "M8 8v12", "M4 4v16"),
    "sliders" to listOf("M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"),
    "settings" to listOf(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circ(12f, 12f, 3f)
    ),
    "car" to listOf(
        "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
        circ(7f, 17f, 2f), "M9 17h6", circ(17f, 17f, 2f)
    ),
    "phone" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M11 18h2"),
    "slidersh" to listOf("M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4"),
    "info" to listOf(circ(12f, 12f, 10f), "M12 16v-4", "M12 8h.01"),
    "power" to listOf("M12 2v10", "M18.4 6.6a9 9 0 1 1-12.77.04"),
    "x" to listOf("M18 6L6 18", "M6 6l12 12"),
    "expand" to listOf("M15 3h6v6", "M21 3l-7 7", "M3 21l7-7", "M9 21H3v-6"),
    "timer" to listOf("M10 2h4", "M12 14l3-3", circ(12f, 14f, 8f)),
    "clock" to listOf(circ(12f, 12f, 10f), "M12 6v6l4 2"),
    "lines" to listOf("M21 5H3", "M15 12H3", "M17 19H3"),
    "history" to listOf("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2"),
    "flame" to listOf("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z"),
    "play" to listOf("M7 4l13 8-13 8z"),
    "plus" to listOf("M12 5v14", "M5 12h14")
)

private val PARSED = HashMap<String, List<Path>>()

private fun pathsOf(name: String): List<Path> = PARSED.getOrPut(name) {
    (ICONS[name] ?: emptyList()).map { PathParser().parsePathString(it).toPath() }
}

/** Vẽ biểu tượng theo tên. filled = tô đặc (vd. trái tim đã thích, nút phát). */
@Composable
fun Ic(name: String, color: Color, size: Dp = 22.dp, filled: Boolean = false, stroke: Float = 2f) {
    val paths = remember(name) { pathsOf(name) }
    Canvas(Modifier.size(size)) {
        val sc = this.size.minDimension / 24f
        if (name == "more") {
            for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.8f * sc, Offset(12f * sc, y * sc))
        } else {
            val fill = filled || name == "play"
            scale(sc, sc, pivot = Offset.Zero) {
                for (p in paths) {
                    if (fill) drawPath(p, color)
                    drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** Nút vuông bo góc, viền mảnh, biểu tượng màu cam (kiểu thanh công cụ của Amusic). */
@Composable
fun IconBtn(icon: String, size: Dp = 46.dp, tint: Color = AccentA, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).pressable(onClick).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111620))
            .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) { Ic(icon, tint, size * 0.46f, filled) }
}

/** Logo nhỏ: hai chữ A, nốt nhạc nằm ở thanh ngang chữ A thứ hai. */
@Composable
fun LogoMark(size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF161B26))
            .border(1.dp, Color(0xFF2A3140), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.78f)) {
            val s = this.size.minDimension / 62f
            fun p(x: Float, y: Float) = Offset((x - 20f) * s, (y - 28f) * s)
            val st = Stroke(width = 8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val a1 = Path().apply { moveTo(p(24f, 80f).x, p(24f, 80f).y); lineTo(p(39f, 36f).x, p(39f, 36f).y); lineTo(p(54f, 80f).x, p(54f, 80f).y) }
            drawPath(a1, Color.White, style = st)
            drawLine(Color.White, p(31f, 64f), p(47f, 64f), strokeWidth = 8f * s, cap = StrokeCap.Round)
            val a2 = Path().apply { moveTo(p(45f, 80f).x, p(45f, 80f).y); lineTo(p(60f, 36f).x, p(60f, 36f).y); lineTo(p(75f, 80f).x, p(75f, 80f).y) }
            drawPath(a2, Color(0xFFFF8A00), style = st)
            val c = p(56f, 64f)
            rotate(-20f, c) {
                drawOval(Color.White, Offset(c.x - 5.5f * s, c.y - 4.5f * s), Size(11f * s, 9f * s))
            }
            drawLine(Color.White, p(61f, 63.5f), p(61f, 48f), strokeWidth = 3.2f * s, cap = StrokeCap.Round)
            val flag = Path().apply {
                moveTo(p(61f, 48f).x, p(61f, 48f).y)
                cubicTo(p(65f, 49f).x, p(65f, 49f).y, p(66f, 53f).x, p(66f, 53f).y, p(65f, 57f).x, p(65f, 57f).y)
            }
            drawPath(flag, Color.White, style = Stroke(width = 3.2f * s, cap = StrokeCap.Round))
        }
    }
}

/** Thanh trên cùng của ứng dụng. */
@Composable
fun AppBar() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(44.dp)
            Spacer(Modifier.width(14.dp))
            Text("AA Music", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1C2230)))
    }
}

/** Thanh điều hướng dưới: Phát, Thư viện, EQ, Hàng đợi, Cài đặt. */
@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(0, "Phát", "disc"), Triple(1, "Thư viện", "library"), Triple(2, "EQ", "sliders"),
        Triple(4, "Hàng đợi", "listmusic"), Triple(3, "Cài đặt", "settings")
    )
    Column(Modifier.fillMaxWidth().background(Color(0xFF0D1018))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1C2230)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for ((id, label, icon) in items) {
                val sel = selected == id
                val col = if (sel) AccentA else Dim
                Column(
                    Modifier.weight(1f).pressable { onSelect(id) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.width(50.dp).height(3.dp).clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .then(if (sel) Modifier.background(AccentBrush) else Modifier)
                    )
                    Spacer(Modifier.height(9.dp))
                    Ic(icon, col, 27.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 13.sp, color = col, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}


/**
 * Khung có phần đầu tự ẩn khi kéo danh sách xuống/lên: phần đầu thu lại khi cuộn, hiện lại khi cuộn ngược,
 * để danh sách được mở rộng. Dùng cho mọi màn hình có danh sách hoặc nội dung cuộn.
 */
@Composable
fun CollapsingScaffold(header: @Composable ColumnScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
    var headerH by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) offset = (offset + dy).coerceIn(-headerH, 0f)
                return Offset.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(conn).clipToBounds()) {
        val off = offset.coerceIn(-headerH, 0f)
        Box(
            Modifier.fillMaxSize().padding(top = with(density) { (headerH + off).coerceAtLeast(0f).toDp() }),
            content = content
        )
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerH = it.height.toFloat() }
                .graphicsLayer { translationY = offset.coerceIn(-headerH, 0f) },
            content = header
        )
    }
}
