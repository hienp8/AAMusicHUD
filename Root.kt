package com.hud.aamusic

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Chạy lệnh với quyền root (KernelSU / Magisk). */
object Root {
    fun run(cmd: String, timeoutMs: Long = 8000L): Pair<Int, String> {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = StringBuffer()
            val t1 = thread { try { p.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } } catch (e: Exception) { } }
            val t2 = thread { try { p.errorStream.bufferedReader().forEachLine { out.append(it).append('\n') } } catch (e: Exception) { } }
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                p.destroy()
                return Pair(-1, "timeout")
            }
            t1.join(500)
            t2.join(500)
            Pair(p.exitValue(), out.toString())
        } catch (e: Exception) {
            Pair(-1, e.message ?: "lỗi")
        }
    }
}
