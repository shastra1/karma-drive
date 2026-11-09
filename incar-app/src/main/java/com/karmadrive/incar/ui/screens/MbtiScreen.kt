package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.game.Mbti
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.TwinCar

/** 운전 MBTI 16종 — "너 무슨 운전이야?" (공유 카드는 KARMA 앱에서 9:16으로 생성) */
@Composable
fun MbtiScreen(app: KarmaApp, onBack: () -> Unit) {
    val runs by app.store.runs.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    val mbti = remember(runs) { Mbti.of(runs.take(30)) }

    Page("운전 MBTI", "최근 RUN ${runs.take(30).size}회로 분석 · 4축 16유형", onBack = onBack) {
        if (mbti == null) {
            KCard(Modifier.fillMaxWidth()) {
                Text("아직 RUN이 없어요. 데모 주행을 한 번 해 보세요!", color = K.text, fontSize = 20.sp)
            }
        } else {
            KCard(Modifier.fillMaxWidth(), border = K.mint) {
                KRow {
                    TwinCar(Color(skin.color), app.twinMood(), Modifier.weight(0.35f).height(180.dp), Damage.levelOf(game.damage).level, game.stage, skinId = skin.id, scars = game.scars)
                    androidx.compose.foundation.layout.Column(Modifier.weight(0.65f)) {
                        Text(mbti.code, color = K.mint, fontSize = 56.sp, fontWeight = FontWeight.Black)
                        Text(mbti.name, color = K.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("“${mbti.line}”", color = K.gold, fontSize = 20.sp)
                    }
                }
            }
            val axes = listOf(
                Triple("리듬", mbti.code[0], if (mbti.code[0] == 'B') "B 버터 — 부드러운 가감속" else "J 점프 — 다이내믹한 가감속"),
                Triple("매너", mbti.code[1], if (mbti.code[1] == 'G') "G 젠틀 — 깜빡이 · 양보" else "S 스피디 — 효율 우선"),
                Triple("동선", mbti.code[2], if (mbti.code[2] == 'R') "R 루틴 — 늘 같은 길" else "E 익스플로러 — 새 길"),
                Triple("시간", mbti.code[3], if (mbti.code[3] == 'D') "D 데이 드라이버" else "N 나이트 드라이버"),
            )
            axes.forEach { (axis, letter, desc) ->
                KCard(Modifier.fillMaxWidth()) {
                    KRow {
                        KChip("$letter", K.mint)
                        Text("$axis · $desc", color = K.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            KCard(Modifier.fillMaxWidth()) {
                Text("공유 카드 · 궁합", color = K.sub, fontSize = 16.sp)
                Text("KARMA 앱에서 인스타 스토리(9:16) 카드로 공유하고 \"나랑 운전 MBTI 궁합 볼래?\" 링크로 크루를 만들 수 있어요.", color = K.text, fontSize = 16.sp)
            }
        }
    }
}
