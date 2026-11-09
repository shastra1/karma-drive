package com.karmadrive.incar.engine

enum class Kind { SKILL, BLACK, WARNING }

/** 기획서 v8 — 매너 스킬 7종 + 법규 스킬, 흑역사 표(7장) */
enum class KarmaType(val title: String, val kind: Kind, val emoji: String) {
    // 매너 스킬 7종
    PERFECT_STOP("퍼펙트 스톱", Kind.SKILL, "🛑"),
    SIGNAL_COMBO("깜빡이 콤보", Kind.SKILL, "💡"),
    YELLOW_BRAKE("옐로 브레이크", Kind.SKILL, "🟡"),
    SCHOOL_ZONE_STEALTH("스쿨존 스텔스", Kind.SKILL, "🏫"),
    BUTTER_DRIVING("버터 드라이빙", Kind.SKILL, "🧈"),
    ECO_HOLD("에코 홀드", Kind.SKILL, "🌱"),
    ALL_BELT_START("올 벨트 스타트", Kind.SKILL, "🔒"),
    // 웨더 · 법규 · 보너스
    RAIN_MASTER("레인 마스터", Kind.SKILL, "🌧"),
    LIMIT_KEEPER("리미트 키퍼", Kind.SKILL, "🎯"),
    STOP_LINE_MASTER("정지선 마스터", Kind.SKILL, "🚦"),
    SAFE_PASS("돌발 구간 세이프 패스", Kind.SKILL, "🚧"),
    GOLDEN_ROAD("골든 로드 발견", Kind.SKILL, "✨"),
    DISTANCE("주행 거리 보상", Kind.SKILL, "🛣"),
    // 흑역사
    RAGE_ACCEL("분노의 질주 · 급가속", Kind.BLACK, "🔥"),
    RAGE_BRAKE("분노의 질주 · 급정거", Kind.BLACK, "💥"),
    IDLE_MEDITATION("공회전 명상", Kind.BLACK, "💨"),
    SIGNAL_MISSING("깜빡이 실종 사건", Kind.BLACK, "👻"),
    RIGHT_TURN_PASS("우회전 패싱", Kind.BLACK, "↪"),
    NO_BELT("노벨트 챌린지", Kind.BLACK, "🪢"),
    OVERSPEED_MINOR("과속 스캔들(소)", Kind.BLACK, "⏱"),
    OVERSPEED_SCANDAL("과속 스캔들(대)", Kind.BLACK, "🚨"),
    RED_LIGHT("레드카드", Kind.BLACK, "🔴"),
    SCHOOL_ZONE_VIOLATION("스쿨존 침공", Kind.BLACK, "🚸"),
    STOP_LINE_OVER("정지선 침범", Kind.BLACK, "⛔"),
    WARNING("오늘의 첫 경고", Kind.WARNING, "⚠"),
}

data class KarmaEvent(
    val type: KarmaType,
    val t: Long,
    val kp: Int,
    val damage: Int = 0,
    val detail: String = "",
    /** 판정 당시 도로 이름 (사유 표시용) */
    val place: String = "",
    /** 리플레이 경로상의 위치 (RunRecorder가 채움) */
    val x: Float = 0f,
    val y: Float = 0f,
)

enum class Grade(val label: String, val line: String) {
    S("S", "완벽한 매너! 오늘의 트윈카가 반짝여요."),
    A("A", "아주 좋아요. 콤보만 조금 더!"),
    B("B", "무난한 주행이었어요."),
    C("C", "아슬아슬했어요. 다음엔 깜빡이부터."),
    D("D", "흑역사가 생겼어요. 회생 미션으로 만회해요."),
}

data class PathPoint(val x: Float, val y: Float)

data class RunSummary(
    val runId: String,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val kp: Int,
    val damage: Int,
    val maxCombo: Int,
    val grade: Grade,
    val events: List<KarmaEvent>,
    val path: List<PathPoint>,
    val simulated: Boolean,
    /** 매너 지수 MQ (0~100) */
    val mq: Int = 100,
    /** 출발 위치 (운전 MBTI 동선 축) */
    val startLat: Double? = null,
    val startLon: Double? = null,
) {
    val skills get() = events.filter { it.type.kind == Kind.SKILL }
    val blacks get() = events.filter { it.type.kind != Kind.SKILL }
    val durationMin: Int get() = ((endMs - startMs) / 60_000L).toInt()
}

/** 판정 기준값 v2 (상세기획서 4 · 7장, 개발계획서 4장) */
data class Rules(
    val stopKmh: Float = 3f,
    val stopHoldMs: Long = 1_000,
    val turnStartDeg: Float = 15f,
    val turnEndDeg: Float = 8f,
    val signalGraceMs: Long = 3_000,
    val moveKmh: Float = 3f,
    val butterKm: Double = 1.0,
    val butterMaxG: Float = 0.25f,
    val rageAccelG: Float = 0.40f,
    val rageBrakeG: Float = 0.45f,
    val rageCooldownMs: Long = 5_000,
    val damageCapPerRun: Int = 25,
    // 스킬
    val kpPerfectStop: Int = 5,
    val kpComboBase: Int = 2,
    /** 콤보 배율 최대 2배 */
    val comboMaxMultiplier: Double = 2.0,
    val kpYellowBrake: Int = 5,
    val kpSchoolStealth: Int = 10,
    val kpButter: Int = 2,
    val rainMultiplier: Double = 1.5,
    val kpEcoHold: Int = 3,
    val ecoMinMs: Long = 30_000,
    val ecoMaxMs: Long = 180_000,
    val ecoMaxPerRun: Int = 5,
    val kpAllBelt: Int = 3,
    val kpLimitKeeper: Int = 2,
    val kpStopLine: Int = 3,
    val kpSafePass: Int = 3,
    val kpGolden: Int = 30,
    // 흑역사 (경미 · 보통 · 중대 · 심각)
    val kpRage: Int = -5, val dmgRage: Int = 2,
    val idleBlackMs: Long = 600_000, val kpIdle: Int = -10, val dmgIdle: Int = 2,
    val kpMissing: Int = -10, val dmgMissing: Int = 3,
    val noBeltKmh: Float = 10f, val noBeltHoldMs: Long = 10_000, val kpNoBelt: Int = -20, val dmgNoBelt: Int = 5,
    val overMinorKmh: Float = 10f, val overScandalKmh: Float = 20f, val overHoldMs: Long = 5_000,
    val kpOverMinor: Int = -20, val dmgOverMinor: Int = 5,
    val kpOverScandal: Int = -50, val dmgOverScandal: Int = 10,
    val kpRedLight: Int = -100, val dmgRedLight: Int = 20,
    val schoolToleranceKmh: Float = 1f,
    val kpSchoolViolation: Int = -100, val dmgSchoolViolation: Int = 20,
    val stopLineWindowM: Float = 15f,
    val stopLineOverM: Float = 10f,
    val crossMinKmh: Float = 5f,
    val kpStopLineOver: Int = -5, val dmgStopLineOver: Int = 2,
    /** 우회전 패싱: 횡단보도 앞 우회전인데 일시정지 없이 이 속도 이상으로 회전 */
    val rightTurnPassKmh: Float = 10f,
    val kpRightTurnPass: Int = -30, val dmgRightTurnPass: Int = 8,
)

/** 매너 지수: 데미지 1당 3점 감점 */
fun mqOf(damage: Int): Int = (100 - damage * 3).coerceIn(0, 100)

/** 거리 KP 배율: MQ 90 이상 1.5배, 70 미만 절반 */
fun distanceMultiplier(mq: Int): Double = when {
    mq >= 90 -> 1.5
    mq >= 70 -> 1.0
    else -> 0.5
}

fun gradeOf(kp: Int): Grade = when {
    kp >= 25 -> Grade.S
    kp >= 12 -> Grade.A
    kp >= 4 -> Grade.B
    kp >= 0 -> Grade.C
    else -> Grade.D
}
