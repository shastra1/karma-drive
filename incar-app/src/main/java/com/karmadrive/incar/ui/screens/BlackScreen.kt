package com.karmadrive.incar.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar

/** IC05 — 흑역사 리포트: 이름 · 데미지 게이지(0~100, 6단계) · 회생 미션 · 이의신청 */
@Composable
fun BlackScreen(app: KarmaApp, run: RunSummary, onRepair: () -> Unit = {}, onDone: () -> Unit) {
    val game by app.store.game.collectAsStateWithLifecycle()
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    var accepted by remember(run.runId) { mutableStateOf(game.mission != null) }
    var appealed by remember(run.runId) { mutableStateOf(false) }
    val lv = Damage.levelOf(game.damage)
    val heavy = lv.level >= 4
    val need = if (heavy) 3 else 1
    val reward = if (heavy) 20 else 5
    val missionText = "48시간 안에 RUN A 이상 ${need}회 → 데미지 −$reward"
    val mood = app.twinMood()

    Page("흑역사 리포트", "상처마다 사유가 남아요 · 한 번에 폐차는 없어요 (RUN당 데미지 최대 25)", onBack = onDone) {
        KRow {
            TwinCar(
                Color(skin.color), mood, Modifier.weight(0.45f).height(220.dp), lv.level, game.stage,
                skinId = skin.id, scars = game.scars, showPins = true,
                highlight = game.scars.filter { it.t in run.startMs..run.endMs }.map { it.id }.toSet(),
            )
            KCard(Modifier.weight(0.55f), border = K.red.copy(alpha = 0.6f)) {
                Text("누적 데미지 ${game.damage} / 100 · Lv.${lv.level} ${lv.name}", color = K.red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                KGauge(game.damage / 100f, K.red)
                Text("이번 RUN +${run.damage} · ${lv.desc}", color = K.sub, fontSize = 14.sp)
                Text("“${app.twinLine(mood)}”", color = K.text, fontSize = 18.sp)
            }
        }
        run.blacks.forEach { e ->
            val warn = e.type.kind == Kind.WARNING
            val scar = game.scars.firstOrNull { it.t == e.t && it.type == e.type }
            if (scar != null) {
                ScarCard(game.scars.indexOf(scar) + 1, scar, fresh = true)
            } else {
                KCard(Modifier.fillMaxWidth(), border = if (warn) K.gold.copy(alpha = 0.5f) else K.red.copy(alpha = 0.4f)) {
                    Text("${e.type.emoji} ${e.type.title}", color = if (warn) K.gold else K.red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (warn) e.detail else com.karmadrive.incar.game.Scars.reasonOf(e), color = K.text, fontSize = 16.sp)
                    if (!warn) Text("카르마 빚 ${e.kp} KP · 데미지 +${e.damage} (이미 수리됨)", color = K.sub, fontSize = 16.sp)
                }
            }
        }
        KButton("🔧 정비소에서 부위별로 고치기", Modifier.fillMaxWidth(), primary = false) { onRepair() }
        KCard(Modifier.fillMaxWidth(), border = K.gold.copy(alpha = 0.6f)) {
            Text("회생 미션", color = K.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(game.mission?.text ?: missionText, color = K.text, fontSize = 20.sp)
            KButton(if (accepted) "미션 진행 중 ✓" else "미션 받기", Modifier.fillMaxWidth(), enabled = !accepted) {
                app.store.acceptMission(missionText, need, reward)
                accepted = true
            }
            Text("자연 회복: 3일 연속 흑역사 0이면 데미지 5 자동 회복", color = K.sub, fontSize = 14.sp)
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("억울하면 이의신청", color = K.sub, fontSize = 16.sp)
            Text("GPS 오차 · 공사 구간 등으로 잘못 판정됐다면 리플레이를 근거로 검토 후 복구해요.", color = K.text, fontSize = 16.sp)
            KButton(if (appealed) "이의신청 접수됨 (검토 중)" else "이의신청 하기", Modifier.fillMaxWidth(), primary = false, enabled = !appealed) {
                appealed = true
            }
        }
        KButton("허브로", Modifier.fillMaxWidth(), primary = false) { onDone() }
    }
}
