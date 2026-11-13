package com.hud.aamusic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

object HudBitmap {
    /** Ảnh bìa mặc định cho thông báo / màn hình khoá / xe khi bài hát không có bìa. */
    fun placeholder(): Bitmap {
        val s = 480
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), 0xFF111A33.toInt(), 0xFFB4236B.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        p.shader = null
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 240f
        c.drawText("♪", s / 2f, s / 2f + 80f, p)
        return bmp
    }
}
