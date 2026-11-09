package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karmadrive.incar.engine.Grade
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.KButton
import com.karmadrive.incar.ui.KRow
import com.karmadrive.incar.ui.KTitle

@Composable
fun Page(title: String, sub: String? = null, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KRow {
            if (onBack != null) KButton("← 허브", primary = false, onClick = onBack)
            KTitle(title, sub)
        }
        content()
    }
}

fun gradeColor(g: Grade) = when (g) {
    Grade.S -> K.mint
    Grade.A -> K.sky
    Grade.B -> K.gold
    Grade.C -> K.gold
    Grade.D -> K.red
}

