package com.karmadrive.incar.run

import com.karmadrive.incar.data.KarmaStore
import com.karmadrive.incar.engine.JudgeEngine
import com.karmadrive.incar.engine.KarmaEvent
import com.karmadrive.incar.engine.KarmaType
import com.karmadrive.incar.engine.PathPoint
import com.karmadrive.incar.engine.RoadContext
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.signal.Gear
import com.karmadrive.incar.signal.ReplaySource
import com.karmadrive.incar.signal.Scenario
import com.karmadrive.incar.signal.SignalEvent
import com.karmadrive.incar.signal.SignalStatus
import com.karmadrive.incar.signal.Turn
import com.karmadrive.incar.signal.VehicleSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** 실시간 차량 상태 (신호 점검 · 개발 로그 화면용) */
data class LiveState(
    val gear: Gear = Gear.P,
    val speedKmh: Float = 0f,
    val turn: Turn = Turn.OFF,
    val steeringDeg: Float = 0f,
    val belt: Boolean? = null,
    val wiper: Boolean = false,
    val brake: Boolean = false,
    val accelG: Float = 0f,
    val running: Boolean = false,
    val replaying: Boolean = false,
    val replayTitle: String = "",
    val runKp: Int = 0,
    val runDamage: Int = 0,
    val combo: Int = 0,
    val distanceKm: Double = 0.0,
    val lat: Double? = null,
    val lon: Double? = null,
    val road: RoadContext? = null,
    val log: List<String> = emptyList(),
    /** 이번 RUN에서 지금까지 판정된 이벤트 (라이브 트윈 공유용 — 운전자 화면에는 표시하지 않음) */
    val runEvents: List<KarmaEvent> = emptyList(),
    val runStartMs: Long = 0L,
)

/**
 * 주행 1회(RUN) 기록기. 기어 D/R → RUN 시작, 기어 P → RUN 종료 · 요약 · 저장.
 * 신호는 콜백으로만 받는다(폴링 없음). 차속이 변하지 않아 이벤트가 끊기는 구간만 1초 틱으로 보정.
 */
class RunRecorder(
    private val store: KarmaStore,
    /** 위치 → 도로 · 법규 맥락 (RoadDataHub.resolve). null이면 도로 판정 없음 */
    private val roadResolver: ((Double, Double, Float?) -> RoadContext?)? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live

    private val _pending = MutableStateFlow<RunSummary?>(null)
    /** 주차(P) 순간 생성된 RUN 요약 — UI가 받아 리플레이 → 언박싱으로 이동한 뒤 consumePending() */
    val pending: StateFlow<RunSummary?> = _pending

    fun consumePending() {
        _pending.value = null
    }

    private val liveSources = mutableListOf<VehicleSignalSource>()
    private var tickStarted = false
    private var vhalSpeedSeen = false
    private var replay: ReplaySource? = null
    private var replayScenario: Scenario? = null

    private var engine: JudgeEngine? = null
    private var runStart = 0L
    private var runStartLat: Double? = null
    private var runStartLon: Double? = null
    private val events = mutableListOf<KarmaEvent>()
    private val path = mutableListOf<PathPoint>()
    private var px = 0f
    private var py = 0f
    private var heading = (-Math.PI / 2) // 화면 위쪽으로 출발
    private var sinceLastPoint = 0f
    private var lastSpeedT = -1L
    private var lastSpeed = 0f
    private var steering = 0f
    private val log = ArrayDeque<String>()

    fun attachLiveSource(source: VehicleSignalSource) {
        liveSources += source
        source.start { e -> onLive(e) }
        if (tickStarted) return
        tickStarted = true
        scope.launch {
            // 차속 이벤트가 '값이 바뀔 때만' 오는 경우를 대비한 1초 보정 틱
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                if (replay == null && engine != null && lastSpeedT > 0 && now - lastSpeedT > 900) {
                    onEvent(SignalEvent.Speed(now, lastSpeed), fromTick = true)
                }
            }
        }
    }

    fun statuses(): List<SignalStatus> = liveSources.flatMap { it.status() } + (replay?.status() ?: emptyList())

    fun isReplaying() = replay != null

    /** 데모 주행 재생 (실제 RUN이 진행 중이면 거부) */
    fun startScenario(s: Scenario, speedUp: Float = s.speedUp): Boolean {
        synchronized(this) {
            if (engine != null || replay != null) return false
            replayScenario = s
            val r = ReplaySource(s, speedUp) { onReplayFinished() }
            replay = r
            _live.value = _live.value.copy(replaying = true, replayTitle = s.title)
            addLog("▶ 시나리오 재생: ${s.title} (${speedUp.toInt()}배속)")
            r.start { e -> onEvent(e) }
            return true
        }
    }

    fun stopScenario() {
        synchronized(this) {
            replay?.stop()
            replay = null
            engine = null
            _live.value = _live.value.copy(replaying = false, running = false, gear = Gear.P, speedKmh = 0f)
            addLog("■ 시나리오 중단")
        }
    }

    private fun onReplayFinished() {
        synchronized(this) {
            replay = null
            _live.value = _live.value.copy(replaying = false)
        }
    }

    private fun onLive(e: SignalEvent) {
        if (replay != null) return // 시나리오 재생 중에는 실제 신호를 섞지 않음
        if (e is SignalEvent.Speed) vhalSpeedSeen = true
        onEvent(e)
        if (e is SignalEvent.Location) {
            // 차속 신호(VHAL)가 없으면 GPS 속도로 대체
            if (!vhalSpeedSeen && e.gpsSpeedKmh != null) onEvent(SignalEvent.Speed(e.t, e.gpsSpeedKmh))
            val resolver = roadResolver ?: return
            val ctx = try { resolver(e.lat, e.lon, e.bearing) } catch (ex: Exception) { null }
            onEvent(SignalEvent.RoadInfo(e.t, ctx))
        }
    }

    private var loggedSpeed = -100f
    private var loggedSteer = -1000f
    private var loggedRoad = ""

    private fun shouldLog(e: SignalEvent): Boolean = when (e) {
        is SignalEvent.Speed -> if (kotlin.math.abs(e.kmh - loggedSpeed) >= 10f || (e.kmh < 1f && loggedSpeed >= 1f)) {
            loggedSpeed = e.kmh; true
        } else false
        is SignalEvent.Steering -> if (kotlin.math.abs(e.deg - loggedSteer) >= 10f) {
            loggedSteer = e.deg; true
        } else false
        is SignalEvent.Location -> false
        is SignalEvent.RoadInfo -> {
            val key = e.ctx?.let { "${it.limitKmh}|${it.schoolZone}|${it.incident}|${it.signal?.phase}|${it.signal?.intersectionName}" } ?: "none"
            if (key != loggedRoad) { loggedRoad = key; true } else false
        }
        else -> true
    }

    private fun onEvent(e: SignalEvent, fromTick: Boolean = false) {
        synchronized(this) { handleEvent(e, fromTick) }
    }

    private fun handleEvent(e: SignalEvent, fromTick: Boolean) {
        if (!fromTick && shouldLog(e)) addLog(e.label())
        updateLive(e)

        // RUN 시작
        if (e is SignalEvent.GearChanged && (e.gear == Gear.D || e.gear == Gear.R) && engine == null) {
            startRun(e.t)
        }

        val eng = engine
        if (eng != null) {
            if (e is SignalEvent.Speed) advancePath(e)
            if (e is SignalEvent.Steering) steering = e.deg
            val out = eng.process(e)
            out.forEach { k ->
                val placed = k.copy(x = px, y = py)
                events += placed
                if (k.type == KarmaType.WARNING) store.markWarningUsed()
                // 흑역사는 그 순간 트윈카에 상처로 반영 → 동승자 · 가족 화면에 실시간 공개
                if (k.type.kind == com.karmadrive.incar.engine.Kind.BLACK) store.applyLiveBlack(placed)
                addLog("${k.type.emoji} ${k.type.title} ${if (k.kp >= 0) "+" else ""}${k.kp}KP ${k.detail}")
            }
            _live.value = _live.value.copy(
                runKp = events.sumOf { it.kp }, runDamage = eng.damage, combo = eng.combo,
                distanceKm = eng.distanceKm, accelG = eng.lastAccelG, runEvents = events.toList(),
            )
        }

        // RUN 종료 (주차)
        if (e is SignalEvent.GearChanged && e.gear == Gear.P && eng != null) {
            eng.close(e.t).forEach { k ->
                val placed = k.copy(x = px, y = py)
                events += placed
                if (k.type.kind == com.karmadrive.incar.engine.Kind.BLACK) store.applyLiveBlack(placed)
                addLog("${k.type.emoji} ${k.type.title} ${if (k.kp >= 0) "+" else ""}${k.kp}KP ${k.detail}")
            }
            finishRun(eng, e.t)
            _live.value = _live.value.copy(runEvents = events.toList())
        }
    }

    private fun startRun(t: Long) {
        // 기어 D 이전에 들어온 안전벨트 상태를 엔진에 넘겨 '올 벨트 스타트'를 판정
        val eng = JudgeEngine(warningAvailable = store.isWarningAvailableToday(), goldenKm = store.goldenKmToday())
        runStartLat = _live.value.lat
        runStartLon = _live.value.lon
        _live.value.belt?.let { eng.process(SignalEvent.SeatBelt(t, it)) }
        eng.process(SignalEvent.Wiper(t, _live.value.wiper))
        eng.process(SignalEvent.TurnSignal(t, _live.value.turn))
        _live.value.road?.let { eng.process(SignalEvent.RoadInfo(t, it)) }
        engine = eng
        runStart = t
        events.clear()
        path.clear()
        px = 0f; py = 0f; heading = -Math.PI / 2; sinceLastPoint = 0f
        lastSpeedT = -1L
        path += PathPoint(0f, 0f)
        _live.value = _live.value.copy(running = true, runKp = 0, runDamage = 0, combo = 0, distanceKm = 0.0, runEvents = emptyList(), runStartMs = t)
        addLog("● RUN 시작")
    }

    private fun finishRun(eng: JudgeEngine, t: Long) {
        path += PathPoint(px, py)
        val id = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.KOREA).format(Date(runStart)) + "-" + (System.nanoTime() % 1000)
        val summary = eng.summarize(id, runStart, t, path.toList(), simulated = replay != null, events = events.toList())
            .copy(startLat = runStartLat, startLon = runStartLon)
        engine = null
        store.saveRun(summary)
        _live.value = _live.value.copy(running = false)
        addLog("■ RUN 종료 · ${summary.grade.label}등급 · ${summary.kp}KP")
        _pending.value = summary
    }

    /** 차속 · 조향으로 그리는 리플레이용 경로 (데드 레커닝, 시각화 전용) */
    private fun advancePath(e: SignalEvent.Speed) {
        if (lastSpeedT > 0 && e.t > lastSpeedT) {
            val dtS = (e.t - lastSpeedT) / 1000f
            val meters = (lastSpeed + e.kmh) / 2f / 3.6f * dtS
            heading += steering * meters * 0.002
            px += (cos(heading) * meters).toFloat()
            py += (sin(heading) * meters).toFloat()
            sinceLastPoint += meters
            if (sinceLastPoint >= 5f) {
                path += PathPoint(px, py)
                sinceLastPoint = 0f
            }
        }
        lastSpeedT = e.t
        lastSpeed = e.kmh
    }

    private fun updateLive(e: SignalEvent) {
        val s = _live.value
        _live.value = when (e) {
            is SignalEvent.GearChanged -> s.copy(gear = e.gear)
            is SignalEvent.Speed -> s.copy(speedKmh = e.kmh)
            is SignalEvent.TurnSignal -> s.copy(turn = e.state)
            is SignalEvent.Steering -> s.copy(steeringDeg = e.deg)
            is SignalEvent.SeatBelt -> s.copy(belt = e.buckled)
            is SignalEvent.Wiper -> s.copy(wiper = e.on)
            is SignalEvent.BrakePedal -> s.copy(brake = e.pressed)
            is SignalEvent.Location -> s.copy(lat = e.lat, lon = e.lon)
            is SignalEvent.RoadInfo -> s.copy(road = e.ctx)
            else -> s
        }
        if (e is SignalEvent.Speed && engine == null) {
            lastSpeedT = e.t; lastSpeed = e.kmh
        }
    }

    private fun addLog(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        log.addFirst("$ts  $line")
        while (log.size > 80) log.removeLast()
        _live.value = _live.value.copy(log = log.toList())
    }
}
