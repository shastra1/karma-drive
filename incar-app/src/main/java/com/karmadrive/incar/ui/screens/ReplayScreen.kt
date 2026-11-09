package com.karmadrive.incar.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KChip
import com.karmadrive.incar.ui.KRow
import kotlin.math.hypot
import kotlin.math.max

/** IC03 — 리플레이: 경로 · 이벤트 핀 연출 (주차 중에만) */
@Composable
fun ReplayScreen(run: RunSummary, concept: Boolean = false, onDone: () -> Unit) {
    val accent = if (concept) Color(0xFFB07CFF) else K.mint
    val progress = remember(run.runId) { Animatable(0f) }
    LaunchedEffect(run.runId) {
        progress.animateTo(1f, tween(durationMillis = 9_000, easing = LinearEasing))
        kotlinx.coroutines.delay(800)
        onDone()
    }

    val path = run.path
    // 이벤트마다 가장 가까운 경로 지점의 순번 (언제 핀을 보여줄지)
    val eventIdx = remember(run.runId) {
        run.events.map { e ->
            var best = 0
            var bestD = Float.MAX_VALUE
            path.forEachIndexed { i, p ->
                val d = hypot(p.x - e.x, p.y - e.y)
                if (d < bestD) { bestD = d; best = i }
            }
            best
        }
    }

    val p = progress.value
    val headIdx = ((path.size - 1) * p).toInt().coerceAtLeast(0)
    val shown = run.events.indices.filter { eventIdx[it] <= headIdx }
    val latest = shown.lastOrNull()?.let { run.events[it] }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        KRow {
            Text(if (concept) "내 출근길 × KARMA CONCEPT 01" else "오늘의 리플레이", color = K.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (concept) KChip("컨셉카 가상 시승", accent)
            if (run.simulated) KChip("시뮬레이션 데이터", K.gold)
            KButton("건너뛰기", primary = false) { onDone() }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0E1A2E), RoundedCornerShape(20.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                // 지도 느낌의 격자 도로
                val grid = 6
                for (i in 0..grid) {
                    val x = size.width * i / grid
                    val y = size.height * i / grid
                    drawLine(Color(0xFF1C2942), Offset(x, 0f), Offset(x, size.height), strokeWidth = 6f)
                    drawLine(Color(0xFF1C2942), Offset(0f, y), Offset(size.width, y), strokeWidth = 6f)
                }
                if (path.size < 2) return@Canvas
                val minX = path.minOf { it.x }
                val maxX = path.maxOf { it.x }
                val minY = path.minOf { it.y }
                val maxY = path.maxOf { it.y }
                val span = max(max(maxX - minX, maxY - minY), 1f)
                val scale = minOf(size.width, size.height) / span * 0.9f
                val cx = (minX + maxX) / 2
                val cy = (minY + maxY) / 2
                fun map(x: Float, y: Float) = Offset(size.width / 2 + (x - cx) * scale, size.height / 2 + (y - cy) * scale)

                // 전체 경로(흐리게) → 진행 경로(밝게)
                val full = Path().apply {
                    path.forEachIndexed { i, pt -> val o = map(pt.x, pt.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                }
                drawPath(full, Color(0xFF2A3A5A), style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val done = Path().apply {
                    for (i in 0..headIdx) { val o = map(path[i].x, path[i].y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                }
                drawPath(done, accent, style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // 출발 · 도착
                drawCircle(K.sky, radius = 14f, center = map(path.first().x, path.first().y))
                // 이벤트 핀
                shown.forEach { i ->
                    val e = run.events[i]
                    val c = when (e.type.kind) { Kind.SKILL -> K.gold; Kind.BLACK -> K.red; Kind.WARNING -> Color(0xFFFFA94D) }
                    val o = map(e.x, e.y)
                    drawCircle(c, radius = 18f, center = o)
                    drawCircle(Color(0xFF0B1220), radius = 7f, center = o)
                }
                // 트윈카 위치
                val head = map(path[headIdx].x, path[headIdx].y)
                if (concept) drawCircle(accent.copy(alpha = 0.35f), radius = 34f, center = head)
                drawCircle(Color.White, radius = 16f, center = head)
                drawCircle(accent, radius = 10f, center = head)
            }
        }
        Text(
            latest?.let { "${it.type.emoji} ${it.type.title}  ${if (it.kp >= 0) "+" else ""}${it.kp} KP  ${it.detail}" } ?: "출발!",
            color = if (latest?.type?.kind == Kind.SKILL || latest == null) K.gold else K.red,
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        Text("${"%.1f".format(run.distanceKm)} km · ${run.durationMin}분 · 이벤트 ${run.events.size}개", color = K.sub, fontSize = 16.sp)
    }
}
