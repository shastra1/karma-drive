package com.karmadrive.incar.signal

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * AAOS 표준 Car API(VHAL)로 차량 신호를 받는 소스.
 * Pleos Connect Emulator의 Virtual Console 조작은 VHAL 값으로 들어옵니다.
 * 속성 ID는 이름으로 찾아서(리플렉션) SDK 버전 차이로 빌드가 깨지지 않게 했습니다.
 *
 * v0.2에서 PleosVehicleSource(ai.pleos.playground:Vehicle)로 교체 · 병행 예정.
 */
class CarApiSource(private val context: Context) : VehicleSignalSource {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var listener: ((SignalEvent) -> Unit)? = null
    private val statuses = linkedMapOf<String, SignalStatus>()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            try {
                handle(value)
            } catch (e: Exception) {
                Log.w(TAG, "handle failed: ${value.propertyId}", e)
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error $propId zone $zone")
        }
    }

    private val ids = mutableMapOf<Int, String>()

    override fun start(listener: (SignalEvent) -> Unit) {
        this.listener = listener
        val c: Car? = try {
            Car.createCar(context)
        } catch (e: Throwable) {
            Log.w(TAG, "Car 연결 실패", e)
            null
        }
        if (c == null) {
            statuses["Car 서비스"] = SignalStatus("Car 서비스", false, "연결 실패")
            return
        }
        car = c
        val m = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        manager = m
        if (m == null) {
            statuses["Car 서비스"] = SignalStatus("Car 서비스", false, "PropertyManager 없음")
            return
        }
        statuses["Car 서비스"] = SignalStatus("Car 서비스", true)
        subscribe(m, "GEAR_SELECTION", "기어", CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(m, "PERF_VEHICLE_SPEED", "차속", 5f)
        subscribe(m, "TURN_SIGNAL_STATE", "방향지시등", CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(m, "PERF_STEERING_ANGLE", "조향각", 5f)
        subscribe(m, "SEAT_BELT_BUCKLED", "안전벨트", CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(m, "WINDSHIELD_WIPERS_STATE", "와이퍼", CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(m, "HEADLIGHTS_STATE", "전조등", CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(m, "EV_BATTERY_LEVEL", "배터리", 1f)
    }

    private fun subscribe(m: CarPropertyManager, propName: String, label: String, rate: Float) {
        val id = try {
            VehiclePropertyIds::class.java.getField(propName).getInt(null)
        } catch (e: Exception) {
            statuses[label] = SignalStatus(label, false, "이 SDK에 없음")
            return
        }
        ids[id] = propName
        val ok = try {
            val config = m.getCarPropertyConfig(id)
            if (config == null) {
                statuses[label] = SignalStatus(label, false, "차량이 지원하지 않음")
                return
            }
            m.registerCallback(callback, id, rate)
        } catch (e: SecurityException) {
            statuses[label] = SignalStatus(label, false, "권한 없음")
            return
        } catch (e: Exception) {
            statuses[label] = SignalStatus(label, false, e.javaClass.simpleName)
            return
        }
        statuses[label] = SignalStatus(label, ok, if (ok) "" else "등록 실패")
    }

    private fun handle(v: CarPropertyValue<*>) {
        val l = listener ?: return
        val now = System.currentTimeMillis()
        val raw = v.value ?: return
        when (ids[v.propertyId]) {
            "GEAR_SELECTION" -> {
                val g = when ((raw as Number).toInt()) {
                    GEAR_PARK -> Gear.P
                    GEAR_REVERSE -> Gear.R
                    GEAR_NEUTRAL -> Gear.N
                    else -> Gear.D
                }
                l(SignalEvent.GearChanged(now, g))
            }
            "PERF_VEHICLE_SPEED" -> {
                val ms = (raw as Number).toFloat()
                l(SignalEvent.Speed(now, kotlin.math.abs(ms) * 3.6f))
            }
            "TURN_SIGNAL_STATE" -> {
                val t = when ((raw as Number).toInt()) {
                    1 -> Turn.RIGHT
                    2 -> Turn.LEFT
                    else -> Turn.OFF
                }
                l(SignalEvent.TurnSignal(now, t))
            }
            "PERF_STEERING_ANGLE" -> l(SignalEvent.Steering(now, (raw as Number).toFloat()))
            "SEAT_BELT_BUCKLED" -> {
                // 운전석(1열 왼쪽 = areaId 1) 기준. 다른 좌석 값은 무시
                if (v.areaId == DRIVER_SEAT_AREA || v.areaId == 0) {
                    l(SignalEvent.SeatBelt(now, raw as? Boolean ?: false))
                }
            }
            "WINDSHIELD_WIPERS_STATE" -> {
                // WindshieldWiperState: 1 = OFF, 2 = ON
                l(SignalEvent.Wiper(now, (raw as Number).toInt() == 2))
            }
            "HEADLIGHTS_STATE" -> l(SignalEvent.Headlights(now, (raw as Number).toInt() == 1))
            "EV_BATTERY_LEVEL" -> l(SignalEvent.Battery(now, (raw as Number).toFloat()))
        }
    }

    override fun stop() {
        try {
            manager?.unregisterCallback(callback)
        } catch (_: Exception) {
        }
        try {
            car?.disconnect()
        } catch (_: Exception) {
        }
        car = null
        manager = null
        listener = null
    }

    override fun status(): List<SignalStatus> = statuses.values.toList()

    companion object {
        private const val TAG = "KarmaCarApi"
        private const val GEAR_NEUTRAL = 0x0001
        private const val GEAR_REVERSE = 0x0002
        private const val GEAR_PARK = 0x0004
        private const val DRIVER_SEAT_AREA = 0x0001

        fun isCarApiAvailable(): Boolean = try {
            Class.forName("android.car.Car")
            true
        } catch (e: Throwable) {
            false
        }
    }
}
