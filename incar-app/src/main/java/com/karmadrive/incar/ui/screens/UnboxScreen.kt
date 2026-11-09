package com.karmadrive.incar.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.engine.KarmaType
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.game.Damage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar
import kotlinx.coroutines.delay

/** IC04 — 언박싱: RUN 등급 · KP 카운트업 · 획득 칩 · 트윈카 한마디(TTS) */
@Composable
fun UnboxScreen(app: KarmaApp, run: RunSummary, onNext: () -> Unit) {
    var opened by remember(run.runId) { mutableStateOf(false) }
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    val mood = app.twinMood()
    val line = app.twinLine(mood)
    val mvp = run.skills.filter { it.type != KarmaType.DISTANCE }.maxByOrNull { it.kp }
    val worst = run.blacks.filter { it.type.kind == Kind.BLACK }.minByOrNull { it.kp }
    val clock = SimpleDateFormat("HH:mm", Locale.KOREA)
    // 이번 주행에서 새로 생긴 상처 · 주행 중 도착한 응원
    val runScars = game.scars.filter { it.t in run.startMs..run.endMs }
    val cheers = remember(run.runId) { app.store.cheersSince(run.startMs) }

    LaunchedEffect(run.runId) {
        delay(1_500)
        opened = true
    }
    LaunchedEffect(opened) {
        if (opened) app.voice.speak(app.unboxSentence(run) + " " + line)
    }
    val kpShown by animateIntAsState(if (opened) run.kp else 0, tween(1_500), label = "kp")
    val gradeScale by animateFloatAsState(if (opened) 1f else 0.3f, tween(600), label = "grade")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("RUN 언박싱", color = K.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        if (!opened) {
            GiftBox(Modifier.size(220.dp).clickable { opened = true })
            Text("상자를 누르면 열려요", color = K.sub, fontSize = 18.sp)
        } else {
            Text(run.grade.label, color = gradeColor(run.grade), fontSize = 120.sp, fontWeight = FontWeight.Black, modifier = Modifier.scale(gradeScale))
            Text("${if (kpShown >= 0) "+" else ""}$kpShown KP", color = K.gold, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("${run.grade.line} · 매너 지수 ${run.mq}", color = K.text, fontSize = 20.sp)
            KRow(Modifier.padding(horizontal = 8.dp)) {
                TwinCar(
                    Color(skin.color), mood, Modifier.weight(0.45f).height(200.dp), Damage.levelOf(game.damage).level, game.stage,
                    skinId = skin.id, scars = game.scars, showPins = runScars.isNotEmpty(),
                    highlight = runScars.map { it.id }.toSet(),
                )
                KCard(Modifier.weight(0.55f)) {
                    Text("${game.carName}의 한마디 · ${mood.emoji} ${mood.label}", color = K.sub, fontSize = 14.sp)
                    Text("“$line”", color = K.text, fontSize = 20.sp)
                    Text(app.unboxSentence(run), color = K.sub, fontSize = 14.sp)
                }
            }
            KRow {
                mvp?.let {
                    KCard(Modifier.weight(1f), border = K.gold.copy(alpha = 0.6f)) {
                        Text("MVP 장면", color = K.gold, fontSize = 14.sp)
                        Text("${clock.format(Date(it.t))} ${it.type.emoji} ${it.type.title}", color = K.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                worst?.let {
                    KCard(Modifier.weight(1f), border = K.red.copy(alpha = 0.5f)) {
                        Text("흑역사 장면", color = K.red, fontSize = 14.sp)
                        Text("${clock.format(Date(it.t))} ${it.type.emoji} ${it.type.title}", color = K.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (game.lastNote.isNotEmpty()) Text(game.lastNote, color = K.gold, fontSize = 16.sp)
            CheerCard(cheers)
            if (runScars.isNotEmpty()) {
                KCard(Modifier.fillMaxWidth(), border = K.red.copy(alpha = 0.6f)) {
                    Text("이번 주행에서 트윈카에 생긴 상처 ${runScars.size}곳", color = K.red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    runScars.forEach { s ->
                        Text("${game.scars.indexOf(s) + 1}. ${s.type.emoji} ${s.label} — ${s.reason}", color = K.text, fontSize = 16.sp)
                    }
                    Text("주행 중에는 동승자 · 가족 화면에만 실시간으로 보였어요", color = K.sub, fontSize = 14.sp)
                }
            }
            val kept = run.skills.filter { it.type in LAW_SKILLS }
            if (kept.isNotEmpty()) {
                KCard(Modifier.fillMaxWidth(), border = K.mint.copy(alpha = 0.5f)) {
                    Text("법규 · 도로 준수 보상", color = K.mint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    kept.forEach { e -> Text("${e.type.emoji} ${e.type.title} +${e.kp} KP · ${e.detail}", color = K.text, fontSize = 16.sp) }
                }
            }
            val skillCounts = run.skills.groupBy { it.type }
            if (skillCounts.isNotEmpty()) {
                Text("획득 스킬", color = K.sub, fontSize = 16.sp)
                skillCounts.entries.chunked(3).forEach { row ->
                    KRow {
                        row.forEach { (type, list) ->
                            KChip("${type.emoji} ${type.title} ×${list.size} · +${list.sumOf { it.kp }}", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            KRow {
                KChip("거리 ${"%.1f".format(run.distanceKm)}km", K.sky)
                KChip("최대 콤보 ${run.maxCombo}", K.sky)
                if (run.damage > 0) KChip("데미지 ${run.damage}", K.red)
            }
            if (!app.store.rouletteDoneToday()) {
                var won by remember { mutableStateOf<Int?>(null) }
                KButton(won?.let { "🎰 데일리 룰렛 +$it KP!" } ?: "🎰 데일리 주차 룰렛 돌리기", Modifier.fillMaxWidth(), primary = false, enabled = won == null) {
                    won = app.store.spinRoulette()
                }
            }
            KButton(if (run.blacks.isNotEmpty()) "흑역사 확인하기" else "허브로", Modifier.fillMaxWidth()) { onNext() }
        }
    }
}

/** 도로 · 법규 데이터와 연결된 준수 스킬 (스쿨존 · 제한속도 · 정지선 · 황색불 · 돌발 구간) */
private val LAW_SKILLS = setOf(
    KarmaType.SCHOOL_ZONE_STEALTH, KarmaType.LIMIT_KEEPER, KarmaType.STOP_LINE_MASTER,
    KarmaType.YELLOW_BRAKE, KarmaType.SAFE_PASS, KarmaType.PERFECT_STOP,
)

@Composable
private fun GiftBox(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(K.mint, topLeft = Offset(w * 0.15f, h * 0.38f), size = Size(w * 0.7f, h * 0.52f))
        drawRect(Color(0xFF2BC495), topLeft = Offset(w * 0.1f, h * 0.26f), size = Size(w * 0.8f, h * 0.16f))
        drawRect(K.gold, topLeft = Offset(w * 0.45f, h * 0.26f), size = Size(w * 0.1f, h * 0.64f))
        drawCircle(K.gold, radius = w * 0.09f, center = Offset(w * 0.4f, h * 0.2f))
        drawCircle(K.gold, radius = w * 0.09f, center = Offset(w * 0.6f, h * 0.2f))
    }
}
