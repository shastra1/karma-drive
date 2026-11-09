package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karmadrive.incar.run.LiveState
import com.karmadrive.incar.ui.K

/** IC02 — 주행 중: 화면 없음. 움직이는 요소 · 숫자 갱신 없음 (개발 로그는 개발자 설정에서만) */
@Composable
fun DrivingScreen(live: LiveState, showLog: Boolean, sharing: Boolean = false) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!showLog) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("운전에 집중하세요", color = K.sub, fontSize = 22.sp)
                Text("주차(P)하면 오늘의 언박싱이 열려요", color = K.sub.copy(alpha = 0.7f), fontSize = 16.sp)
                if (sharing) {
                    // 움직이지 않는 한 줄 안내만 — 트윈카 상태는 동승자 · 가족의 KARMA 앱에서만 보인다
                    Text("📡 트윈카 라이브 공유 중 · 동승자 · 가족은 KARMA 앱에서 볼 수 있어요", color = K.sub.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("[개발용] 판정 로그 · ${if (live.replaying) "시뮬레이션 데이터" else "실시간 신호"}", color = K.gold, fontSize = 14.sp)
                Text(
                    "기어 ${live.gear}  ${"%.0f".format(live.speedKmh)}km/h  조향 ${"%.0f".format(live.steeringDeg)}°  " +
                        "KP ${live.runKp}  데미지 ${live.runDamage}  콤보 ${live.combo}  ${"%.2f".format(live.distanceKm)}km",
                    color = K.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace,
                )
                live.log.take(18).forEach {
                    Text(it, color = K.sub, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
