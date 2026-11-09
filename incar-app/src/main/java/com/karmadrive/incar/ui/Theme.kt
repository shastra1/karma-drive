package com.karmadrive.incar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 다크 테마 · 대비 4.5:1 이상 · 최소 12sp · 터치 영역 48dp 이상 (Pleos UI 가이드) */
object K {
    val bg = Color(0xFF0B1220)
    val surface = Color(0xFF141E31)
    val surface2 = Color(0xFF1C2942)
    val line = Color(0xFF2A3A5A)
    val text = Color(0xFFF2F5FA)
    val sub = Color(0xFFA9B6CC)
    val mint = Color(0xFF3BE8B0)
    val gold = Color(0xFFFFC857)
    val red = Color(0xFFFF6B6B)
    val sky = Color(0xFF5AB4FF)
}

@Composable
fun KarmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = K.mint, onPrimary = K.bg, secondary = K.gold,
            background = K.bg, surface = K.surface, onSurface = K.text, onBackground = K.text,
            error = K.red,
        ),
        content = content,
    )
}

@Composable
fun KCard(modifier: Modifier = Modifier, border: Color = K.line, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(K.surface, RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun KButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick, enabled = enabled,
            modifier = modifier.heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = K.mint, contentColor = K.bg),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled,
            modifier = modifier.heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) { Text(text, fontSize = 18.sp, color = K.text, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun KChip(text: String, color: Color = K.mint, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun KTitle(text: String, sub: String? = null) {
    Column {
        Text(text, color = K.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, color = K.sub, fontSize = 16.sp)
    }
}

@Composable
fun KRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** 데미지 · 진행 게이지 */
@Composable
fun KGauge(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 18.dp)
            .background(K.surface2, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .heightIn(min = 18.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}
