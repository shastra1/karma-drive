package com.karmadrive.incar.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.karmadrive.incar.signal.SignalEvent
import com.karmadrive.incar.signal.SignalStatus
import com.karmadrive.incar.signal.VehicleSignalSource

/**
 * 위치 소스 — Android 표준 LocationManager. Pleos FusedLocation SDK가 출시되면 교체(어댑터 구조).
 * 에뮬레이터: 확장 컨트롤(…) → Location에서 위치 지정 또는 경로(GPX) 재생.
 */
class GpsSource(private val context: Context) : VehicleSignalSource {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null
    private var note = "대기"
    private var ok = false

    @SuppressLint("MissingPermission")
    override fun start(listener: (SignalEvent) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            note = "위치 권한 없음"
            return
        }
        val l = LocationListener { loc ->
            listener(
                SignalEvent.Location(
                    System.currentTimeMillis(), loc.latitude, loc.longitude,
                    if (loc.hasSpeed()) loc.speed * 3.6f else null,
                    if (loc.hasBearing()) loc.bearing else null,
                ),
            )
        }
        this.listener = l
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (providers.isEmpty()) {
            note = "위치 서비스 꺼짐"
            return
        }
        providers.forEach { p ->
            try {
                lm.requestLocationUpdates(p, 1_000L, 0f, l, Looper.getMainLooper())
            } catch (e: Exception) {
                note = "등록 실패: ${e.javaClass.simpleName}"
            }
        }
        ok = true
        note = providers.joinToString(", ")
    }

    override fun stop() {
        listener?.let { lm.removeUpdates(it) }
        listener = null
    }

    override fun status(): List<SignalStatus> = listOf(SignalStatus("위치(GPS)", ok, note))
}
