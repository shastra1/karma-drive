package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.game.Ladder
import com.karmadrive.incar.game.Quests
import com.karmadrive.incar.run.LiveState
import com.karmadrive.incar.signal.Scenarios
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.Screen
import com.karmadrive.incar.ui.TwinCar

/** IC06 — P단 허브. 세로(1:1) 창은 한 줄, 가로(2:0) 창은 두 칸 */
@Composable
fun HubScreen(app: KarmaApp, live: LiveState, onReplay: (RunSummary) -> Unit, onGo: (Screen) -> Unit) {
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    val today = remember(runs) { app.store.todayRuns() }
    val mood = app.twinMood()
    val dmg = Damage.levelOf(game.damage)
    val stage = Ladder.stages[game.stage]
    val questDone = Quests.daily.count { app.store.questClaimed(it.id) }
    var notice by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.2f && maxWidth > 900.dp

        val left: @Composable () -> Unit = {
            KCard(Modifier.fillMaxWidth(), border = K.mint.copy(alpha = 0.5f)) {
                KRow {
                    TwinCar(
                        Color(skin.color), mood, Modifier.weight(0.5f).height(190.dp), dmg.level, game.stage,
                        skinId = skin.id, scars = game.scars, showPins = game.scars.isNotEmpty(),
                        onPin = { onGo(Screen.DAMAGE) },
                    )
                    Column(Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${game.carName} · ${stage.name}", color = K.sub, fontSize = 16.sp)
                        Text("$total KP", color = K.mint, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${mood.emoji} ${mood.label} · 데미지 Lv.${dmg.level} ${dmg.name}", color = K.text, fontSize = 16.sp)
                        KGauge(game.damage / 100f, if (dmg.level >= 4) K.red else K.gold)
                    }
                }
                Text("“${app.twinLine(mood)}”", color = K.text, fontSize = 18.sp)
                if (game.scars.isNotEmpty()) {
                    val last = game.scars.last()
                    Text("최근 상처: ${last.type.emoji} ${last.label} — ${last.reason}", color = K.red, fontSize = 15.sp)
                }
                KButton(
                    if (game.scars.isEmpty()) "🔧 트윈카 정비소" else "🔧 상처 ${game.scars.size}곳 · 사유 보고 수리하기",
                    Modifier.fillMaxWidth(), primary = game.scars.isNotEmpty(),
                ) { onGo(Screen.DAMAGE) }
            }
            KCard(Modifier.fillMaxWidth()) {
                Text("오늘의 결과 · ${app.store.tier().label} 리그", color = K.sub, fontSize = 16.sp)
                if (today.isEmpty()) {
                    Text("아직 오늘 RUN이 없어요. 기어 D로 출발해 보세요.", color = K.text, fontSize = 18.sp)
                } else {
                    val best = today.maxByOrNull { it.kp }!!
                    KRow {
                        KChip("RUN ${today.size}회")
                        KChip("${today.sumOf { it.kp }} KP", K.gold)
                        KChip("최고 ${best.grade.label}", gradeColor(best.grade))
                        KChip("MQ ${best.mq}", K.sky)
                    }
                }
                runs.firstOrNull()?.let { r -> KButton("최근 RUN 리플레이 보기", Modifier.fillMaxWidth()) { onReplay(r) } }
                if (game.lastNote.isNotEmpty()) Text(game.lastNote, color = K.gold, fontSize = 14.sp)
            }
            game.mission?.let {
                KCard(Modifier.fillMaxWidth(), border = K.gold.copy(alpha = 0.6f)) {
                    Text("회생 미션", color = K.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(it.text, color = K.text, fontSize = 18.sp)
                }
            }
        }

        val right: @Composable () -> Unit = {
            KRow {
                KButton("🏠 개러지", Modifier.weight(1f), primary = false) { onGo(Screen.GARAGE) }
                KButton("🏆 리그", Modifier.weight(1f), primary = false) { onGo(Screen.LEAGUE) }
            }
            KRow {
                KButton("🎯 퀘스트 · 룰렛 $questDone/${Quests.daily.size}", Modifier.weight(1f), primary = !app.store.rouletteDoneToday()) { onGo(Screen.QUESTS) }
                KButton("🛍 샵 · 쿠폰", Modifier.weight(1f), primary = false) { onGo(Screen.COUPONS) }
            }
            KRow {
                KButton("🚀 FUTURE LAB", Modifier.weight(1f), primary = false) { onGo(Screen.FUTURE) }
                KButton("🧬 운전 MBTI", Modifier.weight(1f), primary = false) { onGo(Screen.MBTI) }
            }
            KButton("🎙 오늘 점수 듣기", Modifier.fillMaxWidth(), primary = false) {
                app.voice.speak(app.todayKarmaSentence())
                notice = "음성: " + app.todayKarmaSentence()
            }
            KCard(Modifier.fillMaxWidth()) {
                Text("데모 주행 (시뮬레이션 데이터 · 8배속)", color = K.sub, fontSize = 16.sp)
                Scenarios.all.forEach { s ->
                    KButton("▶ ${s.title}", Modifier.fillMaxWidth(), primary = false, enabled = !live.running && !live.replaying) {
                        val ok = app.recorder.startScenario(s)
                        notice = if (ok) "${s.title} 재생 시작 — 곧 주행 화면으로 바뀝니다" else "주행 중에는 재생할 수 없어요"
                    }
                }
                Text("실제 신호 테스트: Virtual Console에서 기어 D → 조작 → P", color = K.sub, fontSize = 14.sp)
            }
            KButton("신호 점검 · 개발자 설정", Modifier.fillMaxWidth(), primary = false) { onGo(Screen.DIAG) }
            com.karmadrive.incar.ui.screens.LiveShareCard(app)
            notice?.let { Text(it, color = K.gold, fontSize = 16.sp) }
        }

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KRow {
                Text("KARMA DRIVE", color = K.mint, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                KChip("P · 주차 중", K.sky)
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { left() }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { right() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    left(); right()
                }
            }
        }
    }
}
