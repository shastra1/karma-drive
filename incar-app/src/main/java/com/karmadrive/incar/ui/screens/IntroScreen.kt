package com.karmadrive.incar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.ui.K
import com.karmadrive.incar.ui.TwinCar
import kotlinx.coroutines.delay

/** IC01 — 시동 안내: 오늘의 한 줄 + 트윈카, 3초 후 자동 닫힘 (이후 주행 중 화면 0) */
@Composable
fun IntroScreen(app: KarmaApp, onDone: () -> Unit) {
    val skinId by app.store.skin.collectAsStateWithLifecycle()
    val game by app.store.game.collectAsStateWithLifecycle()
    val skin = (app.store.skins.firstOrNull { it.id == skinId } ?: app.store.skins.first())
    val mood = app.twinMood()
    val line = when {
        game.mission != null -> "회생 미션 진행 중: ${game.mission!!.text}"
        else -> "오늘의 골든 로드는 출근길 어딘가에 숨어 있어요."
    }
    LaunchedEffect(Unit) {
        delay(3_000)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().clickable { onDone() }.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.karmadrive.incar.R.drawable.c_drive),
                contentDescription = "카르마 드라이버",
                modifier = Modifier.height(200.dp),
            )
            TwinCar(Color(skin.color), mood, Modifier.height(220.dp), Damage.levelOf(game.damage).level, game.stage, skinId = skin.id, scars = game.scars)
        }
        Text("${game.carName} · ${mood.emoji} ${mood.label}", color = K.mint, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("“${app.twinLine(mood)}”", color = K.text, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(line, color = K.gold, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text("화면을 누르면 바로 시작 · 출발하면 화면이 꺼져요", color = K.sub, fontSize = 14.sp)
    }
}
