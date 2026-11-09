package com.karmadrive.incar.signal

import com.karmadrive.incar.engine.RoadContext

enum class Gear { P, R, N, D }
enum class Turn { OFF, LEFT, RIGHT }

/** 모든 차량 신호를 하나의 이벤트 스트림으로. t = epoch millis */
sealed class SignalEvent(val t: Long) {
    class GearChanged(t: Long, val gear: Gear) : SignalEvent(t)
    class Speed(t: Long, val kmh: Float) : SignalEvent(t)
    class TurnSignal(t: Long, val state: Turn) : SignalEvent(t)
    /** 조향각(도). 좌우 부호는 차종마다 달라 판정에서는 절댓값만 사용 */
    class Steering(t: Long, val deg: Float) : SignalEvent(t)
    class BrakePedal(t: Long, val pressed: Boolean) : SignalEvent(t)
    class SeatBelt(t: Long, val buckled: Boolean) : SignalEvent(t)
    class Wiper(t: Long, val on: Boolean) : SignalEvent(t)
    class Headlights(t: Long, val on: Boolean) : SignalEvent(t)
    class Battery(t: Long, val percent: Float) : SignalEvent(t)
    /** GPS 위치 (Android 위치 API · Pleos FusedLocation 출시 후 교체) */
    class Location(t: Long, val lat: Double, val lon: Double, val gpsSpeedKmh: Float? = null, val bearing: Float? = null) : SignalEvent(t)
    /** 위치에 대응하는 도로 · 법규 맥락 (RoadContextResolver 또는 시나리오가 생성) */
    class RoadInfo(t: Long, val ctx: RoadContext?) : SignalEvent(t)

    fun label(): String = when (this) {
        is GearChanged -> "기어 $gear"
        is Speed -> "속도 ${"%.0f".format(kmh)}km/h"
        is TurnSignal -> "방향지시등 " + when (state) { Turn.OFF -> "끔"; Turn.LEFT -> "좌"; Turn.RIGHT -> "우" }
        is Steering -> "조향 ${"%.0f".format(deg)}°"
        is BrakePedal -> if (pressed) "브레이크 밟음" else "브레이크 뗌"
        is SeatBelt -> if (buckled) "안전벨트 착용" else "안전벨트 해제"
        is Wiper -> if (on) "와이퍼 ON" else "와이퍼 OFF"
        is Headlights -> if (on) "전조등 ON" else "전조등 OFF"
        is Battery -> "배터리 ${"%.0f".format(percent)}%"
        is Location -> "위치 ${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
        is RoadInfo -> ctx?.let { c ->
            "도로 제한 ${c.limitKmh}km/h(${c.limitSource})" +
                (c.schoolZone?.let { " · 스쿨존 ${it.name}" } ?: "") +
                (c.incident?.let { " · 돌발 ${it.title}" } ?: "") +
                (c.signal?.let { " · 신호 ${it.phase} ${"%.0f".format(it.distToStopLineM)}m" } ?: "")
        } ?: "도로 정보 없음"
    }
}

/** 신호 이름별 수신 상태 (신호 점검 화면용) */
data class SignalStatus(val name: String, val available: Boolean, val note: String = "")

interface VehicleSignalSource {
    fun start(listener: (SignalEvent) -> Unit)
    fun stop()
    fun status(): List<SignalStatus> = emptyList()
}
