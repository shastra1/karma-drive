package com.karmadrive.incar.game

import com.karmadrive.incar.engine.KarmaEvent
import com.karmadrive.incar.engine.KarmaType

/** 트윈카 외형 (이미지 세트) — 부위 좌표가 모델마다 다르다 */
enum class CarModel { OLD, EV }

/**
 * 트윈카 부위. 좌표는 이미지 안의 비율(0~1) — [old]는 연식 오래된 해치백, [ev]는 EV SUV 일러스트 기준.
 */
enum class CarPart(val title: String, val old: Pair<Float, Float>, val ev: Pair<Float, Float>) {
    BUMPER("앞 범퍼", 0.30f to 0.78f, 0.30f to 0.80f),
    HOOD("보닛", 0.36f to 0.40f, 0.35f to 0.36f),
    WINDSHIELD("앞 유리", 0.56f to 0.18f, 0.46f to 0.20f),
    HEADLAMP("헤드램프", 0.09f to 0.56f, 0.09f to 0.47f),
    FENDER("앞 펜더", 0.73f to 0.52f, 0.69f to 0.50f),
    DOOR("운전석 도어", 0.87f to 0.44f, 0.86f to 0.44f),
    WHEEL("앞 바퀴", 0.67f to 0.83f, 0.73f to 0.76f),
    EXHAUST("배기구", 0.95f to 0.80f, 0.95f to 0.72f),
    INTERIOR("실내 계기판", 0.66f to 0.27f, 0.52f to 0.27f);

    fun anchor(model: CarModel): Pair<Float, Float> = if (model == CarModel.OLD) old else ev
}

/** 상처 종류 — 화면에서 그리는 방식 */
enum class ScarKind(val title: String) {
    DUST("먼지 · 잔 스크래치"),
    SCRATCH("긁힘"),
    DENT("찌그러짐"),
    CRACK("금 · 크랙"),
    SOOT("그을음"),
    WARNING_LIGHT("경고등"),
    BROKEN_LAMP("램프 파손"),
    BENT_WHEEL("휠 휨"),
    SMOKE("찌그러짐 + 연기"),
}

/**
 * 트윈카에 남은 상처 1개 = 흑역사 1건.
 * reason 은 "왜 망가졌는지"를 다른 사람도 알 수 있게 쓴 문장, source 는 판정 근거 데이터.
 */
data class Scar(
    val id: String,
    val type: KarmaType,
    val part: CarPart,
    val kind: ScarKind,
    val damage: Int,
    val kp: Int,
    val reason: String,
    val place: String,
    val source: String,
    val t: Long,
) {
    val label: String get() = "${part.title} ${kind.title}"

    /** 부위 수리비 (KP) — 데미지 1당 120 KP, 최소 100 */
    val repairCost: Int get() = (damage * 120).coerceAtLeast(100)
}

object Scars {

    /** 흑역사 → 트윈카 부위 · 상처 (상세기획서 7장 '트윈카 반응' 열) */
    fun spec(type: KarmaType): Pair<CarPart, ScarKind>? = when (type) {
        KarmaType.RAGE_ACCEL -> CarPart.WHEEL to ScarKind.DUST
        KarmaType.RAGE_BRAKE -> CarPart.BUMPER to ScarKind.DUST
        KarmaType.IDLE_MEDITATION -> CarPart.EXHAUST to ScarKind.SOOT
        KarmaType.SIGNAL_MISSING -> CarPart.DOOR to ScarKind.SCRATCH
        KarmaType.NO_BELT -> CarPart.INTERIOR to ScarKind.WARNING_LIGHT
        KarmaType.OVERSPEED_MINOR -> CarPart.BUMPER to ScarKind.DENT
        KarmaType.RIGHT_TURN_PASS -> CarPart.FENDER to ScarKind.DENT
        KarmaType.OVERSPEED_SCANDAL -> CarPart.WINDSHIELD to ScarKind.CRACK
        KarmaType.RED_LIGHT -> CarPart.HEADLAMP to ScarKind.BROKEN_LAMP
        KarmaType.SCHOOL_ZONE_VIOLATION -> CarPart.HOOD to ScarKind.SMOKE
        KarmaType.STOP_LINE_OVER -> CarPart.BUMPER to ScarKind.SCRATCH
        else -> null
    }

    /** 판정 근거 데이터 (사유 화면에 함께 표시) */
    fun sourceOf(type: KarmaType): String = when (type) {
        KarmaType.SCHOOL_ZONE_VIOLATION -> "공공데이터포털 · 전국어린이보호구역표준데이터 + 차속"
        KarmaType.OVERSPEED_MINOR, KarmaType.OVERSPEED_SCANDAL -> "공공데이터포털 · 전국무인교통단속카메라표준데이터(제한속도) + 차속"
        KarmaType.RED_LIGHT, KarmaType.STOP_LINE_OVER -> "교차로 신호 · 정지선 정보 + 차속 (실시간 신호 API 확보 전 데모)"
        KarmaType.RIGHT_TURN_PASS -> "방향지시등 · 조향 · 차속 + 횡단보도 위치"
        KarmaType.SIGNAL_MISSING -> "Vehicle SDK · 방향지시등 + 조향각"
        KarmaType.NO_BELT -> "Vehicle SDK · 안전벨트 + 차속"
        KarmaType.IDLE_MEDITATION -> "Vehicle SDK · 기어 + 차속 (정차 시간)"
        KarmaType.RAGE_ACCEL, KarmaType.RAGE_BRAKE -> "차속 변화로 계산한 가속도(g)"
        else -> "차량 신호"
    }

    /** 사람이 읽는 사유 문장 */
    fun reasonOf(e: KarmaEvent): String {
        val d = e.detail
        return when (e.type) {
            KarmaType.SCHOOL_ZONE_VIOLATION -> "어린이보호구역 시속 30 준수 위반 · $d"
            KarmaType.OVERSPEED_MINOR -> "제한속도 10~20km/h 초과 5초 이상 · $d"
            KarmaType.OVERSPEED_SCANDAL -> "제한속도 20km/h 넘게 초과 5초 이상 · $d"
            KarmaType.RED_LIGHT -> "적색 신호 위반 · $d"
            KarmaType.STOP_LINE_OVER -> "정지선 침범 · $d"
            KarmaType.RIGHT_TURN_PASS -> "우회전 전 횡단보도 일시정지 안 함 · $d"
            KarmaType.SIGNAL_MISSING -> "방향지시등 없이 차선 변경 · 회전"
            KarmaType.NO_BELT -> "안전벨트 미착용으로 10km/h 넘게 주행"
            KarmaType.IDLE_MEDITATION -> "정차 중 공회전 10분 초과"
            KarmaType.RAGE_ACCEL -> "급가속 · $d"
            KarmaType.RAGE_BRAKE -> "급정거 · $d"
            else -> d
        }
    }

    fun fromEvent(e: KarmaEvent, place: String = e.place): Scar? {
        val (part, kind) = spec(e.type) ?: return null
        return Scar(
            id = "${e.t}-${e.type.ordinal}",
            type = e.type,
            part = part,
            kind = kind,
            damage = e.damage,
            kp = e.kp,
            reason = reasonOf(e),
            place = place,
            source = sourceOf(e.type),
            t = e.t,
        )
    }

    /**
     * 데미지가 [amount]만큼 회복될 때 상처를 가벼운 것부터 지운다
     * (세차 · 자연 회복 · 회생 미션 · 미니게임 공통).
     */
    fun heal(scars: List<Scar>, amount: Int): List<Scar> {
        var left = amount
        if (left <= 0) return scars
        val order = scars.sortedWith(compareBy<Scar> { it.damage }.thenBy { it.t })
        val healed = mutableMapOf<String, Int>()
        for (s in order) {
            if (left <= 0) break
            val take = minOf(left, s.damage.coerceAtLeast(1))
            healed[s.id] = s.damage - take
            left -= take
        }
        return scars.mapNotNull { s ->
            val nd = healed[s.id] ?: return@mapNotNull s
            if (nd <= 0) null else s.copy(damage = nd)
        }
    }
}
