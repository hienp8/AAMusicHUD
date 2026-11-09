package com.hud.aamusic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aa", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val info = TextView(this).apply {
            text = "1) Cấp quyền  2) Nhập radio online (mỗi dòng: Tên|URL)  3) Bắt đầu dịch vụ, rồi kết nối Android Auto."
        }
        val et = EditText(this).apply {
            hint = "VD: Radio ABC|https://example.com/stream.mp3"
            setText(prefs.getString("streams", ""))
            minLines = 5
            gravity = Gravity.TOP
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
        }
        root.addView(info)
        root.addView(btn("Cấp quyền") {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                perms += Manifest.permission.READ_MEDIA_AUDIO
                perms += Manifest.permission.POST_NOTIFICATIONS
            } else {
                perms += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            requestPermissions(perms.toTypedArray(), 1)
        })
        root.addView(et)
        root.addView(btn("Lưu danh sách radio") {
            prefs.edit().putString("streams", et.text.toString()).apply()
            Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("Bắt đầu dịch vụ") {
            startForegroundService(Intent(this, MusicService::class.java))
        })
        root.addView(btn("Dừng dịch vụ") {
            stopService(Intent(this, MusicService::class.java))
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
