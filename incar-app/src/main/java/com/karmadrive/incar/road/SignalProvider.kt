package com.karmadrive.incar.road

import com.karmadrive.incar.engine.SignalInfo

/**
 * 교차로 실시간 신호(현시 · 잔여시간 · 정지선 위치) 제공자.
 * 경찰청 · 지자체의 실시간 신호정보 개방은 현재 지역 단위(예: 고양시 중앙로)이며 내비게이션 사업자 연계 방식이라,
 * 공개 Open API가 확인되면 이 인터페이스로 연결한다. 그 전까지 실제 주행에서는 신호 판정을 끈다.
 */
interface SignalProvider {
    val statusText: String
    fun signalAt(lat: Double, lon: Double, bearing: Float?): SignalInfo?
}

object NoSignalProvider : SignalProvider {
    override val statusText = "실시간 신호정보 공개 API 미확인 — 실제 주행은 신호 판정 제외 (데모 시나리오에서만 시연)"
    override fun signalAt(lat: Double, lon: Double, bearing: Float?): SignalInfo? = null
}
