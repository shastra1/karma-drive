package com.karmadrive.incar.road

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 국가교통정보센터(ITS) Open API — 돌발상황정보(사고 · 공사 · 통제).
 * 현재 위치 주변 약 ±5km 사각형을, 주행 중에만 15분마다(또는 5km 이동 시) 새로 받는다.
 * 월 호출 한도(개발 100 / 운영 1,000)를 넘지 않도록 이번 달 호출 수를 세고 MONTHLY_BUDGET에서 멈춘다.
 * 엔드포인트 · 파라미터는 its.go.kr 오픈API 명세 기준(키 발급 후 명세서로 최종 확인).
 */
class ItsIncidentRepo(context: Context) {
    private val prefs = context.getSharedPreferences("its_quota", Context.MODE_PRIVATE)

    @Volatile var items: List<GeoItem> = emptyList()
        private set
    @Volatile var status = RepoStatus("돌발상황정보(ITS)", false, 0, "대기")
        private set
    private var lastFetch = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    @Volatile private var fetching = false

    /** 이번 달 호출 수 */
    fun usedThisMonth(): Int = if (prefs.getString(K_MONTH, "") == month()) prefs.getInt(K_COUNT, 0) else 0

    private fun countCall() {
        val m = month()
        val n = if (prefs.getString(K_MONTH, "") == m) prefs.getInt(K_COUNT, 0) + 1 else 1
        prefs.edit().putString(K_MONTH, m).putInt(K_COUNT, n).apply()
    }

    fun needsRefresh(lat: Double, lon: Double, running: Boolean): Boolean =
        running && !fetching && usedThisMonth() < MONTHLY_BUDGET &&
            (lastFetch == 0L || System.currentTimeMillis() - lastFetch > REFRESH_MS || Geo.distanceM(lat, lon, lastLat, lastLon) > 5_000)

    suspend fun refresh(apiKey: String, lat: Double, lon: Double) = withContext(Dispatchers.IO) { refreshBlocking(apiKey, lat, lon) }

    private fun refreshBlocking(apiKey: String, lat: Double, lon: Double) {
        if (apiKey.isBlank()) {
            status = RepoStatus("돌발상황정보(ITS)", false, 0, "API 키 없음 (local.properties의 ITS_API_KEY)")
            lastFetch = System.currentTimeMillis()
            lastLat = lat; lastLon = lon
            return
        }
        fetching = true
        countCall()
        try {
            val d = 0.05
            val url = "$BASE/eventInfo?apiKey=$apiKey&type=all&eventType=all" +
                "&minX=${lon - d}&maxX=${lon + d}&minY=${lat - d}&maxY=${lat + d}&getType=json"
            val root = PublicApi.parse(PublicApi.get(url))
            val rows = PublicApi.findItems(root)
            items = rows.mapNotNull { o ->
                val x = PublicApi.pick(o, listOf("coordX", "x", "lon"))?.toDoubleOrNull()
                val y = PublicApi.pick(o, listOf("coordY", "y", "lat"))?.toDoubleOrNull()
                if (x == null || y == null) return@mapNotNull null
                val type = PublicApi.pick(o, listOf("eventType", "eventTypeNm")) ?: "돌발"
                val detail = PublicApi.pick(o, listOf("eventDetailType", "message", "lanesBlocked")) ?: ""
                val road = PublicApi.pick(o, listOf("roadName", "roadNm")) ?: ""
                GeoItem(y, x, "$type${if (road.isNotEmpty()) " · $road" else ""}", 0, detail)
            }
            status = RepoStatus("돌발상황정보(ITS)", true, items.size, "주변 ${items.size}건 · 이번 달 호출 ${usedThisMonth()}/$MONTHLY_BUDGET")
        } catch (e: Exception) {
            Log.w("KarmaRoad", "ITS failed", e)
            status = RepoStatus("돌발상황정보(ITS)", false, items.size, "실패: ${e.message?.take(120)}")
        } finally {
            fetching = false
            lastFetch = System.currentTimeMillis()
            lastLat = lat; lastLon = lon
        }
    }

    fun near(lat: Double, lon: Double, radiusM: Double): GeoItem? =
        items.filter { Geo.roughlyWithin(lat, lon, it.lat, it.lon, radiusM) }
            .minByOrNull { Geo.distanceM(lat, lon, it.lat, it.lon) }
            ?.takeIf { Geo.distanceM(lat, lon, it.lat, it.lon) <= radiusM }

    companion object {
        const val BASE = "https://openapi.its.go.kr:9443"
        private const val REFRESH_MS = 15 * 60_000L
        /** 운영계정 월 1,000건 기준 90%. 개발계정(100건)이면 90으로 낮출 것 */
        const val MONTHLY_BUDGET = 900
        private const val K_MONTH = "month"
        private const val K_COUNT = "count"
        private fun month(): String = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date())
    }
}
