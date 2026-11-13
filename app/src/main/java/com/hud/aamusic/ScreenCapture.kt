package com.hud.aamusic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/**
 * Chạy app (Wyn, DiOTO...) trên một màn hình ảo, chụp hình mỗi ~1,5 giây, cắt hình vuông
 * và dùng làm ảnh bìa trên Android Auto. Cần root (để mở app trên màn hình ảo).
 * Không đọc hay sao chép dữ liệu của app, chỉ chụp lại giao diện đang hiển thị.
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

    var onFrame: ((Bitmap) -> Unit)? = null
    var preview by mutableStateOf<Bitmap?>(null)
    var previewArt by mutableStateOf<Bitmap?>(null)

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
        thread {
            val err = try { doStart(app, pkg) } catch (e: Throwable) { "${e.javaClass.simpleName}: ${e.message}" }
            if (err != null) {
                running = false
                cleanup()
                main.post { Diag.cap = "Chụp màn hình: LỖI - $err" }
            }
        }
    }

    private fun doStart(ctx: Context, pkg: String): String? {
        val (rc, out) = Root.run("id")
        if (rc != 0 || !out.contains("uid=0")) {
            return "không có quyền root (hãy cấp Superuser cho AA Music trong KernelSU)"
        }
        val m = ctx.resources.displayMetrics
        val w = (m.widthPixels / 2).coerceAtLeast(360)
        val h = (m.heightPixels / 2).coerceAtLeast(640)
        val dpi = (m.densityDpi / 2).coerceAtLeast(120)

        val handlerThread = HandlerThread("aamusic-cap").also { it.start() }
        ht = handlerThread
        val rd = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = rd
        rd.setOnImageAvailableListener({ r -> grab(r, w, h) }, Handler(handlerThread.looper))

        val dm = ctx.getSystemService(DisplayManager::class.java)
        val flagsPublic = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        vd = try {
            dm.createVirtualDisplay("AAMusicCap", w, h, dpi, rd.surface, flagsPublic, null, null)
        } catch (e: SecurityException) {
            dm.createVirtualDisplay("AAMusicCap", w, h, dpi, rd.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, null, null)
        }
        val display = vd?.display ?: return "không tạo được màn hình ảo"
        val id = display.displayId

        val (_, o2) = Root.run("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg")
        val comp = o2.lines().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }
            ?: return "không tìm thấy màn hình khởi chạy của $pkg"
        val (r3, o3) = Root.run("am start --display $id -f 0x10008000 -n $comp")
        if (r3 != 0 || o3.contains("Error", ignoreCase = true)) {
            return "không mở được app trên màn hình ảo: ${o3.take(140)}"
        }
        main.post { Diag.cap = "Chụp màn hình: đang chạy ($pkg trên màn hình ảo #$id)" }
        return null
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
            val xp = prefs.getInt("cap_x", 4)
            val yp = prefs.getInt("cap_y", 8)
            val sp = prefs.getInt("cap_size", 92)
            val cw = (w * sp / 100f).toInt().coerceIn(32, w)
            val x0 = (w * xp / 100f).toInt().coerceIn(0, w - cw)
            val y0 = (h * yp / 100f).toInt().coerceIn(0, (h - cw).coerceAtLeast(0))
            val ch = cw.coerceAtMost(h - y0)
            val crop = Bitmap.createBitmap(full, x0, y0, cw, ch)
            var art = Bitmap.createScaledBitmap(crop, 512, 512, true)
            // Khi Android Auto thu nhỏ ảnh bìa còn một nửa (thường cắt phần giữa), dải ngang 2:1 này nằm ở giữa ảnh
            if (prefs.getBoolean("cap_band", true)) {
                val rw = (w * prefs.getInt("cap_rw", 92) / 100f).toInt().coerceIn(32, w)
                val rh = (rw / 2).coerceIn(16, h)
                val rx = (w * prefs.getInt("cap_rx", 4) / 100f).toInt().coerceIn(0, w - rw)
                val ry = (h * prefs.getInt("cap_ry", 14) / 100f).toInt().coerceIn(0, (h - rh).coerceAtLeast(0))
                val band = Bitmap.createScaledBitmap(Bitmap.createBitmap(full, rx, ry, rw, rh), 512, 256, true)
                val out = art.copy(Bitmap.Config.ARGB_8888, true)
                val top = (512 * prefs.getInt("cap_bc", 50) / 100f - 128f).coerceIn(0f, 256f)
                Canvas(out).drawBitmap(band, 0f, top, null)
                art = out
            }
            val finalArt = art
            val small = Bitmap.createScaledBitmap(full, w / 2, h / 2, true)
            main.post {
                preview = small
                previewArt = finalArt
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
        thread {
            // Đóng app trước để nó không nhảy sang màn hình điện thoại khi màn hình ảo bị huỷ
            if (p.isNotEmpty() && pkgRe.matches(p)) Root.run("am force-stop $p")
            cleanup()
            main.post { Diag.cap = "Chụp màn hình: đã dừng" }
        }
    }

    private fun cleanup() {
        try { vd?.release() } catch (e: Exception) { }
        vd = null
        try { reader?.close() } catch (e: Exception) { }
        reader = null
        try { ht?.quitSafely() } catch (e: Exception) { }
        ht = null
    }
}
