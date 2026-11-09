package com.karmadrive.incar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karmadrive.incar.R
import com.karmadrive.incar.game.Scar
import com.karmadrive.incar.game.ScarKind
import com.karmadrive.incar.game.Twin
import com.karmadrive.incar.game.TwinMood
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 그림 이름 → 리소스 (화면 기획안 트윈카 · 카르마 드라이버 일러스트) */
fun twinDrawable(name: String): Int = when (name) {
    "old_lv1" -> R.drawable.old_lv1
    "old_lv2" -> R.drawable.old_lv2
    "old_lv3" -> R.drawable.old_lv3
    "old_lv4" -> R.drawable.old_lv4
    "old_lv5" -> R.drawable.old_lv5
    "old_yellow" -> R.drawable.old_yellow
    "old_pink" -> R.drawable.old_pink
    "ev_blue" -> R.drawable.ev_blue
    "ev_red" -> R.drawable.ev_red
    "concept_white" -> R.drawable.concept_white
    else -> R.drawable.old_lv0
}

/**
 * 운전습관 디지털 트윈카.
 * 바탕 그림(데미지 6단계 · 성장 단계 · 튜닝 컬러) 위에, 흑역사마다 생긴 상처를 해당 부위에 덧그린다.
 *  - 상처 종류: 먼지 · 긁힘 · 찌그러짐 · 유리 금 · 그을음 · 경고등 · 램프 파손 · 연기
 *  - [showPins] 이면 상처마다 번호 핀 → 탭하면 [onPin]으로 사유를 보여 준다
 *  - [highlight] 에 든 상처는 "쾅" 하고 방금 생긴 것처럼 번쩍인다
 */
@Composable
fun TwinCar(
    color: Color,
    mood: TwinMood,
    modifier: Modifier = Modifier,
    damageLevel: Int = 0,
    stage: Int = 0,
    skinId: String = "mint",
    scars: List<Scar> = emptyList(),
    showPins: Boolean = false,
    highlight: Set<String> = emptySet(),
    onPin: ((Scar) -> Unit)? = null,
) {
    val look = Twin.look(stage, skinId, damageLevel)
    val aspect = Twin.aspect(look.image)
    val anim = rememberInfiniteTransition(label = "twin")
    val pulse by anim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = true)) {
            // Legend 후광
            if (stage >= 7) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x663BE8B0), Color.Transparent), center = center, radius = size.minDimension * 0.7f),
                        radius = size.minDimension * 0.7f,
                    )
                }
            }
            Image(
                painter = painterResource(twinDrawable(look.image)),
                contentDescription = "트윈카",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            val tapModifier = if (onPin != null && scars.isNotEmpty()) {
                Modifier.pointerInput(scars, look.image) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val hit = scars.minByOrNull { s ->
                            val (ax, ay) = Twin.anchor(s.part, look)
                            hypot(ax * w - pos.x, ay * h - pos.y)
                        }
                        if (hit != null) {
                            val (ax, ay) = Twin.anchor(hit.part, look)
                            if (hypot(ax * w - pos.x, ay * h - pos.y) < w * 0.12f) onPin(hit)
                        }
                    }
                }
            } else Modifier
            Canvas(Modifier.fillMaxSize().then(tapModifier)) {
                if (!look.damageBaked && damageLevel > 0) drawDirt(damageLevel, Twin.bodyRect(look.image))
                // 같은 부위에 여러 상처가 겹치면 조금씩 비켜 그린다
                val perPart = mutableMapOf<String, Int>()
                scars.forEach { s ->
                    val n = perPart.getOrDefault(s.part.name, 0)
                    perPart[s.part.name] = n + 1
                    val (ax, ay) = Twin.anchor(s.part, look)
                    val c = Offset(ax * size.width + n * size.width * 0.025f, ay * size.height - n * size.height * 0.02f)
                    drawScar(s.kind, c, size.width * 0.07f, pulse, s.id.hashCode())
                    if (s.id in highlight) {
                        val r = size.width * (0.05f + 0.12f * pulse)
                        drawCircle(Color(0xFFFF5252).copy(alpha = 1f - pulse), radius = r, center = c, style = Stroke(width = size.width * 0.012f))
                    }
                }
                if (showPins) {
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = size.width * 0.032f
                        isFakeBoldText = true
                        this.color = android.graphics.Color.WHITE
                    }
                    val drawn = mutableMapOf<String, Int>()
                    scars.forEachIndexed { i, s ->
                        val n = drawn.getOrDefault(s.part.name, 0)
                        drawn[s.part.name] = n + 1
                        val (ax, ay) = Twin.anchor(s.part, look)
                        val pin = Offset(ax * size.width + n * size.width * 0.045f, ay * size.height - size.height * 0.1f)
                        val r = size.width * 0.026f
                        drawLine(Color.White.copy(alpha = 0.8f), pin, Offset(ax * size.width, ay * size.height), strokeWidth = size.width * 0.004f)
                        drawCircle(if (s.damage >= 8) Color(0xFFE53935) else Color(0xFFFF8F00), radius = r, center = pin)
                        drawCircle(Color.White, radius = r, center = pin, style = Stroke(width = size.width * 0.005f))
                        drawContext.canvas.nativeCanvas.drawText("${i + 1}", pin.x, pin.y + paint.textSize * 0.35f, paint)
                    }
                }
            }
            // 기분 말풍선 (그림 얼굴은 고정이라 이모지로 무드를 표시)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color(0xCC0B1220), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text(mood.emoji, fontSize = 18.sp, color = color) }
        }
    }
}

/** 성장 단계 EV · 튜닝 컬러 그림에는 데미지가 그려져 있지 않으므로 먼지 · 녹을 덧칠 */
private fun DrawScope.drawDirt(level: Int, body: FloatArray) {
    val rnd = Random(7)
    val n = level * 10
    for (i in 0 until n) {
        val x = (body[0] + rnd.nextFloat() * (body[2] - body[0])) * size.width
        val y = (body[1] + (0.35f + rnd.nextFloat() * 0.6f) * (body[3] - body[1])) * size.height
        val rust = level >= 4 && i % 3 == 0
        drawCircle(
            if (rust) Color(0x99C0572B) else Color(0x667A6A55),
            radius = size.width * (0.006f + rnd.nextFloat() * 0.012f),
            center = Offset(x, y),
        )
    }
}

/** 상처 하나 그리기 — c: 부위 중심, r: 기준 크기 */
private fun DrawScope.drawScar(kind: ScarKind, c: Offset, r: Float, pulse: Float, seed: Int) {
    val rnd = Random(seed)
    when (kind) {
        ScarKind.DUST -> repeat(9) {
            val a = rnd.nextFloat() * 6.28f
            val d = rnd.nextFloat() * r
            drawCircle(Color(0x887A6A55), radius = r * (0.08f + rnd.nextFloat() * 0.12f), center = Offset(c.x + cos(a) * d, c.y + sin(a) * d * 0.6f))
        }
        ScarKind.SCRATCH -> repeat(4) { i ->
            val y = c.y - r * 0.3f + i * r * 0.2f
            drawLine(Color(0xDDFFFFFF), Offset(c.x - r * 0.9f, y), Offset(c.x + r * 0.7f, y - r * 0.35f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            drawLine(Color(0x99402020), Offset(c.x - r * 0.85f, y + r * 0.05f), Offset(c.x + r * 0.6f, y - r * 0.28f), strokeWidth = r * 0.03f, cap = StrokeCap.Round)
        }
        ScarKind.DENT, ScarKind.SMOKE -> {
            drawOval(
                Brush.radialGradient(listOf(Color(0xAA1A1A1A), Color(0x33000000), Color.Transparent), center = c, radius = r),
                topLeft = Offset(c.x - r, c.y - r * 0.65f), size = Size(r * 2f, r * 1.3f),
            )
            drawArc(Color(0xCCFFFFFF), 200f, 120f, false, topLeft = Offset(c.x - r * 0.7f, c.y - r * 0.4f), size = Size(r * 1.4f, r * 0.8f), style = Stroke(width = r * 0.06f))
            drawArc(Color(0x99000000), 20f, 120f, false, topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.3f), size = Size(r * 1.2f, r * 0.6f), style = Stroke(width = r * 0.08f))
            if (kind == ScarKind.SMOKE) {
                // 피어오르는 연기 (주차 중 화면에서만 움직임)
                for (k in 0 until 4) {
                    val p = (pulse + k * 0.25f) % 1f
                    drawCircle(
                        Color(0xFF9AA4B2).copy(alpha = 0.55f * (1f - p)),
                        radius = r * (0.35f + p * 0.9f),
                        center = Offset(c.x + sin((p + k) * 3f) * r * 0.3f, c.y - r * 0.4f - p * r * 3.2f),
                    )
                }
            }
        }
        ScarKind.CRACK -> {
            val white = Color(0xEEFFFFFF)
            for (k in 0 until 8) {
                val a = k * 0.785f + rnd.nextFloat() * 0.3f
                val len = r * (0.6f + rnd.nextFloat() * 0.6f)
                val end = Offset(c.x + cos(a) * len, c.y + sin(a) * len)
                drawLine(white, c, end, strokeWidth = r * 0.035f)
                val mid = Offset(c.x + cos(a) * len * 0.55f, c.y + sin(a) * len * 0.55f)
                drawLine(white, mid, Offset(mid.x + cos(a + 0.8f) * len * 0.3f, mid.y + sin(a + 0.8f) * len * 0.3f), strokeWidth = r * 0.025f)
            }
            drawCircle(white, radius = r * 0.08f, center = c)
        }
        ScarKind.SOOT -> drawCircle(
            Brush.radialGradient(listOf(Color(0xDD111111), Color(0x66222222), Color.Transparent), center = c, radius = r * 1.2f),
            radius = r * 1.2f, center = c,
        )
        ScarKind.WARNING_LIGHT -> {
            drawCircle(Color(0xFFFFB300).copy(alpha = 0.35f + 0.4f * (1f - pulse)), radius = r * (0.7f + 0.3f * pulse), center = c)
            drawCircle(Color(0xFFFFB300), radius = r * 0.45f, center = c)
            drawLine(Color(0xFF3A2A00), Offset(c.x, c.y - r * 0.28f), Offset(c.x, c.y + r * 0.08f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF3A2A00), radius = r * 0.07f, center = Offset(c.x, c.y + r * 0.25f))
        }
        ScarKind.BROKEN_LAMP -> {
            drawCircle(Color(0xCC2B2B2B), radius = r * 0.75f, center = c)
            val shards = Path().apply {
                moveTo(c.x - r * 0.5f, c.y - r * 0.2f); lineTo(c.x, c.y + r * 0.1f); lineTo(c.x + r * 0.45f, c.y - r * 0.4f)
                moveTo(c.x - r * 0.2f, c.y + r * 0.5f); lineTo(c.x, c.y + r * 0.1f); lineTo(c.x + r * 0.5f, c.y + r * 0.3f)
            }
            drawPath(shards, Color(0xEEFFFFFF), style = Stroke(width = r * 0.06f))
            repeat(5) {
                drawCircle(Color(0xCCBFD9F2), radius = r * 0.06f, center = Offset(c.x + (rnd.nextFloat() - 0.5f) * r * 2f, c.y + r * (0.8f + rnd.nextFloat() * 0.4f)))
            }
        }
        ScarKind.BENT_WHEEL -> drawArc(
            Color(0xFFFF7043), 0f, 300f, false,
            topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.6f), size = Size(r * 1.6f, r * 1.2f),
            style = Stroke(width = r * 0.1f, cap = StrokeCap.Round),
        )
    }
}
