package com.karmadrive.incar.engine

import com.karmadrive.incar.signal.Gear
import com.karmadrive.incar.signal.Scenario
import com.karmadrive.incar.signal.Scenarios
import com.karmadrive.incar.signal.SignalEvent
import com.karmadrive.incar.signal.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeEngineTest {

    private fun runScenario(s: Scenario, warning: Boolean, golden: Double? = null): JudgeEngine {
        val e = JudgeEngine(warningAvailable = warning, goldenKm = golden)
        s.steps.forEach { e.process(it.make(it.atMs)) }
        e.close(s.durationMs)
        return e
    }

    private fun JudgeEngine.count(t: KarmaType) = events.count { it.type == t }

    @Test
    fun commuteScenario_isGradeS_withExpectedSkills() {
        val e = runScenario(Scenarios.commute, warning = true)
        assertEquals(1, e.count(KarmaType.ALL_BELT_START))
        assertEquals(4, e.count(KarmaType.SIGNAL_COMBO))
        assertEquals(1, e.count(KarmaType.PERFECT_STOP))
        assertEquals(1, e.count(KarmaType.BUTTER_DRIVING))
        assertEquals(1, e.count(KarmaType.RAIN_MASTER))
        assertEquals(1, e.count(KarmaType.DISTANCE))
        assertEquals(0, e.damage)
        assertEquals(100, e.mq)
        assertEquals(Grade.S, gradeOf(e.kp))
    }

    @Test
    fun goldenRoad_firesOnceAtDistance() {
        val e = runScenario(Scenarios.commute, warning = true, golden = 1.5)
        assertEquals(1, e.count(KarmaType.GOLDEN_ROAD))
    }

    @Test
    fun blackScenario_firstMinorIsWarningOnly() {
        val e = runScenario(Scenarios.blackDay, warning = true)
        assertEquals(1, e.count(KarmaType.WARNING))
        assertEquals(1, e.count(KarmaType.NO_BELT))
        assertEquals(2, e.count(KarmaType.SIGNAL_MISSING))
        assertEquals(1, e.count(KarmaType.RAGE_BRAKE))
        assertEquals(13, e.damage)
        assertEquals(Grade.D, gradeOf(e.kp))
    }

    @Test
    fun lawScenario_judgesRoadRules() {
        val e = runScenario(Scenarios.lawDemo, warning = true)
        assertEquals(1, e.count(KarmaType.STOP_LINE_MASTER))
        assertEquals(1, e.count(KarmaType.YELLOW_BRAKE))
        assertEquals(1, e.count(KarmaType.SCHOOL_ZONE_STEALTH))
        assertEquals(1, e.count(KarmaType.OVERSPEED_MINOR))
        assertEquals(1, e.count(KarmaType.SAFE_PASS))
        assertEquals(0, e.count(KarmaType.RED_LIGHT))
        assertEquals(5, e.damage)
    }

    @Test
    fun damageIsCappedAt25() {
        val e = JudgeEngine(warningAvailable = false)
        val red = RoadContext(50, "t", signal = SignalInfo("X", SignalPhase.RED, 5f))
        e.process(SignalEvent.GearChanged(0, Gear.D))
        e.process(SignalEvent.Speed(0, 30f))
        e.process(SignalEvent.RoadInfo(100, red))
        e.process(SignalEvent.RoadInfo(1000, red.copy(signal = SignalInfo("X", SignalPhase.RED, -3f))))
        e.process(SignalEvent.RoadInfo(2000, RoadContext(50, "t")))
        e.process(SignalEvent.RoadInfo(3000, red.copy(signal = SignalInfo("Y", SignalPhase.RED, 5f))))
        e.process(SignalEvent.RoadInfo(4000, red.copy(signal = SignalInfo("Y", SignalPhase.RED, -3f))))
        assertEquals(2, e.count(KarmaType.RED_LIGHT))
        assertEquals(25, e.damage)
    }

    @Test
    fun ecoHold_after30sStop() {
        val e = JudgeEngine(warningAvailable = false)
        e.process(SignalEvent.GearChanged(0, Gear.D))
        e.process(SignalEvent.Speed(0, 0f))
        e.process(SignalEvent.Speed(45_000, 0f))
        e.process(SignalEvent.Speed(46_000, 10f))
        assertEquals(1, e.count(KarmaType.ECO_HOLD))
    }

    @Test
    fun turnWithoutSignal_breaksCombo() {
        val e = JudgeEngine(warningAvailable = false)
        var t = 0L
        e.process(SignalEvent.GearChanged(t, Gear.D))
        e.process(SignalEvent.Speed(t, 40f))
        t += 1000; e.process(SignalEvent.TurnSignal(t, Turn.LEFT))
        t += 500; e.process(SignalEvent.Steering(t, -20f))
        t += 500; e.process(SignalEvent.Steering(t, 0f))
        t += 500; e.process(SignalEvent.TurnSignal(t, Turn.OFF))
        t += 5000; e.process(SignalEvent.Steering(t, 25f))
        assertEquals(0, e.combo)
        assertEquals(KarmaType.SIGNAL_MISSING, e.events.last().type)
    }

    @Test
    fun schoolZone_violationWhenOver30() {
        val e = JudgeEngine(warningAvailable = false)
        val zone = RoadContext(30, "어린이보호구역", schoolZone = SchoolZone("A"))
        e.process(SignalEvent.GearChanged(0, Gear.D))
        e.process(SignalEvent.RoadInfo(0, zone))
        e.process(SignalEvent.Speed(0, 25f))
        e.process(SignalEvent.Speed(1000, 42f))
        e.process(SignalEvent.RoadInfo(2000, RoadContext(50, "기본값")))
        assertEquals(KarmaType.SCHOOL_ZONE_VIOLATION, e.events.last().type)
    }

    @Test
    fun wreckScenario_leavesScarsOnEveryPart() {
        val e = runScenario(Scenarios.wreck, warning = true)
        assertEquals(1, e.count(KarmaType.SCHOOL_ZONE_VIOLATION))
        assertEquals(1, e.count(KarmaType.RIGHT_TURN_PASS))
        assertEquals(1, e.count(KarmaType.RED_LIGHT))
        assertEquals(1, e.count(KarmaType.OVERSPEED_SCANDAL))
        assertEquals(1, e.count(KarmaType.SIGNAL_MISSING))
        assertEquals(25, e.damage)
        assertEquals(Grade.D, gradeOf(e.kp))
        // 모든 흑역사가 트윈카 부위 상처로 바뀐다
        val scars = e.events.mapNotNull { com.karmadrive.incar.game.Scars.fromEvent(it) }
        assertEquals(5, scars.map { it.part }.toSet().size)
        assertTrue(scars.first().reason.contains("시속 30"))
    }

    @Test
    fun rightTurnAtCrosswalk_withStop_isPerfect_withoutStop_isPassing() {
        fun run(stop: Boolean): JudgeEngine {
            val e = JudgeEngine(warningAvailable = false)
            val cw = RoadContext(50, "t", crosswalk = "A 횡단보도")
            var t = 0L
            e.process(SignalEvent.GearChanged(t, Gear.D))
            e.process(SignalEvent.RoadInfo(t, cw))
            e.process(SignalEvent.TurnSignal(t, Turn.RIGHT))
            e.process(SignalEvent.Speed(t, 20f))
            if (stop) {
                t += 1000; e.process(SignalEvent.Speed(t, 0f))
                t += 1500; e.process(SignalEvent.Speed(t, 0f))
                t += 1000; e.process(SignalEvent.Speed(t, 8f))
            }
            t += 500; e.process(SignalEvent.Speed(t, if (stop) 8f else 20f))
            t += 500; e.process(SignalEvent.Steering(t, 30f))
            return e
        }
        assertEquals(1, run(true).count(KarmaType.PERFECT_STOP))
        assertEquals(0, run(true).count(KarmaType.RIGHT_TURN_PASS))
        assertEquals(1, run(false).count(KarmaType.RIGHT_TURN_PASS))
    }

    @Test
    fun healRemovesLightScarsFirst() {
        val e = runScenario(Scenarios.wreck, warning = true)
        val scars = e.events.mapNotNull { com.karmadrive.incar.game.Scars.fromEvent(it) }
        val healed = com.karmadrive.incar.game.Scars.heal(scars, 5)
        assertTrue(healed.any { it.type == KarmaType.SCHOOL_ZONE_VIOLATION })
        assertTrue(healed.size < scars.size)
    }
}
