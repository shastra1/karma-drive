package com.karmadrive.incar.game

import com.karmadrive.incar.engine.Grade
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.engine.KarmaType
import com.karmadrive.incar.engine.RunSummary
import java.util.Calendar

/** 트윈카 무드 5종 (+회복 · 특별) — 상세기획서 PLAY(3) */
enum class TwinMood(val label: String, val emoji: String) {
    HAPPY("신남", "😆"),
    PROUD("뿌듯", "😎"),
    CALM("평온", "🙂"),
    SULKY("삐짐", "😤"),
    HURT("아픔", "🤕"),
    RECOVER("회복", "🥹"),
    SPECIAL("특별", "🎉"),
}

enum class Personality(val label: String) { KIND("다정"), TSUNDERE("츤데레"), CHIC("시크") }

/** 데미지 게이지(0~100) → 외관 6단계 */
data class DamageLevel(val level: Int, val name: String, val desc: String)

object Damage {
    val levels = listOf(
        DamageLevel(0, "쇼룸 컨디션", "반짝반짝"),
        DamageLevel(1, "먼지 · 잔 스크래치", "세차 한 번이면 OK"),
        DamageLevel(2, "도어 긁힘", "살짝 삐졌어요"),
        DamageLevel(3, "범퍼 찍힘", "정비가 필요해요"),
        DamageLevel(4, "유리 금 · 휠 휨", "KP 적립 절반 · 회생 미션"),
        DamageLevel(5, "연기 · 램프 파손", "쿠폰몰 잠금 · 48시간 회생 미션"),
    )

    fun levelOf(damage: Int): DamageLevel = levels[((damage + 19) / 20).coerceIn(0, 5)]
}

/** 성장 사다리 Stage 0~6 + Legend (차종은 가상의 예시) */
data class Stage(val index: Int, val name: String, val car: String, val cost: Int, val condition: String, val perk: String)

object Ladder {
    val stages = listOf(
        Stage(0, "Stage 0", "연식 오래된 해치백 '똥차'", 0, "가입 즉시", "이름 짓기 · 기본 스킨"),
        Stage(1, "Stage 1", "중고 준중형 세단", 3_000, "매너 지수 70 이상 · 누적 300km", "튜닝 슬롯 추가"),
        Stage(2, "Stage 2", "신차 엔트리 SUV", 8_000, "실버 리그", "실물 쿠폰몰 오픈"),
        Stage(3, "Stage 3", "신차 준중형", 15_000, "골드 리그", "적립 10% 추가"),
        Stage(4, "Stage 4", "중형 세단 · SUV", 25_000, "골드 + 30일 스트릭", "크루 리더 권한"),
        Stage(5, "Stage 5", "대형 · EV", 40_000, "플래티넘 리그", "컨셉카 선공개"),
        Stage(6, "Stage 6", "프리미엄", 60_000, "플래티넘 + 누적 10,000km", "골드 번호판"),
        Stage(7, "★ Legend", "미출시 컨셉카", 100_000, "도로의 전설 + 디자인 투표", "현대차 행사 초청"),
    )
}

/** 리그 티어 (월간) */
enum class Tier(val label: String, val minMq: Int) {
    BRONZE("브론즈", 0), SILVER("실버", 70), GOLD("골드", 80), PLATINUM("플래티넘", 90), LEGEND("도로의 전설", 98);

    companion object {
        fun of(avgMq: Int, runs: Int): Tier =
            if (runs == 0) BRONZE else entries.last { avgMq >= it.minMq }.let { if (it == LEGEND && runs < 5) PLATINUM else it }
    }
}

/** 차 능력치 4종 */
data class Abilities(val mnr: Int, val smt: Int, val eco: Int, val exp: Int)

/** 운전 MBTI 16종 */
data class DriveMbti(val code: String, val name: String, val line: String)

object Mbti {
    private val table = mapOf(
        "BGRD" to ("출근길의 정석러" to "같은 길도 매일 완벽하게"),
        "BGRN" to ("밤길의 수호자" to "야간에도 흐트러짐 없음"),
        "BGED" to ("느긋한 여행가" to "새 길도 천천히 음미"),
        "BGEN" to ("달빛 크루저" to "밤 드라이브의 낭만파"),
        "BSRD" to ("효율 끝판왕" to "부드럽지만 칼같은 동선"),
        "BSRN" to ("조용한 실속파" to "밤길 최단 경로 장인"),
        "BSED" to ("길치 탈출러" to "새 길 찾는 실용주의"),
        "BSEN" to ("미드나잇 노마드" to "밤마다 새로운 동네"),
        "JGRD" to ("리듬 타는 신사" to "경쾌하지만 예의 바름"),
        "JGRN" to ("야간 퇴근 장인" to "밤에도 깜빡이는 필수"),
        "JGED" to ("매너 탐험가" to "낯선 길도 예의 있게"),
        "JGEN" to ("야행성 모험가" to "밤에 새 길 개척"),
        "JSRD" to ("출근 스프린터" to "빠르고 정확, 매너만 조금 더"),
        "JSRN" to ("야간 스피드러너" to "흑역사 주의보 발령"),
        "JSED" to ("도로 위 개척자" to "에너지 넘치는 탐험"),
        "JSEN" to ("분노의 질주 후보생" to "똥차가 걱정하고 있어요"),
    )

    fun of(runs: List<RunSummary>): DriveMbti? {
        if (runs.isEmpty()) return null
        val events = runs.flatMap { it.events }
        val rage = events.count { it.type == KarmaType.RAGE_ACCEL || it.type == KarmaType.RAGE_BRAKE }
        val butter = events.count { it.type == KarmaType.BUTTER_DRIVING || it.type == KarmaType.RAIN_MASTER }
        val combo = events.count { it.type == KarmaType.SIGNAL_COMBO || it.type == KarmaType.PERFECT_STOP }
        val missing = events.count { it.type == KarmaType.SIGNAL_MISSING || it.type == KarmaType.OVERSPEED_MINOR || it.type == KarmaType.OVERSPEED_SCANDAL }
        val b = if (rage.toDouble() / runs.size < 0.5 || butter >= rage * 2) "B" else "J"
        val g = if (combo + missing == 0 || combo.toDouble() / (combo + missing) >= 0.7) "G" else "S"
        val cells = runs.mapNotNull { r -> r.startLat?.let { (it * 100).toInt() to ((r.startLon ?: 0.0) * 100).toInt() } }.toSet()
        val r = if (cells.size >= 3) "E" else "R"
        val night = runs.count { hourOf(it.startMs) !in 6..19 }
        val d = if (night * 2 > runs.size) "N" else "D"
        val code = b + g + r + d
        val (name, line) = table.getValue(code)
        return DriveMbti(code, name, line)
    }

    private fun hourOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
}

/** 트윈카 대사 스크립트 — 반말 친구 톤, 1문장, 잔소리 금지 */
object Lines {
    fun of(mood: TwinMood, p: Personality, seed: Int): String {
        val base = when (mood) {
            TwinMood.HAPPY -> listOf("오늘 진짜 버터처럼 부드러웠어!", "콤보 안 끊긴 거 봤어? 우리 좀 치는 듯")
            TwinMood.PROUD -> listOf("일주일 무사고! 나 오늘 광 좀 나지 않아?", "요즘 우리 좀 멋있지 않아?")
            TwinMood.CALM -> listOf("오늘도 무사히 도착. 수고했어.", "내일 골든 로드는 어디 숨어 있을까?")
            TwinMood.SULKY -> listOf("아까 깜빡이… 나만 기억하는 거야?", "옆 차가 놀란 표정이던데. 나도 놀랐고.")
            TwinMood.HURT -> listOf("범퍼가 좀 아파… 3일만 살살 부탁해.", "정비소 가고 싶다. 거기 커피 맛있대.")
            TwinMood.RECOVER -> listOf("살았다! 역시 너밖에 없어.", "다시 반짝반짝, 고마워!")
            TwinMood.SPECIAL -> listOf("오늘은 특별한 날! 천천히 가자.", "우리 오늘 골든 로드 찾았어!")
        }
        val line = base[Math.floorMod(seed, base.size)]
        return when (p) {
            Personality.KIND -> line
            Personality.TSUNDERE -> when (mood) {
                TwinMood.HAPPY, TwinMood.PROUD, TwinMood.SPECIAL -> "흥, $line 딱히 칭찬하는 건 아니고."
                TwinMood.SULKY, TwinMood.HURT -> "$line …걱정한 거 아니거든."
                else -> line
            }
            Personality.CHIC -> line.split("! ", "? ", ". ").first().trimEnd('.', '!', '?', ' ') + "."
        }
    }
}

/** 데일리 퀘스트 */
data class Quest(val id: String, val title: String, val goal: Int, val reward: Int)

object Quests {
    val daily = listOf(
        Quest("perfect3", "퍼펙트 스톱 3회", 3, 30),
        Quest("combo10", "깜빡이 콤보 10회", 10, 50),
        Quest("runA", "RUN 등급 A 이상 1회", 1, 20),
        Quest("roulette", "데일리 주차 룰렛 돌리기", 1, 10),
        Quest("mood", "트윈카 기분 확인하기 (개러지)", 1, 10),
    )

    fun progress(q: Quest, todayRuns: List<RunSummary>, rouletteDone: Boolean, moodChecked: Boolean): Int = when (q.id) {
        "perfect3" -> todayRuns.sumOf { r -> r.events.count { it.type == KarmaType.PERFECT_STOP } }
        "combo10" -> todayRuns.sumOf { r -> r.events.count { it.type == KarmaType.SIGNAL_COMBO } }
        "runA" -> todayRuns.count { it.grade == Grade.S || it.grade == Grade.A }
        "roulette" -> if (rouletteDone) 1 else 0
        "mood" -> if (moodChecked) 1 else 0
        else -> 0
    }.coerceAtMost(q.goal)
}

/** 배지 도감 */
data class Badge(val name: String, val group: String, val condition: String, val earned: Boolean)

object Badges {
    fun of(runs: List<RunSummary>, stage: Int, missionsDone: Int): List<Badge> {
        val ev = runs.flatMap { it.events }
        fun n(t: KarmaType) = ev.count { it.type == t }
        return listOf(
            Badge("퍼펙트 스톱 데뷔", "스킬", "퍼펙트 스톱 1회", n(KarmaType.PERFECT_STOP) >= 1),
            Badge("깜빡이 장인", "스킬", "깜빡이 콤보 50회", n(KarmaType.SIGNAL_COMBO) >= 50),
            Badge("옐로 마스터", "스킬", "옐로 브레이크 5회", n(KarmaType.YELLOW_BRAKE) >= 5),
            Badge("버터 장인", "스킬", "버터 드라이빙 20구간", n(KarmaType.BUTTER_DRIVING) + n(KarmaType.RAIN_MASTER) >= 20),
            Badge("스쿨존 수호자", "안심", "스쿨존 무사통과 10회", n(KarmaType.SCHOOL_ZONE_STEALTH) >= 10),
            Badge("올 벨트 패밀리", "안심", "올 벨트 스타트 10회", n(KarmaType.ALL_BELT_START) >= 10),
            Badge("공회전 제로", "안심", "에코 홀드 10회", n(KarmaType.ECO_HOLD) >= 10),
            Badge("첫 S등급", "성장", "RUN S 등급 1회", runs.any { it.grade == Grade.S }),
            Badge("똥차 졸업", "성장", "Stage 1 업그레이드", stage >= 1),
            Badge("회생의 아이콘", "개과천선", "회생 미션 완료", missionsDone >= 1),
            Badge("빗길의 기사", "히든", "레인 마스터 달성", n(KarmaType.RAIN_MASTER) >= 1),
            Badge("골든 헌터", "히든", "골든 로드 발견", n(KarmaType.GOLDEN_ROAD) >= 1),
        )
    }
}

fun abilitiesOf(runs: List<RunSummary>): Abilities {
    if (runs.isEmpty()) return Abilities(50, 50, 50, 0)
    val ev = runs.flatMap { it.events }
    val mnr = runs.map { it.mq }.average().toInt()
    val rage = ev.count { it.type == KarmaType.RAGE_ACCEL || it.type == KarmaType.RAGE_BRAKE }
    val butter = ev.count { it.type == KarmaType.BUTTER_DRIVING || it.type == KarmaType.RAIN_MASTER }
    val smt = (60 + butter * 4 - rage * 8).coerceIn(0, 100)
    val eco = (50 + ev.count { it.type == KarmaType.ECO_HOLD } * 5 - ev.count { it.type == KarmaType.IDLE_MEDITATION } * 10).coerceIn(0, 100)
    val exp = (runs.sumOf { it.distanceKm } * 2).toInt().coerceIn(0, 100)
    return Abilities(mnr, smt, eco, exp)
}

fun moodOf(last: RunSummary?, damage: Int, streakDays: Int, missionJustDone: Boolean): TwinMood = when {
    missionJustDone -> TwinMood.RECOVER
    last == null -> TwinMood.CALM
    last.events.any { it.type == KarmaType.GOLDEN_ROAD } -> TwinMood.SPECIAL
    damage >= 60 || last.events.any { it.type in setOf(KarmaType.RED_LIGHT, KarmaType.SCHOOL_ZONE_VIOLATION, KarmaType.OVERSPEED_SCANDAL) } -> TwinMood.HURT
    last.events.any { it.type == KarmaType.SIGNAL_MISSING } -> TwinMood.SULKY
    last.blacks.any { it.type.kind == Kind.BLACK } -> TwinMood.SULKY
    streakDays >= 7 -> TwinMood.PROUD
    last.grade == Grade.S -> TwinMood.HAPPY
    else -> TwinMood.CALM
}
