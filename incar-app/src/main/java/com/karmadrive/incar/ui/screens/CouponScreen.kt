package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.navi.NaviLauncher
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KGauge
import com.karmadrive.incar.ui.KRow

/** LOOT — 가상 아이템샵 · 실물 쿠폰몰(실버 + 5,000 KP에 오픈) · IC08 사용처 길안내 · 누적 KP 마일스톤 */
@Composable
fun CouponScreen(app: KarmaApp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val total by app.store.totalKp.collectAsStateWithLifecycle()
    val owned by app.store.ownedCoupons.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val runs by app.store.runs.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    val open = app.store.couponMallOpen()
    val earned = runs.sumOf { it.kp.coerceAtLeast(0) }

    Page("샵 · 쿠폰", "1 KP ≈ 1원 · 보유 $total KP · 유료 결제 · 확률형 아이템 없음", onBack = onBack) {
        notice?.let { Text(it, color = K.gold, fontSize = 16.sp) }

        Text("가상 아이템샵", color = K.sub, fontSize = 16.sp)
        app.store.items.forEach { item ->
            KCard(Modifier.fillMaxWidth()) {
                KRow {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = K.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(item.desc, color = K.sub, fontSize = 15.sp)
                    }
                    KButton("${item.costKp} KP", primary = false, enabled = total >= item.costKp && game.damage > 0) {
                        notice = if (app.store.buyItem(item)) "${item.name} 완료! ${game.carName}가 기뻐해요" else "KP가 부족하거나 고칠 곳이 없어요"
                    }
                }
            }
        }

        KCard(Modifier.fillMaxWidth(), border = if (open) K.mint else K.line) {
            Text(if (open) "🔓 실물 쿠폰몰 열림" else "🔒 실물 쿠폰몰 — 쌓아야 열린다", color = if (open) K.mint else K.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (!open) Text(app.store.mallLockReason(), color = K.text, fontSize = 16.sp)
        }
        app.store.coupons.forEach { c ->
            val has = owned.contains(c.id)
            KCard(Modifier.fillMaxWidth(), border = if (has) K.mint.copy(alpha = 0.6f) else K.line) {
                KRow {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = K.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(c.place, color = K.sub, fontSize = 16.sp)
                    }
                    KChip(if (has) "보유" else "${c.costKp} KP", if (has) K.mint else K.gold)
                }
                if (has) {
                    KButton("내비로 보내기", Modifier.fillMaxWidth()) {
                        val ok = NaviLauncher.send(ctx, c)
                        notice = if (ok) "${c.place} 길안내를 보냈어요." else "길안내 앱을 찾지 못했어요. (Pleos NaviHelper 연결 예정)"
                    }
                } else {
                    KButton("${c.costKp} KP로 교환", Modifier.fillMaxWidth(), primary = false, enabled = open && total >= c.costKp) {
                        notice = if (app.store.buyCoupon(c)) "${c.title} 쿠폰을 받았어요. 양도는 안 돼요." else "아직 교환할 수 없어요"
                    }
                }
            }
        }

        KCard(Modifier.fillMaxWidth()) {
            Text("누적 KP 마일스톤 · 지금까지 $earned KP", color = K.sub, fontSize = 16.sp)
            app.store.milestones.forEach { (goal, reward) ->
                KRow {
                    Text(if (earned >= goal) "✓" else "·", color = K.mint, fontSize = 18.sp)
                    Column(Modifier.weight(1f)) {
                        Text("${"%,d".format(goal)} KP — $reward", color = K.text, fontSize = 16.sp)
                        KGauge(earned / goal.toFloat(), K.gold)
                    }
                }
            }
        }
    }
}
