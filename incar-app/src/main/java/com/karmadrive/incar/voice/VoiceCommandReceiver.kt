package com.karmadrive.incar.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.MainActivity
import com.karmadrive.incar.navi.NaviLauncher
import com.karmadrive.incar.signal.Gear

/**
 * IC09 음성 응답 (Gleo App Schema 함수와 같은 이름). Gleo 연동 승인 전에는 adb 방송으로 시연:
 *   adb shell am broadcast -a com.karmadrive.incar.GET_TODAY_KARMA -n com.karmadrive.incar/.voice.VoiceCommandReceiver
 * 주행 중에는 화면 없이 음성으로만 답한다.
 */
class VoiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = KarmaApp.instance
        val parked = app.recorder.live.value.gear == Gear.P
        when (intent.action) {
            ACTION_TODAY -> app.voice.speak(app.todayKarmaSentence())
            ACTION_REPLAY -> {
                if (!parked) {
                    app.voice.speak("주차하면 오늘의 리플레이를 열어 드릴게요.")
                } else {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_REPLAY)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            ACTION_COUPON -> {
                val type = intent.getStringExtra("couponType") ?: "CAR_WASH"
                val owned = app.store.ownedCoupons.value
                val c = app.store.coupons.firstOrNull { it.category == type && owned.contains(it.id) }
                    ?: app.store.coupons.firstOrNull { it.category == type }
                if (c == null) {
                    app.voice.speak("해당 쿠폰을 찾지 못했어요.")
                } else {
                    val ok = NaviLauncher.send(context, c)
                    app.voice.speak(if (ok) "${c.place}로 길안내를 보냈어요." else "${c.place}, 내비 연결은 주차 후 다시 시도해 주세요.")
                }
            }
        }
    }

    companion object {
        const val ACTION_TODAY = "com.karmadrive.incar.GET_TODAY_KARMA"
        const val ACTION_REPLAY = "com.karmadrive.incar.OPEN_REPLAY"
        const val ACTION_COUPON = "com.karmadrive.incar.SEND_COUPON_ROUTE"
    }
}
