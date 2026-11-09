package com.karmadrive.incar.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.game.TwinMood
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar

/** IC07 · FUTURE LAB — 컨셉카 드롭 · 내 출근길을 컨셉카로 · 지갑을 여는 디자인 투표 (가상의 컨셉카) */
@Composable
fun FutureLabScreen(app: KarmaApp, onConceptReplay: (RunSummary) -> Unit, onBack: () -> Unit) {
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    val colors = listOf(
        Triple("aurora", "오로라 바이올렛", 0xFFB07CFF),
        Triple("ocean", "딥 오션", 0xFF5AB4FF),
        Triple("sunset", "선셋 골드", 0xFFFFC857),
    )
    val share = mapOf("aurora" to 46, "ocean" to 31, "sunset" to 23)
    var preview by remember { mutableStateOf(colors.first()) }

    Page("FUTURE LAB", "아직 세상에 없는 차를, 내가 제일 먼저 · 이번 달 드롭", onBack = onBack) {
        notice?.let { Text(it, color = K.gold, fontSize = 16.sp) }
        KCard(Modifier.fillMaxWidth(), border = Color(preview.third).copy(alpha = 0.8f)) {
            KRow {
                TwinCar(Color(preview.third), TwinMood.SPECIAL, Modifier.weight(0.45f).height(220.dp), 0, 7)
                Column(Modifier.weight(0.55f)) {
                    KChip("NEW DROP · 리그 등급별 선공개", Color(0xFFB07CFF))
                    Text("KARMA CONCEPT 01", color = K.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("가상의 미래 EV 컨셉 · ${preview.second}", color = K.sub, fontSize = 18.sp)
                }
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("내 출근길을 컨셉카로", color = K.sub, fontSize = 16.sp)
            Text("내 실제 RUN을 컨셉카 트윈으로 다시 달려 봐요. \"내 출근길을 이 차로 달리면?\"", color = K.text, fontSize = 18.sp)
            val last = runs.firstOrNull()
            KButton(if (last != null) "▶ 최근 RUN 가상 시승" else "먼저 RUN을 한 번 해 주세요", Modifier.fillMaxWidth(), enabled = last != null) {
                last?.let(onConceptReplay)
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("디자인 투표 — KP로 '사전 구매'하는 지갑을 여는 투표 (100 KP)", color = K.sub, fontSize = 16.sp)
            colors.forEach { c ->
                val voted = game.vote == c.first
                KRow {
                    KButton(c.second, Modifier.weight(0.4f), primary = preview == c) { preview = c }
                    Column(Modifier.weight(0.4f)) {
                        Text("${share.getValue(c.first) + if (voted) 1 else 0}%", color = K.text, fontSize = 16.sp)
                        KGauge(share.getValue(c.first) / 100f, Color(c.third))
                    }
                    KButton(
                        if (voted) "투표함 ✓" else "투표",
                        Modifier.weight(0.2f),
                        primary = false,
                        enabled = game.vote.isEmpty() && total >= 100,
                    ) {
                        notice = if (app.store.vote(c.first)) "투표 완료! 이 컬러가 출시되면 '오리지널 디자이너' 배지를 드려요" else "이미 투표했거나 KP가 부족해요"
                    }
                }
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("카르마 어워즈 (연말)", color = K.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("올해의 도로의 전설 · 개과천선상 · 로드마스터 왕을 시상해요.", color = K.text, fontSize = 16.sp)
        }
    }
}
