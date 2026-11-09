package com.karmadrive.incar

import android.app.Application
import com.karmadrive.incar.data.KarmaStore
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.game.Lines
import com.karmadrive.incar.game.TwinMood
import com.karmadrive.incar.game.moodOf
import com.karmadrive.incar.location.GpsSource
import com.karmadrive.incar.road.RoadDataHub
import com.karmadrive.incar.run.RunRecorder
import com.karmadrive.incar.share.LiveShareServer
import com.karmadrive.incar.signal.CarApiSource
import com.karmadrive.incar.state.DrivingStateMonitor
import com.karmadrive.incar.voice.TwinVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class KarmaApp : Application() {
    lateinit var store: KarmaStore
        private set
    lateinit var recorder: RunRecorder
        private set
    lateinit var voice: TwinVoice
        private set
    lateinit var driving: DrivingStateMonitor
        private set
    lateinit var roads: RoadDataHub
        private set
    /** 라이브 트윈 공유 (동승자 · 가족 화면) */
    lateinit var share: LiveShareServer
        private set

    /** 개발용: 주행 중 화면에 판정 로그 표시 (기본 꺼짐 — 정책상 주행 중 화면 0) */
    val devLogWhileDriving = MutableStateFlow(false)

    val carApiAvailable: Boolean by lazy { CarApiSource.isCarApiAvailable() }

    private var liveStarted = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = KarmaStore(this)
        roads = RoadDataHub(this)
        recorder = RunRecorder(store) { lat, lon, bearing -> roads.resolve(lat, lon, bearing) }
        voice = TwinVoice(this)
        driving = DrivingStateMonitor(this)
        share = LiveShareServer(assets, store, { recorder.live.value }, { twinMood() }, { twinLine() })
        share.start()
        // 주행 중일 때만 ITS 실시간 정보를 부르도록 RUN 상태를 도로 데이터에 전달
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            recorder.live.collect { roads.running = it.running }
        }
    }

    /** 권한 응답 이후 1회 호출 */
    fun startLive() {
        if (liveStarted) return
        liveStarted = true
        roads.loadAll()
        recorder.attachLiveSource(GpsSource(this))
        if (carApiAvailable) {
            recorder.attachLiveSource(CarApiSource(this))
            driving.start()
        }
        // Pleos Vehicle SDK: 방향지시등 · 조향각 · 안전벨트 · 와이퍼 · 브레이크 · 전조등
        if (com.karmadrive.incar.signal.PleosVehicleSource.isSdkPresent()) {
            recorder.attachLiveSource(com.karmadrive.incar.signal.PleosVehicleSource(this))
        }
    }

    /** IC09 · 음성 요약 문장 */
    fun todayKarmaSentence(): String {
        val runs = store.todayRuns()
        if (runs.isEmpty()) return "오늘은 아직 주행 기록이 없어요. 총 ${store.totalKp.value} 카르마 포인트를 모았어요."
        val kp = runs.sumOf { it.kp }
        val best = runs.maxByOrNull { it.kp }!!
        val skills = runs.flatMap { it.skills }.groupingBy { it.type.title }.eachCount()
        val top = skills.maxByOrNull { it.value }?.let { "${it.key} ${it.value}번" } ?: "스킬 없음"
        return "오늘 ${runs.size}번 주행해서 ${kp} 카르마 포인트, 최고 ${best.grade.label} 등급이에요. 가장 많이 한 건 $top."
    }

    /** 지금 트윈카의 기분 */
    fun twinMood(): TwinMood {
        val g = store.game.value
        return moodOf(store.runs.value.firstOrNull(), g.damage, store.streakDays(), g.missionDoneDay == KarmaStore.today())
    }

    /** 트윈카 한마디 (무드 × 성격) */
    fun twinLine(mood: TwinMood = twinMood()): String =
        Lines.of(mood, store.game.value.personality, KarmaStore.today().hashCode() + store.runs.value.size)

    fun unboxSentence(run: RunSummary): String {
        val skills = run.skills.groupingBy { it.type.title }.eachCount().entries
            .sortedByDescending { it.value }.take(2).joinToString(", ") { "${it.key} ${it.value}번" }
        val head = "${run.grade.label} 등급! ${run.kp} 카르마 포인트."
        return if (skills.isNotEmpty()) "$head $skills, 멋졌어요." else "$head ${run.grade.line}"
    }

    companion object {
        lateinit var instance: KarmaApp
            private set
    }
}
