package com.karmadrive.incar.engine

enum class SignalPhase { RED, YELLOW, GREEN }

/** 교차로 신호 상태 — 실시간 신호정보 제공자(또는 데모)가 채움 */
data class SignalInfo(
    val intersectionName: String,
    val phase: SignalPhase,
    /** 정지선까지 남은 거리(m). 정지선을 지나면 음수 */
    val distToStopLineM: Float,
)

data class SchoolZone(val name: String, val limitKmh: Int = 30)

data class Incident(val title: String, val detail: String = "")

/**
 * 현재 위치의 도로 · 법규 맥락. 순수 데이터 — 엔진은 이것만 보고 판정한다.
 * limitSource: 제한속도의 출처(예: "무인단속카메라 데이터", "어린이보호구역", "기본값 · 안전속도 5030")
 */
data class RoadContext(
    val limitKmh: Int,
    val limitSource: String,
    val schoolZone: SchoolZone? = null,
    val incident: Incident? = null,
    val signal: SignalInfo? = null,
    val roadName: String = "",
    /** 바로 앞 횡단보도 (우회전 일시정지 판정용) */
    val crosswalk: String? = null,
)
