package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.run.LiveState
import com.karmadrive.incar.road.RepoStatus
import com.karmadrive.incar.signal.SignalStatus
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KRow
import kotlinx.coroutines.delay

/** 신호 점검 · 개발자 설정 — Virtual Console 조작이 앱에 들어오는지 확인 */
@Composable
fun DiagScreen(app: KarmaApp, live: LiveState, onBack: () -> Unit) {
    val devLog by app.devLogWhileDriving.collectAsStateWithLifecycle()
    val voiceStatus by app.voice.status.collectAsStateWithLifecycle()
    val ux by app.driving.requiresDO.collectAsStateWithLifecycle()
    val uxAvail by app.driving.available.collectAsStateWithLifecycle()
    var statuses by remember { mutableStateOf<List<SignalStatus>>(emptyList()) }
    var confirmReset by remember { mutableStateOf(false) }
    var roadStatuses by remember { mutableStateOf<List<RepoStatus>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            statuses = app.recorder.statuses()
            roadStatuses = app.roads.statuses()
            delay(1_000)
        }
    }

    Page("신호 점검", "Virtual Console에서 조작하면 값이 바로 바뀌어야 해요", onBack = onBack) {
        KCard(Modifier.fillMaxWidth(), border = K.gold.copy(alpha = 0.6f)) {
            Text("시연 · 테스트 도구 (개발용)", color = K.sub, fontSize = 16.sp)
            KRow {
                KButton("KP +5,000", Modifier.weight(1f), primary = false) { app.store.devAddKp(5_000) }
                KButton("데미지 +20", Modifier.weight(1f), primary = false) { app.store.devAddDamage(20) }
                KButton("데미지 −20", Modifier.weight(1f), primary = false) { app.store.devAddDamage(-20) }
            }
            Text("쿠폰몰 · 성장 사다리 · 데미지 6단계를 영상에서 보여 줄 때 사용", color = K.sub, fontSize = 14.sp)
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("차량 신호 연결", color = K.sub, fontSize = 16.sp)
            if (!app.carApiAvailable) Text("✗ 이 기기에는 Car API가 없어요 (데모 주행만 가능)", color = K.red, fontSize = 18.sp)
            if (statuses.isEmpty() && app.carApiAvailable) Text("연결 중… (권한 허용 후 표시)", color = K.sub, fontSize = 16.sp)
            statuses.forEach { s ->
                Text(
                    "${if (s.available) "✓" else "✗"} ${s.name}${if (s.note.isNotEmpty()) " — ${s.note}" else ""}",
                    color = if (s.available) K.mint else K.red, fontSize = 18.sp,
                )
            }
            Text("주행 제한(UX Restrictions): ${if (!uxAvail) "확인 불가" else if (ux) "제한 중" else "해제"}", color = K.text, fontSize = 18.sp)
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("도로 · 법규 데이터 (공공 API)", color = K.sub, fontSize = 16.sp)
            Text(
                "공공데이터포털 키 ${if (app.roads.dataKeySet) "✓" else "✗ 없음"} · ITS 키 ${if (app.roads.itsKeySet) "✓" else "✗ 없음"}",
                color = K.text, fontSize = 18.sp,
            )
            roadStatuses.forEach { r ->
                Text(
                    "${if (r.ok) "✓" else "✗"} ${r.name}${if (r.count > 0) " ${r.count}건" else ""} — ${r.message}",
                    color = if (r.ok) K.mint else K.gold, fontSize = 16.sp,
                )
            }
            val road = live.road
            Text(
                "현재 위치 " + (live.lat?.let { "${"%.5f".format(it)}, ${"%.5f".format(live.lon ?: 0.0)}" } ?: "없음 (에뮬레이터 확장 컨트롤 → Location에서 지정)"),
                color = K.text, fontSize = 16.sp,
            )
            Text(
                road?.let {
                    "제한속도 ${it.limitKmh}km/h · ${it.limitSource}" +
                        (it.schoolZone?.let { z -> "\n어린이보호구역: ${z.name}" } ?: "") +
                        (it.incident?.let { i -> "\n돌발: ${i.title} ${i.detail}" } ?: "") +
                        (it.signal?.let { g -> "\n신호: ${g.intersectionName} ${g.phase} · 정지선 ${"%.0f".format(g.distToStopLineM)}m" } ?: "")
                } ?: "도로 맥락 없음",
                color = K.mint, fontSize = 18.sp,
            )
            KButton("도로 데이터 다시 받기", Modifier.fillMaxWidth(), primary = false) { app.roads.loadAll(force = true) }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("트윈카 음성 (TTS)", color = K.sub, fontSize = 16.sp)
            Text(voiceStatus, color = if (voiceStatus.startsWith("✗")) K.red else K.mint, fontSize = 18.sp)
            KButton("음성 테스트", Modifier.fillMaxWidth(), primary = false) {
                app.voice.speak("안녕하세요, 카르마 드라이브 트윈카예요. 소리가 잘 들리나요?")
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("실시간 값", color = K.sub, fontSize = 16.sp)
            Text(
                "기어 ${live.gear} · 속도 ${"%.1f".format(live.speedKmh)} km/h · 가속 ${"%.2f".format(live.accelG)} g\n" +
                    "방향지시등 ${live.turn} · 조향 ${"%.0f".format(live.steeringDeg)}° · 벨트 ${live.belt ?: "?"} · 와이퍼 ${if (live.wiper) "ON" else "OFF"}\n" +
                    "RUN ${if (live.running) "기록 중" else "대기"} · KP ${live.runKp} · 데미지 ${live.runDamage} · 콤보 ${live.combo} · ${"%.2f".format(live.distanceKm)} km",
                color = K.text, fontSize = 18.sp, fontFamily = FontFamily.Monospace,
            )
        }
        KCard(Modifier.fillMaxWidth()) {
            KRow {
                Text("주행 중 판정 로그 표시 (개발 · 영상 촬영용)", color = K.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Switch(checked = devLog, onCheckedChange = { app.devLogWhileDriving.value = it })
            }
            Text("앱마켓 제출 빌드에서는 꺼 둡니다 (주행 중 화면 0 정책).", color = K.sub, fontSize = 14.sp)
        }
        KRow {
            if (live.replaying) KButton("시나리오 중단", Modifier.weight(1f), primary = false) { app.recorder.stopScenario() }
            KButton(if (confirmReset) "한 번 더 누르면 초기화" else "기록 초기화", Modifier.weight(1f), primary = false) {
                if (confirmReset) {
                    app.store.resetAll()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("신호 로그 (최근 순)", color = K.sub, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            live.log.take(40).forEach { Text(it, color = K.sub, fontSize = 14.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}
