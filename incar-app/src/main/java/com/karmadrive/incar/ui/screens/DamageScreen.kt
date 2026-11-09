package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.R
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar

/**
 * 트윈카 상태 · 정비소 (KA06 · KA08 연결).
 * 트윈카 위 번호 핀 = 흑역사 1건. 핀이나 카드를 누르면 "왜 망가졌는지" 사유와 판정 근거가 나오고,
 * 부위별로 KP를 써서 고친다.
 */
@Composable
fun DamageScreen(app: KarmaApp, onBack: () -> Unit) {
    val game by app.store.game.collectAsStateWithLifecycle()
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val skin = app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first()
    val lv = Damage.levelOf(game.damage)
    val mood = app.twinMood()
    var selected by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scars = game.scars

    Page("트윈카 정비소", "상처 하나 = 흑역사 하나 · 핀을 누르면 사유가 보여요", onBack = onBack) {
        KCard(Modifier.fillMaxWidth(), border = if (lv.level >= 4) K.red else K.gold.copy(alpha = 0.6f)) {
            KRow {
                TwinCar(
                    Color(skin.color), mood, Modifier.weight(0.62f).height(300.dp),
                    lv.level, game.stage, skinId = skin.id, scars = scars, showPins = true,
                    highlight = setOfNotNull(selected),
                    onPin = { selected = it.id },
                )
                Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(game.carName, color = K.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("데미지 ${game.damage}/100", color = if (lv.level >= 4) K.red else K.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    KGauge(game.damage / 100f, if (lv.level >= 4) K.red else K.gold)
                    Text("Lv.${lv.level} ${lv.name} · ${lv.desc}", color = K.text, fontSize = 16.sp)
                    KChip("상처 ${scars.size}곳", if (scars.isEmpty()) K.mint else K.red)
                    KChip("보유 $total KP", K.mint)
                }
            }
            if (scars.isEmpty()) {
                Text("상처가 하나도 없어요. 쇼룸 컨디션! ✨", color = K.mint, fontSize = 18.sp)
            }
        }

        notice?.let { Text(it, color = K.gold, fontSize = 16.sp) }

        // 시연용: KP가 모자라면 바로 채울 수 있게 (정식 빌드에서는 제거)
        if (scars.isNotEmpty() && total < app.store.repairAllCost()) {
            KButton("🎁 시연용 KP +5,000 받기 (보유 $total KP)", Modifier.fillMaxWidth(), primary = false) {
                app.store.devAddKp(5_000)
                notice = "시연용 KP 5,000을 넣었어요. 이제 수리해 보세요."
            }
        }

        if (scars.isNotEmpty()) {
            KCard(Modifier.fillMaxWidth()) {
                KRow {
                    Image(painterResource(R.drawable.c_repair), contentDescription = "정비하는 카르마 드라이버", modifier = Modifier.height(110.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("정비 고릴라: “어디 보자… 전부 고치면 ${app.store.repairAllCost()} KP!”", color = K.text, fontSize = 17.sp)
                        Text("풀 리페어는 부위별 수리비 합계에서 20% 할인", color = K.sub, fontSize = 14.sp)
                        KButton("🔧 풀 리페어 · ${app.store.repairAllCost()} KP", Modifier.fillMaxWidth()) {
                            notice = if (app.store.repairAll()) "풀 리페어 완료! 다시 반짝반짝" else "KP가 부족해요"
                            selected = null
                        }
                    }
                }
            }
            val sel = scars.firstOrNull { it.id == selected }
            if (sel != null) {
                Text("선택한 상처", color = K.gold, fontSize = 16.sp)
                ScarCard(scars.indexOf(sel) + 1, sel, selected = true) {
                    notice = if (app.store.repairScar(sel.id)) "${sel.label} 수리 완료!" else "KP가 부족해요 (${sel.repairCost} KP 필요)"
                    selected = null
                }
            }
            Text("상처 기록 (최근 순)", color = K.sub, fontSize = 16.sp)
            scars.withIndex().reversed().forEach { (i, s) ->
                ScarCard(i + 1, s, selected = s.id == selected) {
                    notice = if (app.store.repairScar(s.id)) "${s.label} 수리 완료!" else "KP가 부족해요 (${s.repairCost} KP 필요)"
                    if (selected == s.id) selected = null
                }
            }
        }

        LiveShareCard(app)
    }
}

/** 라이브 트윈 공유 안내 — 운전자는 못 보지만 동승자 · 가족은 폰 · PC로 실시간 관람 */
@Composable
fun LiveShareCard(app: KarmaApp) {
    val share = app.share
    KCard(Modifier.fillMaxWidth(), border = K.sky.copy(alpha = 0.6f)) {
        Text("📡 라이브 트윈 공유", color = K.sky, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            if (share.running) "공유 중 · 동승자 · 가족은 KARMA 앱(라이브 트윈)에서 지금 트윈카 상태와 사유를 볼 수 있어요"
            else "공유 서버가 꺼져 있어요" + (share.lastError?.let { " ($it)" } ?: ""),
            color = K.text, fontSize = 16.sp,
        )
        Text("PC 브라우저: adb forward tcp:${share.port} tcp:${share.port} → http://127.0.0.1:${share.port}", color = K.sub, fontSize = 14.sp)
        Text("폰 에뮬레이터 KARMA 앱: 서버 주소 http://10.0.2.2:${share.port}", color = K.sub, fontSize = 14.sp)
        Text("지금 보는 사람: ${share.viewersNow()}명", color = K.sub, fontSize = 14.sp)
    }
}
