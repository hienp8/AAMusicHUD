package com.hud.aamusic

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Theo dõi GPS và gộp 2 nguồn dữ liệu:
 *  - dữ liệu CSV do bạn nhập (chạy offline, ưu tiên cao hơn)
 *  - OpenStreetMap qua Overpass (tuỳ chọn, bật/tắt trong app)
 */
class TrafficMonitor(
    private val ctx: Context,
    private val onUpdate: (speedKmh: Int, limit: Int?, alert: String) -> Unit,
    private val onSpeak: (String) -> Unit
) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val store = PointStore(ctx)

    // Dữ liệu CSV
    private var localAlerts = listOf<Pt>()
    private var localSigns = listOf<Pt>()
    private var storeStamp = -1L
    private var storeCheck = 0L

    // Dữ liệu OSM
    private var osmCams = listOf<Pt>()
    private var camCenter: Location? = null
    private var camBusy = false
    private var osmLimit: Int? = null
    private var limLoc: Location? = null
    private var limTime = 0L
    private var limBusy = false

    // Giới hạn đọc từ biển trong CSV
    private var signLimit: Int? = null
    private var signAt = 0L

    private val spoken = HashSet<String>()
    private var lastOver = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = handle(loc)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    fun start() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
        }
    }

    fun stop() {
        lm.removeUpdates(listener)
        io.shutdownNow()
    }

    private fun online() = ctx.getSharedPreferences("aa", Context.MODE_PRIVATE).getBoolean("online", true)

    private fun refreshStore(now: Long) {
        if (now - storeCheck < 20000L) return
        storeCheck = now
        val st = store.stamp()
        if (st == storeStamp) return
        storeStamp = st
        io.execute {
            val (a, s) = try { store.load() } catch (e: Exception) { Pair(emptyList<Pt>(), emptyList<Pt>()) }
            main.post { localAlerts = a; localSigns = s }
        }
    }

    private fun near(loc: Location, p: Pt) =
        abs(p.lat - loc.latitude) < 0.01 && abs(p.lon - loc.longitude) < 0.012

    private fun ahead(loc: Location, p: Pt, maxDiff: Float): Boolean {
        if (!(loc.hasBearing() && loc.speed > 2f)) return true
        val t = Location("pt").apply { latitude = p.lat; longitude = p.lon }
        val d = ((loc.bearingTo(t) - loc.bearing + 540f) % 360f) - 180f
        return abs(d) < maxDiff
    }

    private fun handle(loc: Location) {
        val kmh = (loc.speed * 3.6f).roundToInt()
        val now = SystemClock.elapsedRealtime()
        refreshStore(now)

        if (online()) {
            val cc = camCenter
            if (cc == null || loc.distanceTo(cc) > 1500f) fetchCams(loc)
            val ll = limLoc
            if (ll == null || loc.distanceTo(ll) > 120f || now - limTime > 30000L) fetchLimit(loc)
        }

        // 1) Biển giới hạn trong CSV: đi qua trong ~35 m thì áp dụng giới hạn đó 5 phút
        val r = FloatArray(1)
        for (s in localSigns) {
            if (s.limit == null || !near(loc, s)) continue
            Location.distanceBetween(loc.latitude, loc.longitude, s.lat, s.lon, r)
            if (r[0] < 35f && ahead(loc, s, 100f)) {
                signLimit = s.limit
                signAt = now
            }
        }

        // 2) Điểm cảnh báo gần nhất phía trước (camera 800 m, cảnh báo khác 500 m)
        var best = Float.MAX_VALUE
        var bestPt: Pt? = null
        fun consider(p: Pt) {
            if (!near(loc, p)) return
            Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lon, r)
            val thr = if (p.type == Pt.WARN) 500f else 800f
            if (r[0] < thr && r[0] < best && ahead(loc, p, 60f)) {
                best = r[0]
                bestPt = p
            }
        }
        for (p in osmCams) consider(p)
        for (p in localAlerts) consider(p)

        var alert = ""
        val pt = bestPt
        if (pt != null) {
            val m = (best / 10f).roundToInt() * 10
            val isWarn = pt.type == Pt.WARN
            val label = if (isWarn) pt.name.ifEmpty { "Cảnh báo" } else "Camera tốc độ"
            alert = "$label $m m" + (pt.limit?.let { " - tối đa $it" } ?: "")
            val key = "${pt.lat},${pt.lon}"
            if (best < 300f) {
                if (spoken.add("$key:300"))
                    onSpeak(if (isWarn) "Sắp tới $label" else "Sắp tới camera tốc độ" + (pt.limit?.let { ", giới hạn $it" } ?: ""))
            } else if (spoken.add("$key:far")) {
                onSpeak(if (isWarn) "Phía trước khoảng $m mét: $label" else "Phía trước khoảng $m mét có camera tốc độ")
            }
            if (spoken.size > 300) spoken.clear()
        }

        // 3) Giới hạn tốc độ hiện hành: biển CSV (còn mới) > OSM
        val fromSign = if (now - signAt < 300000L) signLimit else null
        val eff = fromSign ?: osmLimit

        if (eff != null && kmh > eff + 3 && now - lastOver > 30000L) {
            lastOver = now
            onSpeak("Bạn đang chạy quá tốc độ, giới hạn $eff")
        }
        onUpdate(kmh, eff, alert)
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
                    main.postDelayed({ camCenter = null }, 30000L)
                } else {
                    val list = mutableListOf<Pt>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("type") != "node") continue
                        list += Pt(o.getDouble("lat"), o.getDouble("lon"), Pt.CAM,
                            parseSpeed(o.optJSONObject("tags")?.optString("maxspeed")), "")
                    }
                    osmCams = list
                }
            }
        }
    }

    private fun fetchLimit(loc: Location) {
        if (limBusy) return
        limBusy = true
        limLoc = Location(loc)
        limTime = SystemClock.elapsedRealtime()
        val q = "[out:json][timeout:8];way(around:25,${loc.latitude},${loc.longitude})[\"maxspeed\"];out tags;"
        io.execute {
            val arr = overpass(q)
            main.post {
                limBusy = false
                if (arr != null) {
                    osmLimit = if (arr.length() > 0)
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
