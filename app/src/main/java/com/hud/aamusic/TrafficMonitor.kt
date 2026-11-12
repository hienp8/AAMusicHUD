package com.hud.aamusic

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Theo dõi GPS và gộp 2 nguồn dữ liệu:
 *  - CSV do bạn nhập (offline, ưu tiên cao hơn)
 *  - OpenStreetMap qua Overpass (tuỳ chọn). Trạng thái từng bước được ghi vào [Diag].
 */
class TrafficMonitor(
    private val ctx: Context,
    private val onUpdate: (speedKmh: Int, limit: Int?, alert: String) -> Unit,
    private val onSpeak: (String) -> Unit
) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)
    private val store = PointStore(ctx)
    private var started = false

    private var localAlerts = listOf<Pt>()
    private var localSigns = listOf<Pt>()
    private var storeStamp = -1L
    private var storeCheck = 0L

    private var osmCams = listOf<Pt>()
    private var camCenter: Location? = null
    private var camBusy = false
    private var osmLimit: Int? = null
    private var limLoc: Location? = null
    private var limTime = 0L
    private var limBusy = false

    // Goong Speed Limit API (tuỳ chọn)
    private var goongLimit: Int? = null
    private var goongLoc: Location? = null
    private var goongAt = 0L
    private var goongBusy = false

    private var signLimit: Int? = null
    private var signAt = 0L

    private val spoken = HashSet<String>()
    private var lastOver = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = handle(loc)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) { Diag.gps = "GPS: đang chờ tín hiệu..." }
        override fun onProviderDisabled(p: String) { Diag.gps = "GPS: đang TẮT trong cài đặt máy" }
    }

    fun start() {
        if (started) return
        try {
            Diag.gps = "GPS: đang chờ tín hiệu (hãy ra nơi thoáng)..."
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            started = true
        } catch (e: SecurityException) {
            Diag.gps = "GPS: chưa được cấp quyền vị trí"
        }
    }

    /** Tạm dừng GPS (tiết kiệm pin). Có thể gọi start() lại sau đó. */
    fun stop() {
        if (started) lm.removeUpdates(listener)
        started = false
        Diag.gps = "GPS: tạm tắt (chưa kết nối Android Auto, tiết kiệm pin)"
    }

    fun release() {
        stop()
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
        Diag.gps = "GPS: $kmh km/h, sai số ${if (loc.hasAccuracy()) loc.accuracy.roundToInt() else -1} m"
        refreshStore(now)

        if (online()) {
            val cc = camCenter
            if (cc == null || loc.distanceTo(cc) > 1500f) fetchCams(loc)
            val ll = limLoc
            if (ll == null || loc.distanceTo(ll) > 120f || now - limTime > 30000L) fetchLimit(loc)
        }

        val gkey = Engine.prefs.getString("goong_key", "") ?: ""
        if (gkey.isNotBlank() && !goongBusy && kmh >= 5) {
            val g0 = goongLoc
            if (g0 == null || loc.distanceTo(g0) > 150f || now - goongAt > 30000L) fetchGoong(loc, gkey)
        }

        val r = FloatArray(1)
        for (s in localSigns) {
            if (s.limit == null || !near(loc, s)) continue
            Location.distanceBetween(loc.latitude, loc.longitude, s.lat, s.lon, r)
            if (r[0] < 35f && ahead(loc, s, 100f)) {
                signLimit = s.limit
                signAt = now
            }
        }

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

        val fromSign = if (now - signAt < 300000L) signLimit else null
        val fromGoong = if (now - goongAt < 60000L) goongLimit else null
        val eff = fromSign ?: fromGoong ?: osmLimit

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
        Diag.osmCam = "OSM camera: đang tải..."
        val q = "[out:json][timeout:15];node(around:3000,${loc.latitude},${loc.longitude})[\"highway\"=\"speed_camera\"];out;"
        io.execute {
            val res = Overpass.query(q)
            main.post {
                camBusy = false
                val arr = res.elements
                if (arr == null) {
                    Diag.osmCam = "OSM camera: LỖI - ${res.log}"
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
                    Diag.osmCam = "OSM camera: OK (${res.log}), ${list.size} camera trong 3 km quanh bạn"
                }
            }
        }
    }

    private fun fetchLimit(loc: Location) {
        if (limBusy) return
        limBusy = true
        limLoc = Location(loc)
        limTime = SystemClock.elapsedRealtime()
        val q = "[out:json][timeout:10];way(around:25,${loc.latitude},${loc.longitude})[\"maxspeed\"];out tags;"
        io.execute {
            val res = Overpass.query(q)
            main.post {
                limBusy = false
                val arr = res.elements
                if (arr == null) {
                    Diag.osmLim = "OSM giới hạn đường: LỖI - ${res.log}"
                } else {
                    osmLimit = if (arr.length() > 0)
                        parseSpeed(arr.getJSONObject(0).optJSONObject("tags")?.optString("maxspeed"))
                    else null
                    Diag.osmLim = "OSM giới hạn đường: OK (${res.log}), " +
                        (osmLimit?.let { "$it km/h" } ?: "đoạn đường này chưa có dữ liệu maxspeed")
                }
            }
        }
    }

    private fun fetchGoong(loc: Location, key: String) {
        goongBusy = true
        goongLoc = Location(loc)
        goongAt = SystemClock.elapsedRealtime()
        val bearing = if (loc.hasBearing() && loc.speed > 2f) loc.bearing else null
        io.execute {
            val res = Goong.query(loc.latitude, loc.longitude, bearing, key)
            main.post {
                goongBusy = false
                goongLimit = res.first
                goongAt = SystemClock.elapsedRealtime()
                Diag.goong = res.second
            }
        }
    }

    private fun parseSpeed(s: String?): Int? =
        s?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
}
