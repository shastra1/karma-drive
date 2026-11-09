package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karmadrive.incar.data.Cheer
import com.karmadrive.incar.game.Scar
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KCard
import com.karmadrive.incar.ui.KRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CLOCK = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

/** 상처 1건 카드: 번호 · 부위 · 사유 · 근거 데이터 · 수리 */
@Composable
fun ScarCard(
    index: Int,
    scar: Scar,
    selected: Boolean = false,
    fresh: Boolean = false,
    onRepair: (() -> Unit)? = null,
) {
    val heavy = scar.damage >= 8
    KCard(Modifier.fillMaxWidth(), border = if (selected) K.gold else if (heavy) K.red.copy(alpha = 0.6f) else K.line) {
        KRow {
            Box(
                Modifier.size(36.dp).background(if (heavy) Color(0xFFE53935) else Color(0xFFFF8F00), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$index", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) {
                Text(
                    "${scar.type.emoji} ${scar.label}" + if (fresh) "  · 방금 생김" else "",
                    color = if (heavy) K.red else K.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text("${scar.type.title} · ${CLOCK.format(Date(scar.t))}" + if (scar.place.isNotEmpty()) " · ${scar.place}" else "", color = K.sub, fontSize = 14.sp)
            }
        }
        Text("사유: ${scar.reason}", color = K.text, fontSize = 17.sp)
        Text(
            "카르마 빚 ${scar.kp} KP · 데미지 +${scar.damage}" + if (scar.damage == 0) " (오늘 데미지 상한 25 도달 — 상처만 남음)" else "",
            color = K.sub, fontSize = 14.sp,
        )
        Text("근거: ${scar.source}", color = K.sub.copy(alpha = 0.8f), fontSize = 13.sp)
        if (onRepair != null) {
            KButton("🔧 이 부위 수리 · ${scar.repairCost} KP", Modifier.fillMaxWidth(), primary = false, onClick = onRepair)
        }
    }
}

/** 동승자 · 가족이 라이브 트윈에서 보낸 응원 스티커 */
@Composable
fun CheerCard(cheers: List<Cheer>) {
    if (cheers.isEmpty()) return
    KCard(Modifier.fillMaxWidth(), border = K.sky.copy(alpha = 0.6f)) {
        KRow {
            Image(painterResource(com.karmadrive.incar.R.drawable.c_thumb), contentDescription = null, modifier = Modifier.height(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("주행 중 도착한 응원 ${cheers.size}개", color = K.sky, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                cheers.takeLast(4).forEach {
                    Text("${it.emoji} ${it.from}: ${it.text}", color = K.text, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
