package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.karmadrive.incar.game.Badges
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.game.Ladder
import com.karmadrive.incar.game.Personality
import com.karmadrive.incar.game.TwinMood
import com.karmadrive.incar.game.abilitiesOf
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar

/** 마이 개러지 — 트윈카 육성(이름 · 성격 · 무드 · 능력치 · 데미지 · 성장 사다리 · 튜닝 · 배지) */
@Composable
fun GarageScreen(app: KarmaApp, onBack: () -> Unit) {
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    val lv = Damage.levelOf(game.damage)
    val ab = remember(runs) { abilitiesOf(runs) }
    val mood = app.twinMood()
    var name by remember(game.carName) { mutableStateOf(game.carName) }
    var notice by remember { mutableStateOf<String?>(null) }
    var washTaps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { app.store.markMoodChecked() }

    Page("마이 개러지", "운전한 대로, 차가 된다", onBack = onBack) {
        notice?.let { Text(it, color = K.gold, fontSize = 16.sp) }
        KRow {
            TwinCar(Color(skin.color), mood, Modifier.weight(0.45f).height(240.dp), lv.level, game.stage, skinId = skin.id, scars = game.scars)
            KCard(Modifier.weight(0.55f)) {
                Text("${mood.emoji} 지금 기분: ${mood.label}", color = K.mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("“${app.twinLine(mood)}”", color = K.text, fontSize = 18.sp)
                Text("${Ladder.stages[game.stage].name} · ${Ladder.stages[game.stage].car}", color = K.sub, fontSize = 16.sp)
                Text("데미지 ${game.damage}/100 · Lv.${lv.level} ${lv.name}", color = if (lv.level >= 4) K.red else K.gold, fontSize = 16.sp)
                KGauge(game.damage / 100f, if (lv.level >= 4) K.red else K.gold)
            }
        }

        KCard(Modifier.fillMaxWidth()) {
            Text("이름 짓기 · 성격", color = K.sub, fontSize = 16.sp)
            KRow {
                OutlinedTextField(value = name, onValueChange = { name = it.take(12) }, singleLine = true, modifier = Modifier.weight(1f))
                KButton("저장", primary = false) { app.store.rename(name); notice = "이름을 '${name}'(으)로 바꿨어요" }
            }
            KRow {
                Personality.entries.forEach { p ->
                    KButton(p.label, Modifier.weight(1f), primary = game.personality == p) { app.store.setPersonality(p) }
                }
            }
        }

        KCard(Modifier.fillMaxWidth()) {
            Text("차 능력치", color = K.sub, fontSize = 16.sp)
            listOf("매너 MNR" to ab.mnr, "부드러움 SMT" to ab.smt, "에코 ECO" to ab.eco, "내공 EXP" to ab.exp).forEach { (label, v) ->
                KRow {
                    Text(label, color = K.text, fontSize = 16.sp, modifier = Modifier.weight(0.3f))
                    Column(Modifier.weight(0.6f)) { KGauge(v / 100f, K.mint) }
                    Text("$v", color = K.mint, fontSize = 16.sp, modifier = Modifier.weight(0.1f))
                }
            }
        }

        KCard(Modifier.fillMaxWidth(), border = K.sky.copy(alpha = 0.5f)) {
            Text("세차장 미니게임 — 거품 칠하고 닦기 (10번 탭)", color = K.sub, fontSize = 16.sp)
            KGauge(washTaps / 10f, K.sky)
            KButton(if (washTaps < 10) "🫧 문지르기 $washTaps/10" else "✨ 반짝! 다시 하기", Modifier.fillMaxWidth(), primary = false, enabled = game.damage > 0 || washTaps >= 10) {
                if (washTaps >= 10) {
                    washTaps = 0
                } else {
                    washTaps++
                    if (washTaps == 10) {
                        app.store.repairByMiniGame(1)
                        notice = "세차 완료! 데미지 −1 · ${game.carName}: “${app.twinLine(TwinMood.RECOVER)}”"
                    }
                }
            }
            if (game.damage == 0) Text("이미 반짝반짝해요", color = K.sub, fontSize = 14.sp)
        }

        KCard(Modifier.fillMaxWidth()) {
            Text("성장 사다리 · 보유 $total KP", color = K.sub, fontSize = 16.sp)
            Ladder.stages.forEach { st ->
                val cur = st.index == game.stage
                val next = st.index == game.stage + 1
                KRow {
                    Text(if (st.index <= game.stage) "✓" else "·", color = K.mint, fontSize = 18.sp)
                    Column(Modifier.weight(1f)) {
                        Text("${st.name} ${st.car}", color = if (cur) K.mint else K.text, fontSize = 17.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal)
                        Text("${st.condition} · 특전: ${st.perk}", color = K.sub, fontSize = 13.sp)
                    }
                    if (next) {
                        KButton("${st.cost} KP", primary = total >= st.cost, enabled = total >= st.cost) {
                            notice = if (app.store.upgradeStage()) "${st.name} 업그레이드! 똥차 졸업 축하해요" else "KP가 부족해요"
                        }
                    } else if (st.cost > 0) {
                        KChip("${st.cost} KP", K.sub)
                    }
                }
            }
        }

        KCard(Modifier.fillMaxWidth()) {
            Text("튜닝샵 — 컬러", color = K.sub, fontSize = 16.sp)
            app.store.skins.forEach { s ->
                val owned = game.ownedSkins.contains(s.id)
                KRow {
                    TwinCar(Color(s.color), TwinMood.CALM, Modifier.weight(0.2f).height(70.dp), 0, game.stage, skinId = s.id)
                    Text(s.name, color = K.text, fontSize = 18.sp, modifier = Modifier.weight(0.45f))
                    KButton(
                        when {
                            s.id == skinId -> "장착 중"
                            owned -> "장착"
                            else -> "${s.costKp} KP"
                        },
                        Modifier.weight(0.35f),
                        primary = s.id != skinId,
                        enabled = s.id != skinId && (owned || total >= s.costKp),
                    ) { if (!app.store.buySkin(s)) notice = "KP가 부족해요" }
                }
            }
        }

        KCard(Modifier.fillMaxWidth()) {
            val badges = Badges.of(runs, game.stage, game.missionsDone)
            Text("배지 도감 ${badges.count { it.earned }}/${badges.size}", color = K.sub, fontSize = 16.sp)
            badges.chunked(3).forEach { row ->
                KRow {
                    row.forEach { b ->
                        KChip(
                            (if (b.earned) "🏅 " else "🔒 ") + b.name,
                            if (b.earned) K.gold else K.sub,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
