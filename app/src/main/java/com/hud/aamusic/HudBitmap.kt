package com.hud.aamusic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object HudBitmap {
    fun render(speed: Int, limit: Int?, alert: String): Bitmap {
        val s = 480
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (alert.isNotEmpty()) 0xFF4A0000.toInt() else 0xFF101418.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textAlign = Paint.Align.CENTER

        // Biển giới hạn tốc độ
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(240f, 170f, 125f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 24f
        p.color = if (limit != null) 0xFFE53935.toInt() else Color.GRAY
        c.drawCircle(240f, 170f, 113f, p)
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 110f
        c.drawText(limit?.toString() ?: "--", 240f, 208f, p)

        // Tốc độ hiện tại
        p.color = if (limit != null && speed > limit) 0xFFFF5252.toInt() else Color.WHITE
        p.textSize = 120f
        c.drawText("$speed", 240f, 380f, p)
        p.textSize = 36f
        p.color = Color.LTGRAY
        c.drawText("km/h", 240f, 430f, p)
        return bmp
    }
}
