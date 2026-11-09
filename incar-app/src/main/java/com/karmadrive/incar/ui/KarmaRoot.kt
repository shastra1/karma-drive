package com.karmadrive.incar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karmadrive.incar.KarmaApp
import com.karmadrive.incar.MainActivity
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.signal.Gear
import com.karmadrive.incar.ui.screens.BlackScreen
import com.karmadrive.incar.ui.screens.CouponScreen
import com.karmadrive.incar.ui.screens.DiagScreen
import com.karmadrive.incar.ui.screens.DrivingScreen
import com.karmadrive.incar.ui.screens.GarageScreen
import com.karmadrive.incar.ui.screens.HubScreen
import com.karmadrive.incar.ui.screens.IntroScreen
import com.karmadrive.incar.ui.screens.LeagueScreen
import com.karmadrive.incar.ui.screens.ReplayScreen
import com.karmadrive.incar.ui.screens.UnboxScreen
import com.karmadrive.incar.ui.screens.QuestScreen
import com.karmadrive.incar.ui.screens.MbtiScreen
import com.karmadrive.incar.ui.screens.FutureLabScreen
import com.karmadrive.incar.ui.screens.DamageScreen

enum class Screen { INTRO, HUB, REPLAY, UNBOX, BLACK, GARAGE, LEAGUE, COUPONS, DIAG, QUESTS, FUTURE, MBTI, DAMAGE }

@Composable
fun KarmaRoot(app: KarmaApp, activity: MainActivity) {
    var screen by rememberSaveable { mutableStateOf(Screen.INTRO) }
    var current by remember { mutableStateOf<RunSummary?>(null) }
    /** IC07: 내 RUN을 컨셉카로 재생 */
    var conceptMode by remember { mutableStateOf(false) }

    val live by app.recorder.live.collectAsStateWithLifecycle()
    val pending by app.recorder.pending.collectAsStateWithLifecycle()
    val open by activity.openRequest.collectAsStateWithLifecycle()
    val devLog by app.devLogWhileDriving.collectAsStateWithLifecycle()

    val driving = live.gear == Gear.D || live.gear == Gear.R

    // 주차(P) 순간 → 리플레이 → 언박싱 자동 진입
    LaunchedEffect(pending, driving) {
        val p = pending
        if (p != null && !driving) {
            current = p
            conceptMode = false
            app.recorder.consumePending()
            screen = Screen.REPLAY
        }
    }
    // 음성 명령 "리플레이 열어줘"
    LaunchedEffect(open) {
        if (open == MainActivity.OPEN_REPLAY) {
            app.store.runs.value.firstOrNull()?.let { current = it; screen = Screen.REPLAY }
            activity.openRequest.value = null
        }
    }

    BackHandler(enabled = screen != Screen.HUB && screen != Screen.INTRO && !driving) { screen = Screen.HUB }

    Box(Modifier.fillMaxSize().background(K.bg)) {
        val run = current
        val needsRun = screen == Screen.REPLAY || screen == Screen.UNBOX || screen == Screen.BLACK
        val effective = if (needsRun && run == null) Screen.HUB else screen
        if (driving) {
            // IC02: 주행 중 화면 0 — 렌더링 · 애니메이션 · 소리 중지
            DrivingScreen(live = live, showLog = devLog, sharing = app.share.running)
        } else when (effective) {
            Screen.INTRO -> IntroScreen(app) { screen = Screen.HUB }
            Screen.HUB -> HubScreen(
                app = app,
                live = live,
                onReplay = { r -> current = r; conceptMode = false; screen = Screen.REPLAY },
                onGo = { screen = it },
            )
            Screen.REPLAY -> ReplayScreen(run!!, concept = conceptMode) {
                screen = if (conceptMode) Screen.FUTURE else Screen.UNBOX
            }
            Screen.UNBOX -> UnboxScreen(app, run!!) {
                screen = if (run!!.blacks.isNotEmpty()) Screen.BLACK else Screen.HUB
            }
            Screen.BLACK -> BlackScreen(app, run!!, onRepair = { screen = Screen.DAMAGE }) { screen = Screen.HUB }
            Screen.GARAGE -> GarageScreen(app) { screen = Screen.HUB }
            Screen.LEAGUE -> LeagueScreen(app) { screen = Screen.HUB }
            Screen.COUPONS -> CouponScreen(app) { screen = Screen.HUB }
            Screen.DIAG -> DiagScreen(app, live) { screen = Screen.HUB }
            Screen.QUESTS -> QuestScreen(app) { screen = Screen.HUB }
            Screen.MBTI -> MbtiScreen(app) { screen = Screen.HUB }
            Screen.DAMAGE -> DamageScreen(app) { screen = Screen.HUB }
            Screen.FUTURE -> FutureLabScreen(
                app = app,
                onConceptReplay = { r -> current = r; conceptMode = true; screen = Screen.REPLAY },
                onBack = { screen = Screen.HUB },
            )
        }
    }
}
