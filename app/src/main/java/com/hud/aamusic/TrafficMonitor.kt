package com.hud.aamusic

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Theo dõi GPS và tải liên tục dữ liệu giao thông từ OpenStreetMap (Overpass API):
 *  - camera tốc độ trong bán kính 3 km (tải lại khi di chuyển > 1,5 km)
 *  - giới hạn tốc độ (maxspeed) của đoạn đường hiện tại (tải lại mỗi 120 m / 30 s)
 * Muốn dùng nguồn khác (TomTom, HERE, API riêng...) thì thay hàm fetchCams/fetchLimit.
 */
class TrafficMonitor(
    ctx: Context,
    private val onUpdate: (speedKmh: Int, limit: Int?, alert: String) -> Unit
) {
    private data class Cam(val lat: Double, val lon: Double, val max: Int?)

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var cams = listOf<Cam>()
    private var camCenter: Location? = null
    private var camBusy = false
    private var limit: Int? = null
    private var limLoc: Location? = null
    private var limTime = 0L
    private var limBusy = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = handle(loc)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    fun start() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 5f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
        }
    }

    fun stop() {
        lm.removeUpdates(listener)
        io.shutdownNow()
    }

    private fun handle(loc: Location) {
        val kmh = (loc.speed * 3.6f).roundToInt()
        val now = System.currentTimeMillis()
        val cc = camCenter
        if (cc == null || loc.distanceTo(cc) > 1500f) fetchCams(loc)
        val ll = limLoc
        if (ll == null || loc.distanceTo(ll) > 120f || now - limTime > 30000L) fetchLimit(loc)

        var best = Float.MAX_VALUE
        var bestCam: Cam? = null
        for (c in cams) {
            val r = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, c.lat, c.lon, r)
            var ahead = true
            if (loc.hasBearing() && loc.speed > 2f) {
                val t = Location("cam").apply { latitude = c.lat; longitude = c.lon }
                val d = ((loc.bearingTo(t) - loc.bearing + 540f) % 360f) - 180f
                ahead = abs(d) < 60f
            }
            if (ahead && r[0] < best) {
                best = r[0]
                bestCam = c
            }
        }
        var alert = ""
        if (bestCam != null && best < 800f) {
            alert = "Camera tốc độ ${(best / 10f).roundToInt() * 10} m" +
                (bestCam.max?.let { " - tối đa $it" } ?: "")
        }
        onUpdate(kmh, limit, alert)
    }

    private fun fetchCams(loc: Location) {
        if (camBusy) return
        camBusy = true
        camCenter = Location(loc)
        val q = "[out:json][timeout:10];node(around:3000,${loc.latitude},${loc.longitude})[\"highway\"=\"speed_camera\"];out;"
        io.execute {
            val arr = overpass(q)
            main.post {
                camBusy = false
                if (arr == null) {
                    main.postDelayed({ camCenter = null }, 30000L) // thử lại sau 30 s
                } else {
                    val list = mutableListOf<Cam>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("type") != "node") continue
                        list += Cam(o.getDouble("lat"), o.getDouble("lon"),
                            parseSpeed(o.optJSONObject("tags")?.optString("maxspeed")))
                    }
                    cams = list
                }
            }
        }
    }

    private fun fetchLimit(loc: Location) {
        if (limBusy) return
        limBusy = true
        limLoc = Location(loc)
        limTime = System.currentTimeMillis()
        val q = "[out:json][timeout:8];way(around:25,${loc.latitude},${loc.longitude})[\"maxspeed\"];out tags;"
        io.execute {
            val arr = overpass(q)
            main.post {
                limBusy = false
                if (arr != null) {
                    limit = if (arr.length() > 0)
                        parseSpeed(arr.getJSONObject(0).optJSONObject("tags")?.optString("maxspeed"))
                    else null
                }
            }
        }
    }

    private fun parseSpeed(s: String?): Int? =
        s?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

    private fun overpass(q: String): JSONArray? = try {
        val c = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "AAMusicHud/1.0")
        c.outputStream.use { it.write("data=${URLEncoder.encode(q, "UTF-8")}".toByteArray()) }
        JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("elements")
    } catch (e: Exception) {
        null
    }
}
