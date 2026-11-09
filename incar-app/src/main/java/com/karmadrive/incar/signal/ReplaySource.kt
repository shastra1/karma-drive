package com.karmadrive.incar.signal

import com.karmadrive.incar.engine.Incident
import com.karmadrive.incar.engine.RoadContext
import com.karmadrive.incar.engine.SchoolZone
import com.karmadrive.incar.engine.SignalInfo
import com.karmadrive.incar.engine.SignalPhase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 시나리오 한 줄: 시작 기준 경과 ms + 이벤트 생성기(실제 타임스탬프를 받아 이벤트 생성) */
class ScenarioStep(val atMs: Long, val make: (Long) -> SignalEvent)

class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<ScenarioStep>,
    /** 재생 배속 — 라이브 공유 시연용 시나리오는 천천히 */
    val speedUp: Float = 8f,
) {
    val durationMs: Long get() = steps.lastOrNull()?.atMs ?: 0L
}

/**
 * 녹화 · 각본 주행 로그를 재생하는 소스 (시연 · 테스트용).
 * 이벤트 타임스탬프는 "실제 주행 시간" 기준으로 찍고, 재생만 [speedUp]배 빠르게 합니다.
 */
class ReplaySource(
    private val scenario: Scenario,
    private val speedUp: Float = 8f,
    private val onFinished: () -> Unit = {},
) : VehicleSignalSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun start(listener: (SignalEvent) -> Unit) {
        val base = System.currentTimeMillis()
        job = scope.launch {
            var last = 0L
            for (step in scenario.steps) {
                val wait = ((step.atMs - last) / speedUp).toLong()
                if (wait > 0) delay(wait)
                last = step.atMs
                listener(step.make(base + step.atMs))
            }
            onFinished()
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    override fun status(): List<SignalStatus> =
        listOf(SignalStatus("시나리오 재생", true, scenario.title))
}

/** 0.5초 간격으로 속도 · 조향 샘플을 찍어 주는 각본 작성기 */
class ScenarioBuilder {
    private val steps = mutableListOf<ScenarioStep>()
    private var t = 0L
    private var speed = 0f
    private var steer = 0f

    /** 출발 후 이동 거리(m) · 경과 시간(ms) */
    var meters = 0.0
        private set
    val nowMs: Long get() = t

    /** 매 샘플마다 도로 맥락을 만들어 주는 함수 (null이면 도로 정보 없음) */
    private var roadFn: ((Double, Long) -> RoadContext?)? = null

    fun road(fn: ((Double, Long) -> RoadContext?)?) {
        roadFn = fn
        val ctx = fn?.invoke(meters, t)
        steps += ScenarioStep(t) { ts -> SignalEvent.RoadInfo(ts, ctx) }
    }

    fun event(make: (Long) -> SignalEvent) {
        steps += ScenarioStep(t, make)
    }

    fun gear(g: Gear) = event { SignalEvent.GearChanged(it, g) }
    fun turn(s: Turn) = event { SignalEvent.TurnSignal(it, s) }
    fun belt(b: Boolean) = event { SignalEvent.SeatBelt(it, b) }
    fun wiper(on: Boolean) = event { SignalEvent.Wiper(it, on) }
    fun brake(p: Boolean) = event { SignalEvent.BrakePedal(it, p) }

    /** [ms] 동안 속도 · 조향을 목표값까지 선형으로 바꾸며 0.5초마다 샘플 */
    fun ramp(toKmh: Float = speed, toSteer: Float = steer, ms: Long) {
        val n = (ms / STEP).coerceAtLeast(1)
        val s0 = speed
        val a0 = steer
        for (i in 1..n) {
            t += STEP
            val f = i.toFloat() / n
            val v = s0 + (toKmh - s0) * f
            val a = a0 + (toSteer - a0) * f
            val prevV = s0 + (toKmh - s0) * (i - 1).toFloat() / n
            meters += (prevV + v) / 2.0 / 3.6 * STEP / 1000.0
            steps += ScenarioStep(t) { ts -> SignalEvent.Speed(ts, v) }
            if (a != a0 || toSteer != a0) steps += ScenarioStep(t) { ts -> SignalEvent.Steering(ts, a) }
            roadFn?.let { fn ->
                val ctx = fn(meters, t)
                steps += ScenarioStep(t) { ts -> SignalEvent.RoadInfo(ts, ctx) }
            }
        }
        speed = toKmh
        steer = toSteer
    }

    fun hold(ms: Long) = ramp(speed, steer, ms)

    fun pause(ms: Long) {
        t += ms
    }

    fun build(id: String, title: String, description: String, speedUp: Float = 8f) =
        Scenario(id, title, description, steps.toList(), speedUp)

    companion object {
        private const val STEP = 500L
    }
}

object Scenarios {

    /** 출근길 S등급: 올 벨트 · 깜빡이 콤보 · 퍼펙트 스톱 · 버터 · 레인 마스터 */
    val commute: Scenario = ScenarioBuilder().apply {
        belt(true); turn(Turn.OFF); wiper(false); gear(Gear.P)
        pause(1000)
        gear(Gear.D)
        ramp(40f, ms = 15_000)
        hold(30_000)
        laneChange(Turn.LEFT)
        hold(15_000)
        laneChange(Turn.RIGHT)
        hold(20_000)
        laneChange(Turn.LEFT)
        hold(30_000)
        wiper(true)
        hold(60_000)
        // 우회전 퍼펙트 스톱: 깜빡이 → 감속 → 정지 2초 → 회전
        turn(Turn.RIGHT)
        brake(true)
        ramp(0f, ms = 10_000)
        hold(2_000)
        brake(false)
        ramp(15f, 35f, 4_000)
        ramp(20f, 0f, 2_000)
        turn(Turn.OFF)
        ramp(40f, ms = 12_000)
        hold(50_000)
        brake(true)
        ramp(0f, ms = 12_000)
        wiper(false)
        pause(1000)
        gear(Gear.P)
    }.build("commute", "출근길 S등급", "올 벨트 · 깜빡이 콤보 · 퍼펙트 스톱 · 빗길 버터 드라이빙")

    /** 흑역사: 벨트 미착용 · 급가속 · 깜빡이 실종 · 급정거 */
    val blackDay: Scenario = ScenarioBuilder().apply {
        belt(false); turn(Turn.OFF); wiper(false); gear(Gear.P)
        pause(1000)
        gear(Gear.D)
        ramp(50f, ms = 3_000)
        hold(20_000)
        ramp(toSteer = 22f, ms = 1_000)
        ramp(toSteer = 0f, ms = 1_000)
        hold(10_000)
        ramp(toSteer = -24f, ms = 1_000)
        ramp(toSteer = 0f, ms = 1_000)
        hold(10_000)
        brake(true)
        ramp(0f, ms = 2_500)
        hold(2_000)
        brake(false)
        pause(1000)
        gear(Gear.P)
    }.build("black", "흑역사 주행", "급가속 · 깜빡이 실종 · 급정거")

    private val urban = RoadContext(50, "기본값 · 안전속도 5030", roadName = "테헤란로(데모)")

    /** 법규 데모 (도심): 정지선 · 스쿨존 · 과속 · 돌발 구간. 도로 데이터는 각본(오프라인) */
    val lawDemo: Scenario = ScenarioBuilder().apply {
        belt(true); turn(Turn.OFF); wiper(false); gear(Gear.P)
        pause(1000)
        road { _, _ -> urban }
        gear(Gear.D)
        ramp(45f, ms = 15_000)
        hold(40_000)

        // ① 적색 신호 → 정지선 앞 정차 → 녹색에 출발
        val stopLine = meters + 85.0
        val greenAt = nowMs + 17_000
        road { m, t ->
            val d = (stopLine - m).toFloat()
            if (d < -40f) urban
            else urban.copy(signal = SignalInfo("역삼역 교차로", if (t < greenAt) SignalPhase.RED else SignalPhase.GREEN, d))
        }
        ramp(0f, ms = 12_000)
        hold(6_000)
        ramp(40f, ms = 10_000)
        hold(8_000)
        road { _, _ -> urban }

        // ② 어린이보호구역 300m — 30km/h 이하 유지
        ramp(28f, ms = 6_000)
        val zoneStart = meters
        road { m, _ ->
            if (m - zoneStart < 300.0) urban.copy(limitKmh = 30, limitSource = "어린이보호구역", schoolZone = SchoolZone("역삼초등학교(데모)"))
            else urban
        }
        hold(45_000)
        road { _, _ -> urban }

        // ③ 과속 스캔들(소): 제한 50 구간에서 65km/h 8초
        ramp(50f, ms = 5_000)
        ramp(65f, ms = 6_000)
        hold(8_000)
        ramp(48f, ms = 8_000)

        // ④ 돌발 구간(공사) 200m — 서행 통과
        ramp(35f, ms = 6_000)
        val workStart = meters
        road { m, _ ->
            if (m - workStart < 200.0) urban.copy(incident = Incident("공사 · 2차로 통제", "국가교통정보센터 돌발정보(데모)"))
            else urban
        }
        hold(25_000)
        road { _, _ -> urban }
        hold(10_000)

        // ⑤ 옐로 브레이크: 황색불에 무리하지 않고 정지선 앞 정차
        val yLine = meters + 50.0
        val yUntil = nowMs + 9_000
        val greenAgain = nowMs + 16_000
        road { m, t ->
            val d = (yLine - m).toFloat()
            val phase = when {
                t < yUntil -> SignalPhase.YELLOW
                t < greenAgain -> SignalPhase.RED
                else -> SignalPhase.GREEN
            }
            if (d < -40f) urban else urban.copy(signal = SignalInfo("선릉역 교차로", phase, d))
        }
        ramp(0f, ms = 8_000)
        hold(8_000)
        ramp(30f, ms = 8_000)
        hold(6_000)
        road { _, _ -> urban }

        brake(true)
        ramp(0f, ms = 10_000)
        pause(1000)
        road(null)
        gear(Gear.P)
    }.build("law", "법규 데모 (도심)", "정지선 · 스쿨존 · 과속 · 돌발 구간 · 옐로 브레이크 (각본 도로 데이터)")

    private val nonhyeon = RoadContext(50, "무인교통단속카메라 데이터(데모)", roadName = "논현로(데모)")

    /**
     * 트윈카 수난 시대 — 법규 위반이 트윈카 부위에 하나씩 새겨지는 과정을 라이브로 보여 주는 시연용.
     * 스쿨존 침공(보닛 · 연기) → 우회전 패싱(펜더) → 레드카드(헤드램프) → 과속 스캔들(대)(유리) → 깜빡이 실종(도어).
     * 1회 주행 데미지 상한 25는 그대로 — 상한 이후 위반은 상처(사유)만 남고 게이지는 오르지 않는다.
     */
    val wreck: Scenario = ScenarioBuilder().apply {
        belt(true); turn(Turn.OFF); wiper(false); gear(Gear.P)
        pause(1000)
        road { _, _ -> nonhyeon }
        gear(Gear.D)
        ramp(45f, ms = 8_000)
        hold(10_000)

        // ① 스쿨존 침공: 30 구간을 45km/h로 200m
        val zoneStart = meters
        road { m, _ ->
            if (m - zoneStart < 200.0) nonhyeon.copy(limitKmh = 30, limitSource = "어린이보호구역", schoolZone = SchoolZone("논현초등학교 앞(데모)"))
            else nonhyeon
        }
        hold(20_000)
        road { _, _ -> nonhyeon }
        hold(6_000)

        // ② 우회전 패싱: 깜빡이는 켰지만 횡단보도 앞에서 서지 않고 20km/h로 회전
        turn(Turn.RIGHT)
        road { _, _ -> nonhyeon.copy(crosswalk = "학동사거리 횡단보도") }
        ramp(20f, ms = 5_000)
        ramp(toSteer = 35f, ms = 1_500)
        ramp(toSteer = 0f, ms = 1_500)
        turn(Turn.OFF)
        road { _, _ -> nonhyeon }
        ramp(40f, ms = 6_000)
        hold(6_000)

        // ③ 레드카드: 적색 신호에 정지선 통과
        val line = meters + 60.0
        road { m, _ ->
            val d = (line - m).toFloat()
            if (d < -40f) nonhyeon else nonhyeon.copy(signal = SignalInfo("학동역 교차로", SignalPhase.RED, d))
        }
        hold(12_000)
        road { _, _ -> nonhyeon }

        // ④ 과속 스캔들(대): 제한 50에서 78km/h 8초
        ramp(78f, ms = 6_000)
        hold(8_000)
        ramp(50f, ms = 8_000)

        // ⑤ 깜빡이 실종: 방향지시등 없이 차선 변경
        hold(5_000)
        ramp(toSteer = -22f, ms = 1_000)
        ramp(toSteer = 0f, ms = 1_000)
        hold(5_000)

        brake(true)
        ramp(0f, ms = 8_000)
        pause(1000)
        road(null)
        gear(Gear.P)
    }.build("wreck", "트윈카 수난 시대 (라이브 공유)", "스쿨존 · 우회전 · 신호 · 과속 · 깜빡이 위반이 부위별 상처로 — 폰 · PC에서 실시간 관람", speedUp = 3f)

    val all = listOf(commute, blackDay, lawDemo, wreck)
}

private fun ScenarioBuilder.laneChange(dir: Turn) {
    turn(dir)
    hold(1_500)
    ramp(toSteer = if (dir == Turn.LEFT) -18f else 18f, ms = 1_000)
    ramp(toSteer = 0f, ms = 1_000)
    turn(Turn.OFF)
}
