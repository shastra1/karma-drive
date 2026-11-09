package com.karmadrive.incar.engine

import com.karmadrive.incar.signal.Gear
import com.karmadrive.incar.signal.SignalEvent
import com.karmadrive.incar.signal.Turn
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 매너 스킬 · 흑역사 판정 엔진 v1.
 * 순수 Kotlin(안드로이드 의존성 없음) — 이벤트를 시간순으로 넣으면 판정 결과를 돌려준다.
 *
 * @param warningAvailable 오늘 첫 경미 흑역사를 경고로 바꿔 줄 수 있는지 (하루 1회)
 */
class JudgeEngine(
    private val rules: Rules = Rules(),
    warningAvailable: Boolean = true,
    /** 오늘의 골든 로드 — 출발 후 이 거리(km)를 지나면 보너스 (주행 중엔 비밀) */
    private val goldenKm: Double? = null,
) {
    private var warningLeft = warningAvailable
    val warningUsed: Boolean get() = !warningLeft && warningInitial
    private val warningInitial = warningAvailable

    private val _events = mutableListOf<KarmaEvent>()
    val events: List<KarmaEvent> get() = _events

    var damage = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var distanceKm = 0.0
        private set
    var speedKmh = 0f
        private set
    var gear = Gear.P
        private set

    private var lastSpeedT = -1L
    private val samples = ArrayDeque<Pair<Long, Float>>()
    var lastAccelG = 0f
        private set

    private var turn = Turn.OFF
    private var lastTurnDir = Turn.OFF
    private var turnOffAt = -1L
    private var inManeuver = false

    private var belt: Boolean? = null
    private var beltChecked = false
    private var wiperOn = false

    private var stopSince = -1L
    private var perfectArmed = false

    private var bucketStartKm = 0.0
    private var bucketMaxG = 0f
    private var bucketWiperMs = 0L
    private var bucketTotalMs = 0L

    private var lastRageT = Long.MIN_VALUE / 2

    // 에코 홀드 · 공회전 · 노벨트 · 골든 로드
    private var ecoStopSince = -1L
    private var ecoCount = 0
    private var idleFired = false
    private var noBeltSince = -1L
    private var noBeltFired = false
    private var goldenFired = false

    // 법규 · 도로 상태
    var road: RoadContext? = null
        private set
    private var over10Since = -1L
    private var over20Since = -1L
    private var overMinorFired = false
    private var overScandalFired = false
    private var bucketOver = false
    private var bucketHadRoad = false
    private var schoolMax = 0f
    private var incidentMax = 0f
    private var incidentRage = false
    private var prevStopDist: Float? = null
    private var approach: String? = null
    private var stopLineAwarded = false
    private var stopLineOverFired = false

    val kp: Int get() = _events.sumOf { it.kp }

    fun process(e: SignalEvent): List<KarmaEvent> {
        val out = mutableListOf<KarmaEvent>()
        when (e) {
            is SignalEvent.GearChanged -> onGear(e, out)
            is SignalEvent.Speed -> onSpeed(e, out)
            is SignalEvent.TurnSignal -> onTurn(e)
            is SignalEvent.Steering -> onSteering(e, out)
            is SignalEvent.SeatBelt -> belt = e.buckled
            is SignalEvent.Wiper -> wiperOn = e.on
            is SignalEvent.RoadInfo -> onRoad(e, out)
            is SignalEvent.BrakePedal, is SignalEvent.Headlights, is SignalEvent.Battery, is SignalEvent.Location -> Unit
        }
        _events += out
        return out
    }

    private fun onGear(e: SignalEvent.GearChanged, out: MutableList<KarmaEvent>) {
        val moving = e.gear == Gear.D || e.gear == Gear.R
        if (moving && !beltChecked) {
            beltChecked = true
            if (belt == true) out += KarmaEvent(KarmaType.ALL_BELT_START, e.t, rules.kpAllBelt, detail = "출발 전 안전벨트 착용")
        }
        gear = e.gear
    }

    private fun onSpeed(e: SignalEvent.Speed, out: MutableList<KarmaEvent>) {
        val v = e.kmh
        if (lastSpeedT >= 0 && e.t > lastSpeedT) {
            val dt = e.t - lastSpeedT
            distanceKm += (speedKmh + v) / 2.0 * dt / 3_600_000.0
            bucketTotalMs += dt
            if (wiperOn) bucketWiperMs += dt
        }
        lastSpeedT = e.t
        speedKmh = v

        // 가속도: 0.8~2초 전 샘플과 비교
        samples.addLast(e.t to v)
        while (samples.isNotEmpty() && e.t - samples.first().first > 2_000) samples.removeFirst()
        val ref = samples.lastOrNull { e.t - it.first >= 800 }
        if (ref != null) {
            val dtS = (e.t - ref.first) / 1000f
            val g = ((v - ref.second) / 3.6f / dtS) / 9.81f
            lastAccelG = g
            bucketMaxG = max(bucketMaxG, abs(g))
            if (e.t - lastRageT >= rules.rageCooldownMs) {
                if (g >= rules.rageAccelG) {
                    lastRageT = e.t
                    incidentRage = true
                    out += black(KarmaType.RAGE_ACCEL, e.t, rules.kpRage, rules.dmgRage, "가속 ${"%.2f".format(g)}g", minor = true)
                } else if (g <= -rules.rageBrakeG) {
                    lastRageT = e.t
                    incidentRage = true
                    out += black(KarmaType.RAGE_BRAKE, e.t, rules.kpRage, rules.dmgRage, "감속 ${"%.2f".format(-g)}g", minor = true)
                }
            }
        }

        checkOverspeed(e.t, v, out)
        checkEcoAndIdle(e.t, v, out)
        checkNoBelt(e.t, v, out)
        val gk = goldenKm
        if (gk != null && !goldenFired && distanceKm >= gk) {
            goldenFired = true
            out += KarmaEvent(KarmaType.GOLDEN_ROAD, e.t, rules.kpGolden, detail = "오늘의 숨은 보너스 구간을 지나갔어요")
        }
        if (road?.schoolZone != null) schoolMax = max(schoolMax, v)
        if (road?.incident != null) incidentMax = max(incidentMax, v)
        checkStopLine(e.t, out)

        // 정지 판정 (퍼펙트 스톱 준비)
        if (v <= rules.stopKmh) {
            if (stopSince < 0) stopSince = e.t
            if (e.t - stopSince >= rules.stopHoldMs && turn == Turn.RIGHT) perfectArmed = true
        } else {
            stopSince = -1
        }

        // 버터 드라이빙: 1km 구간 단위
        if (distanceKm - bucketStartKm >= rules.butterKm) {
            if (bucketMaxG < rules.butterMaxG) {
                val rainy = bucketTotalMs > 0 && bucketWiperMs * 2 >= bucketTotalMs
                if (rainy) {
                    out += KarmaEvent(
                        KarmaType.RAIN_MASTER, e.t, (rules.kpButter * rules.rainMultiplier).roundToInt(),
                        detail = "빗길 1km 부드럽게 · ${rules.rainMultiplier}배",
                    )
                } else {
                    out += KarmaEvent(KarmaType.BUTTER_DRIVING, e.t, rules.kpButter, detail = "1km 동안 최대 ${"%.2f".format(bucketMaxG)}g")
                }
            }
            if (bucketHadRoad && !bucketOver) {
                out += KarmaEvent(KarmaType.LIMIT_KEEPER, e.t, rules.kpLimitKeeper, detail = "1km 동안 제한속도 준수")
            }
            bucketOver = false
            bucketHadRoad = road != null
            bucketStartKm = distanceKm
            bucketMaxG = 0f
            bucketWiperMs = 0
            bucketTotalMs = 0
        }
    }

    private fun onTurn(e: SignalEvent.TurnSignal) {
        if (e.state != Turn.OFF) {
            lastTurnDir = e.state
        } else if (turn != Turn.OFF) {
            turnOffAt = e.t
        }
        if (e.state != Turn.RIGHT) perfectArmed = false
        turn = e.state
    }

    private fun onSteering(e: SignalEvent.Steering, out: MutableList<KarmaEvent>) {
        val a = abs(e.deg)
        if (!inManeuver && a >= rules.turnStartDeg && speedKmh >= rules.moveKmh) {
            inManeuver = true
            val signalOk = turn != Turn.OFF || (turnOffAt >= 0 && e.t - turnOffAt <= rules.signalGraceMs)
            if (signalOk) {
                combo += 1
                maxCombo = max(maxCombo, combo)
                val mult = min(1.0 + (combo - 1) * 0.25, rules.comboMaxMultiplier)
                out += KarmaEvent(KarmaType.SIGNAL_COMBO, e.t, (rules.kpComboBase * mult).roundToInt(), detail = "콤보 ×$combo · 배율 ${"%.2f".format(mult)}")
                val rightTurn = turn == Turn.RIGHT || lastTurnDir == Turn.RIGHT
                val cw = road?.crosswalk
                if (perfectArmed && rightTurn) {
                    out += KarmaEvent(KarmaType.PERFECT_STOP, e.t, rules.kpPerfectStop, detail = "우회전 전 완전 정지" + (cw?.let { " · $it" } ?: ""), place = road?.roadName ?: "")
                } else if (rightTurn && cw != null && speedKmh >= rules.rightTurnPassKmh) {
                    out += black(KarmaType.RIGHT_TURN_PASS, e.t, rules.kpRightTurnPass, rules.dmgRightTurnPass, "$cw · ${"%.0f".format(speedKmh)}km/h로 회전")
                    combo = 0
                }
                perfectArmed = false
            } else {
                combo = 0
                out += black(KarmaType.SIGNAL_MISSING, e.t, rules.kpMissing, rules.dmgMissing, "방향 전환 · 방향지시등 없음")
            }
        } else if (inManeuver && a < rules.turnEndDeg) {
            inManeuver = false
        }
    }

    private fun onRoad(e: SignalEvent.RoadInfo, out: MutableList<KarmaEvent>) {
        val prev = road
        val now = e.ctx
        road = now
        if (now != null) bucketHadRoad = true

        // 어린이보호구역 진입 · 이탈
        if (prev?.schoolZone == null && now?.schoolZone != null) schoolMax = speedKmh
        if (prev?.schoolZone != null && now?.schoolZone == null) judgeSchoolZone(e.t, prev.schoolZone, out)

        // 돌발 구간 진입 · 이탈
        if (prev?.incident == null && now?.incident != null) {
            incidentMax = speedKmh
            incidentRage = false
        }
        if (prev?.incident != null && now?.incident == null) judgeIncident(e.t, prev, out)

        // 신호 · 정지선
        val sig = now?.signal
        if (sig == null) {
            prevStopDist = null
            approach = null
        } else {
            if (approach != sig.intersectionName) {
                approach = sig.intersectionName
                stopLineAwarded = false
                stopLineOverFired = false
                prevStopDist = null
            }
            val d = sig.distToStopLineM
            val pd = prevStopDist
            if (pd != null && pd > 0f && d <= 0f && sig.phase == SignalPhase.RED && speedKmh > rules.crossMinKmh) {
                out += black(KarmaType.RED_LIGHT, e.t, rules.kpRedLight, rules.dmgRedLight, "${sig.intersectionName} · 적색 신호에 정지선 통과")
            }
            prevStopDist = d
            checkStopLine(e.t, out)
        }
    }

    private fun checkStopLine(t: Long, out: MutableList<KarmaEvent>) {
        val sig = road?.signal ?: return
        if (sig.phase == SignalPhase.GREEN || speedKmh > 1f) return
        val d = sig.distToStopLineM
        if (d > 0f && d <= rules.stopLineWindowM && !stopLineAwarded) {
            stopLineAwarded = true
            out += if (sig.phase == SignalPhase.YELLOW) {
                KarmaEvent(KarmaType.YELLOW_BRAKE, t, rules.kpYellowBrake, detail = "${sig.intersectionName} · 황색불에 무리하지 않고 정지")
            } else {
                KarmaEvent(KarmaType.STOP_LINE_MASTER, t, rules.kpStopLine, detail = "${sig.intersectionName} · 정지선 ${"%.0f".format(d)}m 앞 정차")
            }
        } else if (sig.phase == SignalPhase.RED && d <= 0f && d > -rules.stopLineOverM && !stopLineOverFired && !stopLineAwarded) {
            stopLineOverFired = true
            out += black(KarmaType.STOP_LINE_OVER, t, rules.kpStopLineOver, rules.dmgStopLineOver, "${sig.intersectionName} · 정지선을 넘어 정차", minor = true)
        }
    }

    /** 에코 홀드: 정차 30초~3분 후 출발 / 공회전 명상: 10분 넘게 정차 */
    private fun checkEcoAndIdle(t: Long, v: Float, out: MutableList<KarmaEvent>) {
        if (gear == Gear.P) {
            ecoStopSince = -1
            return
        }
        if (v <= rules.stopKmh) {
            if (ecoStopSince < 0) ecoStopSince = t
            if (!idleFired && t - ecoStopSince >= rules.idleBlackMs) {
                idleFired = true
                out += black(KarmaType.IDLE_MEDITATION, t, rules.kpIdle, rules.dmgIdle, "정차 중 공회전 10분 초과", minor = true)
            }
        } else {
            if (ecoStopSince >= 0) {
                val held = t - ecoStopSince
                if (held in rules.ecoMinMs..rules.ecoMaxMs && ecoCount < rules.ecoMaxPerRun) {
                    ecoCount++
                    out += KarmaEvent(KarmaType.ECO_HOLD, t, rules.kpEcoHold, detail = "정차 ${held / 1000}초 후 부드럽게 출발")
                }
            }
            ecoStopSince = -1
            idleFired = false
        }
    }

    /** 노벨트 챌린지: 운전석 벨트 해제 상태로 10km/h 넘게 10초 */
    private fun checkNoBelt(t: Long, v: Float, out: MutableList<KarmaEvent>) {
        if (noBeltFired || belt != false || v <= rules.noBeltKmh) {
            if (belt != false) noBeltSince = -1
            return
        }
        if (noBeltSince < 0) noBeltSince = t
        if (t - noBeltSince >= rules.noBeltHoldMs) {
            noBeltFired = true
            out += black(KarmaType.NO_BELT, t, rules.kpNoBelt, rules.dmgNoBelt, "안전벨트 미착용 주행")
        }
    }

    private fun checkOverspeed(t: Long, v: Float, out: MutableList<KarmaEvent>) {
        val r = road ?: return
        if (r.schoolZone != null) return // 스쿨존은 별도 판정
        val over = v - r.limitKmh
        if (over > 5f) bucketOver = true
        if (over <= 0f) {
            overMinorFired = false
            overScandalFired = false
        }
        if (over > rules.overScandalKmh) {
            if (over20Since < 0) over20Since = t
            if (t - over20Since >= rules.overHoldMs && !overScandalFired) {
                overScandalFired = true
                out += black(KarmaType.OVERSPEED_SCANDAL, t, rules.kpOverScandal, rules.dmgOverScandal, "제한 ${r.limitKmh}km/h 구간 ${"%.0f".format(v)}km/h (${r.limitSource})")
            }
        } else {
            over20Since = -1
        }
        if (over > rules.overMinorKmh) {
            if (over10Since < 0) over10Since = t
            if (t - over10Since >= rules.overHoldMs && !overMinorFired && !overScandalFired && over <= rules.overScandalKmh) {
                overMinorFired = true
                out += black(KarmaType.OVERSPEED_MINOR, t, rules.kpOverMinor, rules.dmgOverMinor, "제한 ${r.limitKmh}km/h 구간 ${"%.0f".format(v)}km/h (${r.limitSource})")
            }
        } else {
            over10Since = -1
        }
    }

    private fun judgeSchoolZone(t: Long, zone: SchoolZone, out: MutableList<KarmaEvent>) {
        if (schoolMax <= zone.limitKmh + rules.schoolToleranceKmh) {
            out += KarmaEvent(KarmaType.SCHOOL_ZONE_STEALTH, t, rules.kpSchoolStealth, detail = "${zone.name} · 최고 ${"%.0f".format(schoolMax)}km/h")
        } else {
            out += black(KarmaType.SCHOOL_ZONE_VIOLATION, t, rules.kpSchoolViolation, rules.dmgSchoolViolation, "${zone.name} · 제한 ${zone.limitKmh} / 최고 ${"%.0f".format(schoolMax)}km/h")
        }
    }

    private fun judgeIncident(t: Long, ctx: RoadContext, out: MutableList<KarmaEvent>) {
        val inc = ctx.incident ?: return
        if (!incidentRage && incidentMax <= ctx.limitKmh) {
            out += KarmaEvent(KarmaType.SAFE_PASS, t, rules.kpSafePass, detail = "${inc.title} 구간 · 최고 ${"%.0f".format(incidentMax)}km/h")
        }
    }

    /** 매너 지수 (이번 RUN) */
    val mq: Int get() = mqOf(damage)

    /** RUN 종료(P) 직전 호출 — 구간 안에서 끝난 판정을 마무리하고 거리 KP 지급 */
    fun close(t: Long): List<KarmaEvent> {
        val out = mutableListOf<KarmaEvent>()
        val r = road
        if (r?.schoolZone != null) judgeSchoolZone(t, r.schoolZone, out)
        if (r?.incident != null) judgeIncident(t, r, out)
        road = null
        val distKp = kotlin.math.floor(distanceKm * distanceMultiplier(mq)).toInt()
        if (distKp > 0) {
            out += KarmaEvent(KarmaType.DISTANCE, t, distKp, detail = "${"%.1f".format(distanceKm)}km × ${distanceMultiplier(mq)}배 (매너 지수 $mq)")
        }
        _events += out
        return out
    }

    private fun black(type: KarmaType, t: Long, kp: Int, dmg: Int, detail: String, minor: Boolean = false): KarmaEvent {
        if (minor && warningLeft) {
            warningLeft = false
            return KarmaEvent(KarmaType.WARNING, t, 0, 0, "${type.title} — 오늘 첫 번째라 경고만")
        }
        val applied = min(dmg, rules.damageCapPerRun - damage).coerceAtLeast(0)
        damage += applied
        return KarmaEvent(type, t, kp, applied, detail, place = road?.roadName ?: "")
    }

    fun summarize(runId: String, startMs: Long, endMs: Long, path: List<PathPoint>, simulated: Boolean, events: List<KarmaEvent> = this.events) =
        RunSummary(
            runId = runId,
            startMs = startMs,
            endMs = endMs,
            distanceKm = distanceKm,
            kp = events.sumOf { it.kp },
            damage = damage,
            maxCombo = maxCombo,
            grade = gradeOf(events.sumOf { it.kp }),
            events = events,
            path = path,
            simulated = simulated,
            mq = mq,
        )
}
