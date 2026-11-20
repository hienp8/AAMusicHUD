package com.hud.aamusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/**
 * Chạy app (Wyn...) trên một màn hình ảo, chụp hình mỗi ~1,5 giây, cắt hình vuông
 * và dùng làm ảnh bìa trên Android Auto. Cần root (để mở app trên màn hình ảo).
 * Không đọc hay sao chép dữ liệu của app, chỉ chụp lại giao diện đang hiển thị.
 *
 * Để chạy khi khoá máy: màn hình ảo riêng tư (hệ thống không vẽ đồng hồ khoá màn hình lên đó),
 * giữ CPU thức, loại app khỏi chế độ Doze và mở lại app khi màn hình bật / tắt / mở khoá.
 */
object ScreenCapture {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var running = false
    @Volatile var manual = false
    @Volatile private var lastGrab = 0L
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var ht: HandlerThread? = null
    private var pkg = ""
    private var comp = ""
    private var displayId = -1
    private var wl: PowerManager.WakeLock? = null
    private var receiver: BroadcastReceiver? = null
    private var appCtx: Context? = null

    var onFrame: ((Bitmap) -> Unit)? = null
    var preview by mutableStateOf<Bitmap?>(null)       // khung hình thu nhỏ để chỉnh vùng cắt
    var previewArt by mutableStateOf<Bitmap?>(null)    // ảnh gửi lên xe
    var liveFrame by mutableStateOf<Bitmap?>(null)     // khung hình đầy đủ (xem trên màn hình khoá điện thoại)

    private val pkgRe = Regex("^[A-Za-z0-9_.]+$")

    fun start(ctx: Context, pkg: String, isManual: Boolean) {
        if (running) return
        if (!pkgRe.matches(pkg)) {
            Diag.cap = "Chụp màn hình: chưa chọn app hợp lệ"
            return
        }
        running = true
        manual = isManual
        this.pkg = pkg
        Diag.cap = "Chụp màn hình: đang khởi động..."
        val app = ctx.applicationContext
        appCtx = app
        thread {
            val err = try { doStart(app, pkg) } catch (e: Throwable) { "${e.javaClass.simpleName}: ${e.message}" }
            if (err != null) {
                running = false
                cleanup()
                main.post { Diag.cap = "Chụp màn hình: LỖI - $err" }
            }
        }
    }

    private fun createVd(dm: DisplayManager, w: Int, h: Int, dpi: Int, rd: ImageReader, public: Boolean): VirtualDisplay? {
        val flags = if (public) {
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        } else {
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        }
        return try {
            dm.createVirtualDisplay("AAMusicCap", w, h, dpi, rd.surface, flags, null, null)
        } catch (e: SecurityException) {
            null
        }
    }

    private fun launchApp(id: Int, flags: String): Pair<Int, String> =
        Root.run("am start --display $id -f $flags -n $comp")

    private fun doStart(ctx: Context, pkg: String): String? {
        val (rc, out) = Root.run("id")
        if (rc != 0 || !out.contains("uid=0")) {
            return "không có quyền root (hãy cấp Superuser cho AA Music trong KernelSU)"
        }
        val m = ctx.resources.displayMetrics
        // Độ phân giải đầy đủ của máy để hình nét hơn khi gửi lên xe
        val w = m.widthPixels.coerceAtLeast(360)
        val h = m.heightPixels.coerceAtLeast(640)
        val dpi = m.densityDpi.coerceAtLeast(120)

        val handlerThread = HandlerThread("aamusic-cap").also { it.start() }
        ht = handlerThread
        val rd = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = rd
        rd.setOnImageAvailableListener({ r -> grab(r, w, h) }, Handler(handlerThread.looper))

        val dm = ctx.getSystemService(DisplayManager::class.java)
        // Màn hình ảo riêng tư trước (không bị vẽ đồng hồ khoá màn hình), dự phòng là công khai
        var priv = Engine.prefs.getBoolean("cap_private", true)
        var v = createVd(dm, w, h, dpi, rd, !priv)
        if (v == null && priv) {
            priv = false
            v = createVd(dm, w, h, dpi, rd, true)
        }
        if (v == null) return "không tạo được màn hình ảo"
        vd = v

        val (_, o2) = Root.run("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg")
        comp = o2.lines().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }
            ?: return "không tìm thấy màn hình khởi chạy của $pkg"

        var res = launchApp(v.display.displayId, "0x10008000")
        var failed = res.first != 0 || res.second.contains("Error", ignoreCase = true)
        if (failed && priv) {
            // Dự phòng: màn hình ảo công khai
            try { v.release() } catch (e: Exception) { }
            priv = false
            val v2 = createVd(dm, w, h, dpi, rd, true) ?: return "không tạo được màn hình ảo công khai"
            v = v2
            vd = v2
            res = launchApp(v2.display.displayId, "0x10008000")
            failed = res.first != 0 || res.second.contains("Error", ignoreCase = true)
        }
        if (failed) return "không mở được app trên màn hình ảo: ${res.second.take(140)}"
        displayId = v.display.displayId

        // Giữ chạy khi khoá máy
        try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aamusic:capture")
            lock.setReferenceCounted(false)
            lock.acquire(12L * 3600L * 1000L)
            wl = lock
        } catch (e: Exception) { }
        Root.run("cmd deviceidle whitelist +$pkg")
        main.post { registerScreenReceiver(ctx) }

        val mode = if (priv) "riêng tư" else "công khai"
        main.post {
            Diag.cap = "Chụp màn hình: đang chạy ($pkg, màn hình ảo #$displayId, $mode)"
            main.removeCallbacks(relaunchRunnable)
            main.postDelayed(relaunchRunnable, 30000L)
        }
        return null
    }

    /** Mở lại app trên màn hình ảo (không xoá tác vụ) để phòng khi hệ thống đóng nó lúc khoá máy. */
    private val relaunchRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val id = displayId
            if (id >= 0 && comp.isNotEmpty()) thread { launchApp(id, "0x10000000") }
            main.postDelayed(this, 30000L)
        }
    }

    private fun registerScreenReceiver(ctx: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // Màn hình bật / tắt / mở khoá: mở lại app sau 1,5 giây
                main.removeCallbacks(relaunchRunnable)
                main.postDelayed(relaunchRunnable, 1500L)
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            ctx.registerReceiver(r, f)
            receiver = r
        } catch (e: Exception) { }
    }

    private fun grab(r: ImageReader, w: Int, h: Int) {
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastGrab < 1500L) return
            lastGrab = now
            val plane = img.planes[0]
            val pw = plane.rowStride / plane.pixelStride
            val raw = Bitmap.createBitmap(pw, h, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(plane.buffer)
            val full = if (pw != w) Bitmap.createBitmap(raw, 0, 0, w, h) else raw

            val prefs = Engine.prefs
            val finalArt: Bitmap
            if (prefs.getBoolean("cap_band", true)) {
                // Ảnh gửi lên xe là hình vuông lấy quanh dải ngang đã chọn (dải nằm đúng nửa giữa).
                // Khi Android Auto cắt giữa còn nửa ảnh thì dải vẫn đầy đủ; không ghép chồng hình.
                val rw = (w * prefs.getInt("cap_rw", 70) / 100f).toInt().coerceIn(32, w)
                val rx = (w * prefs.getInt("cap_rx", 26) / 100f).toInt().coerceIn(0, w - rw)
                val rh = rw / 2
                val ry = (h * prefs.getInt("cap_ry", 23) / 100f).toInt().coerceIn(0, (h - rh).coerceAtLeast(0))
                val side = rw
                val top = ry + rh / 2 - side / 2
                val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val cv = Canvas(out)
                val sy0 = top.coerceAtLeast(0)
                val sy1 = (top + side).coerceAtMost(h)
                if (sy1 > sy0) {
                    val k = 512f / side
                    cv.drawBitmap(
                        full, Rect(rx, sy0, rx + side, sy1),
                        RectF(0f, (sy0 - top) * k, 512f, (sy1 - top) * k), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    )
                }
                finalArt = out
            } else {
                val xp = prefs.getInt("cap_x", 4)
                val yp = prefs.getInt("cap_y", 8)
                val sp = prefs.getInt("cap_size", 92)
                val cw = (w * sp / 100f).toInt().coerceIn(32, w)
                val x0 = (w * xp / 100f).toInt().coerceIn(0, w - cw)
                val y0 = (h * yp / 100f).toInt().coerceIn(0, (h - cw).coerceAtLeast(0))
                val ch = cw.coerceAtMost(h - y0)
                finalArt = Bitmap.createScaledBitmap(Bitmap.createBitmap(full, x0, y0, cw, ch), 512, 512, true)
            }
            val small = Bitmap.createScaledBitmap(full, w / 2, h / 2, true)
            main.post {
                preview = small
                previewArt = finalArt
                liveFrame = Bitmap.createScaledBitmap(full, 720, (720f * h / w).toInt().coerceAtLeast(1), true)
                onFrame?.invoke(finalArt)
            }
        } catch (e: Throwable) {
        } finally {
            img.close()
        }
    }

    fun stop() {
        if (!running && vd == null) return
        running = false
        val p = pkg
        main.removeCallbacks(relaunchRunnable)
        thread {
            // Đóng app trước để nó không nhảy sang màn hình điện thoại khi màn hình ảo bị huỷ
            if (p.isNotEmpty() && pkgRe.matches(p)) {
                Root.run("am force-stop $p")
                Root.run("cmd deviceidle whitelist -$p")
            }
            cleanup()
            main.post { Diag.cap = "Chụp màn hình: đã dừng" }
        }
    }

    private fun cleanup() {
        main.post { main.removeCallbacks(relaunchRunnable) }
        try { receiver?.let { appCtx?.unregisterReceiver(it) } } catch (e: Exception) { }
        receiver = null
        try { wl?.let { if (it.isHeld) it.release() } } catch (e: Exception) { }
        wl = null
        displayId = -1
        try { vd?.release() } catch (e: Exception) { }
        vd = null
        try { reader?.close() } catch (e: Exception) { }
        reader = null
        try { ht?.quitSafely() } catch (e: Exception) { }
        ht = null
    }
}
