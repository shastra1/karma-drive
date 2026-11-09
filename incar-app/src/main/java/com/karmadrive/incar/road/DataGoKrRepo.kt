package com.karmadrive.incar.road

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 위경도가 있는 공공데이터 한 건 */
data class GeoItem(val lat: Double, val lon: Double, val name: String, val limitKmh: Int = 0, val extra: String = "")

data class RepoStatus(val name: String, val ok: Boolean, val count: Int, val message: String)

/**
 * 공공데이터포털(data.go.kr) 전국 표준데이터 Open API를 전부 받아 기기에 저장(캐시)하는 저장소.
 * 표준데이터는 반기 갱신이라 7일마다 새로 받는다.
 */
abstract class DataGoKrRepo(
    private val context: Context,
    val title: String,
    private val endpoint: String,
    private val cacheName: String,
) {
    @Volatile var items: List<GeoItem> = emptyList()
        private set
    @Volatile var status = RepoStatus(title, false, 0, "대기")
        private set

    protected abstract fun toItem(o: JSONObject): GeoItem?

    suspend fun load(serviceKey: String, force: Boolean = false) {
        withContext(Dispatchers.IO) { loadBlocking(serviceKey, force) }
    }

    private fun loadBlocking(serviceKey: String, force: Boolean) {
        val cache = File(context.filesDir, cacheName)
        if (!force && cache.exists() && System.currentTimeMillis() - cache.lastModified() < 7L * 24 * 3600 * 1000) {
            items = readCache(cache)
            if (items.isNotEmpty()) {
                status = RepoStatus(title, true, items.size, "저장된 데이터 사용")
                return
            }
        }
        if (serviceKey.isBlank()) {
            items = if (cache.exists()) readCache(cache) else emptyList()
            status = RepoStatus(title, items.isNotEmpty(), items.size, "API 키 없음 (local.properties의 DATA_GO_KR_KEY)")
            return
        }
        try {
            status = RepoStatus(title, false, 0, "내려받는 중…")
            val out = mutableListOf<GeoItem>()
            var page = 1
            var total = Int.MAX_VALUE
            var seen = 0
            while (seen < total && page <= MAX_PAGES) {
                val body = fetchPage(serviceKey, page)
                val root = PublicApi.parse(body)
                total = PublicApi.findTotalCount(root) ?: total
                val rows = PublicApi.findItems(root)
                if (rows.isEmpty()) {
                    if (page == 1) throw ApiException("응답에 데이터 없음: ${body.take(160)}")
                    break
                }
                seen += rows.size
                rows.mapNotNullTo(out) { toItem(it) }
                status = RepoStatus(title, false, out.size, "내려받는 중… ${page}페이지")
                page++
            }
            items = out
            writeCache(cache, out)
            status = RepoStatus(title, true, out.size, "공공데이터포털에서 받음")
        } catch (e: Exception) {
            Log.w("KarmaRoad", "$title load failed", e)
            items = if (cache.exists()) readCache(cache) else emptyList()
            status = RepoStatus(title, items.isNotEmpty(), items.size, "실패: ${e.message?.take(120)}")
        }
    }

    private fun fetchPage(key: String, page: Int): String {
        val q = "serviceKey=${PublicApi.encodeKey(key)}&pageNo=$page&numOfRows=$ROWS&type=json"
        return try {
            PublicApi.get("https://$endpoint?$q")
        } catch (e: Exception) {
            // 일부 표준데이터 API는 http만 응답하는 경우가 있어 한 번 더 시도
            PublicApi.get("http://$endpoint?$q")
        }
    }

    fun near(lat: Double, lon: Double, radiusM: Double): List<Pair<GeoItem, Double>> =
        items.asSequence()
            .filter { Geo.roughlyWithin(lat, lon, it.lat, it.lon, radiusM) }
            .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusM }
            .sortedBy { it.second }
            .toList()

    private fun readCache(f: File): List<GeoItem> = try {
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map {
            val a = arr.getJSONArray(it)
            GeoItem(a.getDouble(0), a.getDouble(1), a.getString(2), a.getInt(3), a.optString(4))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeCache(f: File, list: List<GeoItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray().put(it.lat).put(it.lon).put(it.name).put(it.limitKmh).put(it.extra)) }
        f.writeText(arr.toString())
    }

    companion object {
        private const val ROWS = 1000
        private const val MAX_PAGES = 60
        private val LAT = Regex("(?i)lat|위도")
        private val LON = Regex("(?i)lon|lng|경도")

        fun latLon(o: JSONObject): Pair<Double, Double>? {
            val lat = PublicApi.pick(o, listOf("latitude", "lat", "위도"), LAT)?.toDoubleOrNull()
            val lon = PublicApi.pick(o, listOf("longitude", "lon", "lng", "경도"), LON)?.toDoubleOrNull()
            if (lat == null || lon == null || lat < 32 || lat > 39.5 || lon < 124 || lon > 132.5) return null
            return lat to lon
        }
    }
}

/** 전국어린이보호구역표준데이터 — 시설 주출입문 기준 반경 300m를 보호구역으로 봄 */
class SchoolZoneRepo(context: Context) : DataGoKrRepo(
    context, "어린이보호구역", "api.data.go.kr/openapi/tn_pubr_public_child_prtc_zn_api", "school_zones.json",
) {
    override fun toItem(o: JSONObject): GeoItem? {
        val (lat, lon) = latLon(o) ?: return null
        val name = PublicApi.pick(o, listOf("trgetFcltyNm", "fcltyNm", "대상시설명"), Regex("(?i)fclty.*nm|시설명")) ?: "어린이보호구역"
        return GeoItem(lat, lon, name)
    }
}

/** 전국무인교통단속카메라표준데이터 — 카메라 주변 제한속도 */
class SpeedCameraRepo(context: Context) : DataGoKrRepo(
    context, "무인교통단속카메라", "api.data.go.kr/openapi/tn_pubr_public_unmanned_traffic_camera_api", "speed_cameras.json",
) {
    override fun toItem(o: JSONObject): GeoItem? {
        val (lat, lon) = latLon(o) ?: return null
        val limit = PublicApi.pick(o, listOf("lmttVe", "제한속도"), Regex("(?i)lmtt|limit|제한속도"))
            ?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val road = PublicApi.pick(o, listOf("roadRouteNm", "도로노선명", "itlpc", "설치장소"), Regex("(?i)road.*nm|노선명")) ?: "단속카메라"
        val kind = PublicApi.pick(o, listOf("regltSe", "단속구분"), Regex("(?i)reglt.*se|단속구분")) ?: ""
        return GeoItem(lat, lon, road, limit, kind)
    }
}
