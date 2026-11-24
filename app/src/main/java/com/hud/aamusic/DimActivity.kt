package com.hud.aamusic

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Màn hình đen: giữ máy KHÔNG khoá nhưng tắt đèn nền, để app tốc độ trên màn hình ảo vẫn chạy và cập nhật hình.
 * Giữ lâu một chỗ trên màn hình để thoát.
 */
class DimActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        window.attributes = lp
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val auto = intent.getBooleanExtra("auto", false)
        setContent {
            // Chế độ tự động: thoát khi đã ngừng chụp (ngắt kết nối Android Auto...)
            LaunchedEffect(Unit) {
                if (auto) {
                    while (true) {
                        delay(2000)
                        if (!ScreenCapture.running) { finish(); break }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { finish() }) },
                contentAlignment = Alignment.Center
            ) { Text("Giữ lâu để thoát", color = Color(0xFF1C1C1C)) }
        }
    }
}
