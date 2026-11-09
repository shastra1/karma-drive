package com.karmadrive.incar.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.game.Quests
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import kotlinx.coroutines.launch

/** 퀘스트 · 데일리 주차 룰렛 (하루 1회 무료, 10~100 KP) */
@Composable
fun QuestScreen(app: KarmaApp, onBack: () -> Unit) {
    val game by app.store.game.collectAsStateWithLifecycle()
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val done = app.store.rouletteDoneToday()
    val colors = listOf(K.mint, K.gold, K.sky, Color(0xFFB07CFF), K.red, Color(0xFF2BC495))

    Page("퀘스트 · 룰렛", "보유 $total KP · 오늘 RUN ${app.store.todayRuns().size}회 · 스트릭 ${app.store.streakDays()}일", onBack = onBack) {
        KCard(Modifier.fillMaxWidth(), border = K.gold.copy(alpha = 0.6f)) {
            Text("🎰 데일리 주차 룰렛", color = K.gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            KRow {
                Canvas(Modifier.size(160.dp).rotate(angle.value)) {
                    val sweep = 360f / colors.size
                    colors.forEachIndexed { i, c ->
                        drawArc(c, i * sweep, sweep, true, topLeft = Offset.Zero, size = Size(size.width, size.height))
                    }
                    drawCircle(Color(0xFF0B1220), radius = size.width * 0.12f)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        result ?: if (done) "오늘은 이미 돌렸어요 (+${game.rouletteWon} KP)" else "하루 1번 무료 · 10~100 KP",
                        color = K.text, fontSize = 20.sp,
                    )
                    KButton(if (done) "내일 또 만나요" else "돌리기!", Modifier.fillMaxWidth(), enabled = !done) {
                        val won = app.store.spinRoulette()
                        scope.launch {
                            angle.animateTo(angle.value + 1440f + (0..359).random(), tween(2_000, easing = FastOutSlowInEasing))
                            result = won?.let { "🎉 +$it KP 당첨!" } ?: "오늘은 이미 돌렸어요"
                            refresh++
                        }
                    }
                }
            }
        }
        Text("데일리 퀘스트", color = K.sub, fontSize = 16.sp)
        Quests.daily.forEach { q ->
            val p = remember(runs, game, refresh) { app.store.questProgress(q.id) }
            val claimed = remember(runs, game, refresh) { app.store.questClaimed(q.id) }
            KCard(Modifier.fillMaxWidth(), border = if (claimed) K.mint.copy(alpha = 0.5f) else K.line) {
                KRow {
                    Column(Modifier.weight(1f)) {
                        Text(q.title, color = K.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("$p / ${q.goal} · 보상 ${q.reward} KP", color = K.sub, fontSize = 15.sp)
                        KGauge(p / q.goal.toFloat(), K.mint)
                    }
                    KButton(if (claimed) "받음 ✓" else "보상 받기", primary = !claimed && p >= q.goal, enabled = !claimed && p >= q.goal) {
                        app.store.claimQuest(q.id)
                        refresh++
                    }
                }
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("위클리 · 시즌 · 히든 (KARMA 앱에서 확장)", color = K.sub, fontSize = 16.sp)
            Text("이번 주 버터 드라이빙 5구간 · 흑역사 0인 날 4일 / 이번 달 로드마스터 깃발 1개 / 비 오는 날 RUN S", color = K.text, fontSize = 16.sp)
        }
    }
}
