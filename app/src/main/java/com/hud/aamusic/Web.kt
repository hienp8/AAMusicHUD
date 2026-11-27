package com.hud.aamusic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Giữ các trang web nhạc đã mở để chuyển tab không làm dừng nhạc đang phát trong trang. */
object Web {
    private val views = HashMap<String, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun get(ctx: Context, url: String): WebView = views.getOrPut(url) {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }
}

/** Thẻ Online: Jamendo (phát bằng trình phát của app) và trang chính thức của YouTube Music, Zing MP3, NhacCuaTui. */
@Composable
fun OnlineTab() {
    var src by remember { mutableStateOf(0) }
    val sites = listOf(
        Triple("YouTube Music", "https://music.youtube.com", "com.google.android.apps.youtube.music"),
        Triple("Zing MP3", "https://zingmp3.vn", "com.zing.mp3"),
        Triple("NhacCuaTui", "https://www.nhaccuatui.com", "ht.nct")
    )
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Pill("Jamendo", src == 0, icon = "music") { src = 0 }
            for ((i, s) in sites.withIndex()) Pill(s.first, src == i + 1, icon = "globe") { src = i + 1 }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (src == 0) {
                JamendoTab()
            } else {
                val s = sites[src - 1]
                WebSite(s.first, s.second, s.third)
            }
        }
    }
}

/** Trang web chính thức chạy trong app. Không lấy file nhạc ra khỏi dịch vụ: nhạc phát bằng trình phát của chính trang đó. */
@Composable
fun WebSite(name: String, url: String, pkg: String) {
    val ctx = LocalContext.current
    val view = remember(url) { Web.get(ctx.applicationContext, url) }
    var canBack by remember { mutableStateOf(false) }
    LaunchedEffect(view) {
        while (true) {
            canBack = view.canGoBack()
            delay(500)
        }
    }
    BackHandler(enabled = canBack) { view.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SoftBtn("Mở bằng app $name", icon = "globe") {
                val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_VIEW, Uri.parse(url))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(i) } catch (e: Exception) { }
            }
            SoftBtn("Tải lại", icon = "refresh") { view.reload() }
        }
        Text(
            "Trang chính thức của $name chạy ngay trong app, nhạc phát bằng trình phát của họ (không qua trình phát của AA Music, " +
                "không có lời và ảnh bìa trên xe) và có thể dừng khi tắt màn hình.",
            fontSize = 11.sp, color = Dim, modifier = Modifier.padding(bottom = 6.dp)
        )
        AndroidView(
            factory = {
                (view.parent as? ViewGroup)?.removeView(view)
                view
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
