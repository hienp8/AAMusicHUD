package com.hud.aamusic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val store by lazy { PointStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aa", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
        }

        val tts = CheckBox(this).apply {
            text = "Đọc cảnh báo bằng giọng nói (TTS)"
            isChecked = prefs.getBoolean("tts", true)
        }
        val online = CheckBox(this).apply {
            text = "Tải thêm dữ liệu OpenStreetMap trực tuyến"
            isChecked = prefs.getBoolean("online", true)
        }
        val streams = EditText(this).apply {
            hint = "Radio online, mỗi dòng: Tên|URL"
            setText(prefs.getString("streams", ""))
            minLines = 4
            gravity = Gravity.TOP
        }
        status = TextView(this)

        root.addView(TextView(this).apply {
            text = "Thứ tự: Cấp quyền > Nhập CSV (nếu có) > Lưu cấu hình > Bắt đầu dịch vụ > kết nối Android Auto."
        })
        root.addView(btn("1. Cấp quyền") {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                perms += Manifest.permission.READ_MEDIA_AUDIO
                perms += Manifest.permission.POST_NOTIFICATIONS
            } else {
                perms += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            requestPermissions(perms.toTypedArray(), 1)
        })
        root.addView(btn("Nhập CSV camera / cảnh báo") { pick(REQ_ALERTS) })
        root.addView(btn("Nhập CSV biển giới hạn tốc độ") { pick(REQ_SIGNS) })
        root.addView(btn("Xoá dữ liệu CSV đã nhập") {
            store.clear()
            refresh()
        })
        root.addView(tts)
        root.addView(online)
        root.addView(streams)
        root.addView(btn("2. Lưu cấu hình") {
            prefs.edit()
                .putBoolean("tts", tts.isChecked)
                .putBoolean("online", online.isChecked)
                .putString("streams", streams.text.toString())
                .apply()
            Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("3. Bắt đầu dịch vụ") {
            startForegroundService(Intent(this, MusicService::class.java))
        })
        root.addView(btn("Dừng dịch vụ") {
            stopService(Intent(this, MusicService::class.java))
        })
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun pick(code: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, code)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || (requestCode != REQ_ALERTS && requestCode != REQ_SIGNS)) return
        val uri = data?.data ?: return
        try {
            val dest = store.fileFor(if (requestCode == REQ_SIGNS) "signs" else "alerts")
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            Toast.makeText(this, "Đã nhập file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Không đọc được file: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun refresh() {
        val (a, s) = try { store.load() } catch (e: Exception) { Pair(emptyList<Pt>(), emptyList<Pt>()) }
        status.text = "Dữ liệu đã nhập: ${a.size} điểm cảnh báo/camera, ${s.size} biển giới hạn tốc độ.\n" +
            "(Dịch vụ tự nạp lại dữ liệu mới trong vòng 20 giây.)"
    }

    companion object {
        private const val REQ_ALERTS = 11
        private const val REQ_SIGNS = 12
    }
}
