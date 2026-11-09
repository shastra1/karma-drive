package com.karmadrive.incar.state

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import com.karmadrive.incar.signal.CarApiSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 제한(CarUxRestrictions) 감시. 비 DO 앱인 KARMA는 제한 중 화면 갱신 · 애니메이션 · 소리를 멈춘다.
 * 화면 전환 자체는 기어(D/R) 기준으로 하고, 이 값은 보조 신호 · 점검용으로 쓴다.
 */
class DrivingStateMonitor(private val context: Context) {
    private val _requiresDO = MutableStateFlow(false)
    val requiresDO: StateFlow<Boolean> = _requiresDO

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    private var impl: Impl? = null

    fun start() {
        if (impl != null || !CarApiSource.isCarApiAvailable()) return
        try {
            impl = Impl(context) { _requiresDO.value = it }.also { _available.value = it.start() }
        } catch (t: Throwable) {
            Log.w("KarmaUx", "UX restrictions unavailable", t)
        }
    }

    fun stop() {
        impl?.stop()
        impl = null
    }

    private class Impl(private val context: Context, private val onChange: (Boolean) -> Unit) {
        private var car: Car? = null
        private var manager: CarUxRestrictionsManager? = null

        fun start(): Boolean {
            val c: Car = Car.createCar(context) ?: return false
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return false
            manager = m
            onChange(m.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false)
            m.registerListener { r -> onChange(r.isRequiresDistractionOptimization) }
            return true
        }

        fun stop() {
            try {
                manager?.unregisterListener()
                car?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
