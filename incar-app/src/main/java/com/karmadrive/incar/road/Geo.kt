package com.karmadrive.incar.road

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** 두 좌표 사이 거리(m) */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    /** 빠른 사전 필터: 위경도 차이로 대략 반경 안인지 */
    fun roughlyWithin(lat1: Double, lon1: Double, lat2: Double, lon2: Double, meters: Double): Boolean {
        val dLat = meters / 111_000.0
        val dLon = meters / (111_000.0 * cos(Math.toRadians(lat1)).coerceAtLeast(0.1))
        return kotlin.math.abs(lat1 - lat2) <= dLat && kotlin.math.abs(lon1 - lon2) <= dLon
    }
}
