package com.karmadrive.incar.navi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.karmadrive.incar.data.Coupon

/**
 * 쿠폰 사용처를 내비로 보내기.
 * v0.1: 표준 geo: 인텐트. v0.2: Pleos NaviHelper requestRoute(RouteInfo)로 교체.
 */
object NaviLauncher {
    fun send(context: Context, coupon: Coupon): Boolean {
        val uri = Uri.parse("geo:${coupon.lat},${coupon.lon}?q=${coupon.lat},${coupon.lon}(${Uri.encode(coupon.place)})")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
