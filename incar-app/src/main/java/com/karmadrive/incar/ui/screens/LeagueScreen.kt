package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.engine.KarmaType
import com.karmadrive.incar.game.Tier
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow

/** 매너 리그 · 로드마스터 (프로토타입: 다른 운전자는 모의 데이터, 랭킹에 속도 지표 사용 안 함) */
@Composable
fun LeagueScreen(app: KarmaApp, onBack: () -> Unit) {
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val month = app.store.monthRuns()
    val myMq = app.store.avgMq()
    val tier = app.store.tier()
    val board = (
        listOf("깜빡이 장인" to 96, "버터 드라이버" to 93, "퍼펙트 스토퍼" to 91, "빗길 마스터" to 88, "출근길 매너왕" to 84, "주말 드라이버" to 77) +
            ("나 · ${game.carName}" to myMq)
        ).sortedByDescending { it.second }
    val schoolGuard = month.any { r -> r.events.any { it.type == KarmaType.SCHOOL_ZONE_STEALTH } }
    val segments = listOf(
        Triple("출근길 테헤란로 3.2km", 92, "깜빡이 장인"),
        Triple("역삼역 → 선릉역 1.8km", 89, "퍼펙트 스토퍼"),
        Triple("역삼초 스쿨존 0.5km", 95, "출근길 매너왕"),
    )

    Page("이번 달 매너 리그", "실명 없이 닉네임만 · 다른 운전자는 프로토타입 모의 데이터", onBack = onBack) {
        KCard(Modifier.fillMaxWidth(), border = K.mint.copy(alpha = 0.6f)) {
            Text("내 리그: ${tier.label}", color = K.mint, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("이번 달 평균 매너 지수 $myMq · RUN ${month.size}회", color = K.text, fontSize = 18.sp)
            val next = Tier.entries.getOrNull(tier.ordinal + 1)
            if (next != null) {
                Text("다음 ${next.label}까지 매너 지수 ${(next.minMq - myMq).coerceAtLeast(0)}점", color = K.sub, fontSize = 16.sp)
                KGauge(if (next.minMq == 0) 1f else myMq / next.minMq.toFloat(), K.mint)
            }
        }
        Text("순위 (매너 지수 기준)", color = K.sub, fontSize = 16.sp)
        board.forEachIndexed { i, (name, mq) ->
            val me = name.startsWith("나")
            KCard(Modifier.fillMaxWidth(), border = if (me) K.mint else K.line) {
                KRow {
                    Text("${i + 1}", color = if (i < 3) K.gold else K.sub, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(name, color = K.text, fontSize = 19.sp, fontWeight = if (me) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    KChip("MQ $mq", if (me) K.mint else K.sky)
                }
            }
        }
        Text("로드마스터 — 구간별 매너 지수 1위가 깃발을 꽂아요", color = K.sub, fontSize = 16.sp)
        segments.forEach { (seg, rivalMq, rival) ->
            val school = seg.contains("스쿨존")
            val mine = myMq > rivalMq || (school && schoolGuard && myMq >= rivalMq - 3)
            KCard(Modifier.fillMaxWidth(), border = if (mine) K.gold else K.line) {
                KRow {
                    Text(if (mine) "🚩" else "🏳", fontSize = 26.sp)
                    Column(Modifier.weight(1f)) {
                        Text(seg + if (school) " · 스쿨존 수호자 깃발 (보너스 2배)" else "", color = K.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (mine) "도로 주인: 나 · 매주 월요일 갱신 · 유지 보너스 하루 최대 20 KP"
                            else "도로 주인: $rival (MQ $rivalMq) · 나와 ${rivalMq - myMq}점 차",
                            color = if (mine) K.gold else K.sub, fontSize = 15.sp,
                        )
                    }
                }
            }
        }
        KCard(Modifier.fillMaxWidth()) {
            Text("개과천선상", color = K.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("꼴찌는 공개하지 않아요. 대신 한 달간 가장 많이 성장한 사람을 축하해요.", color = K.text, fontSize = 16.sp)
        }
    }
}
