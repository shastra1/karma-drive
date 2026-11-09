package com.karmadrive.incar.road

import android.content.Context
import com.karmadrive.incar.BuildConfig
import com.karmadrive.incar.engine.Incident
import com.karmadrive.incar.engine.RoadContext
import com.karmadrive.incar.engine.SchoolZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 도로 · 법규 데이터 모음 + 위치 → RoadContext 변환.
 * 제한속도 우선순위: 어린이보호구역 30 > 주변 무인단속카메라의 제한속도 > 기본값 50(도시부 안전속도 5030)
 */
class RoadDataHub(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val schoolZones = SchoolZoneRepo(context)
    val cameras = SpeedCameraRepo(context)
    val incidents = ItsIncidentRepo(context)

    /** 주행(RUN) 중일 때만 ITS를 호출 — RunRecorder가 갱신 */
    @Volatile var running = false
    var signals: SignalProvider = NoSignalProvider

    val dataKeySet: Boolean get() = BuildConfig.DATA_GO_KR_KEY.isNotBlank()
    val itsKeySet: Boolean get() = BuildConfig.ITS_API_KEY.isNotBlank()

    private val _current = MutableStateFlow<RoadContext?>(null)
    val current: StateFlow<RoadContext?> = _current

    fun loadAll(force: Boolean = false) {
        scope.launch { schoolZones.load(BuildConfig.DATA_GO_KR_KEY, force) }
        scope.launch { cameras.load(BuildConfig.DATA_GO_KR_KEY, force) }
    }

    fun statuses(): List<RepoStatus> = listOf(schoolZones.status, cameras.status, incidents.status) +
        RepoStatus("실시간 신호정보", false, 0, signals.statusText)

    /** 위치 1회 → 도로 맥락. 빠른 메모리 검색만 하고, ITS 갱신은 백그라운드로 */
    fun resolve(lat: Double, lon: Double, bearing: Float?): RoadContext {
        if (incidents.needsRefresh(lat, lon, running)) scope.launch { incidents.refresh(BuildConfig.ITS_API_KEY, lat, lon) }

        val zone = schoolZones.near(lat, lon, SCHOOL_RADIUS_M).firstOrNull()?.first
        val cam = cameras.near(lat, lon, CAMERA_RADIUS_M).firstOrNull { it.first.limitKmh in 20..130 }?.first
        val inc = incidents.near(lat, lon, INCIDENT_RADIUS_M)
        val sig = signals.signalAt(lat, lon, bearing)

        val ctx = when {
            zone != null -> RoadContext(30, "어린이보호구역", schoolZone = SchoolZone(zone.name))
            cam != null -> RoadContext(cam.limitKmh, "무인단속카메라 데이터", roadName = cam.name)
            else -> RoadContext(DEFAULT_LIMIT, "기본값 · 도시부 안전속도 5030")
        }.copy(
            incident = inc?.let { Incident(it.name, it.extra) },
            signal = sig,
        )
        _current.value = ctx
        return ctx
    }

    companion object {
        const val SCHOOL_RADIUS_M = 300.0
        const val CAMERA_RADIUS_M = 300.0
        const val INCIDENT_RADIUS_M = 300.0
        const val DEFAULT_LIMIT = 50
    }
}
