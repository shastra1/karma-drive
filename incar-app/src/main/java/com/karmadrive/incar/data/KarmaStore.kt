package com.karmadrive.incar.data

import android.content.Context
import com.karmadrive.incar.engine.Grade
import com.karmadrive.incar.engine.KarmaEvent
import com.karmadrive.incar.engine.KarmaType
import com.karmadrive.incar.engine.Kind
import com.karmadrive.incar.engine.PathPoint
import com.karmadrive.incar.engine.RunSummary
import com.karmadrive.incar.game.Damage
import com.karmadrive.incar.game.Ladder
import com.karmadrive.incar.game.Personality
import com.karmadrive.incar.game.Quests
import com.karmadrive.incar.game.CarPart
import com.karmadrive.incar.game.Scar
import com.karmadrive.incar.game.ScarKind
import com.karmadrive.incar.game.Scars
import com.karmadrive.incar.game.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class Coupon(
    val id: String,
    val title: String,
    val place: String,
    val category: String, // CAR_WASH · FUEL · MAINTENANCE · PARKING · CAFE
    val costKp: Int,
    val lat: Double,
    val lon: Double,
)

data class Skin(val id: String, val name: String, val color: Long, val costKp: Int)

data class ShopItem(val id: String, val name: String, val desc: String, val costKp: Int, val repair: Int)

/** 회생 미션: 48시간 안에 RUN A 이상 [need]회 → 데미지 [reward] 회복 */
data class Mission(val text: String, val acceptedAt: Long, val need: Int, val reward: Int)

/** 동승자 · 가족이 KARMA 앱(라이브 트윈)에서 보낸 응원 스티커 — 운전자는 주차 후에 본다 */
data class Cheer(val emoji: String, val text: String, val from: String, val t: Long)

/** 게임 진행 상태 (한 덩어리로 저장) */
data class GameState(
    val damage: Int = 0,
    val carName: String = "똥차1호",
    val personality: Personality = Personality.KIND,
    val stage: Int = 0,
    val ownedSkins: Set<String> = setOf("mint"),
    val rouletteDay: String = "",
    val rouletteWon: Int = 0,
    val moodCheckDay: String = "",
    val claimed: Set<String> = emptySet(),
    val mission: Mission? = null,
    val missionsDone: Int = 0,
    val missionDoneDay: String = "",
    val lastBlackDay: String = "",
    val lastRecoveryDay: String = "",
    val vote: String = "",
    val lastNote: String = "",
    /** 트윈카에 남은 상처(흑역사별 부위 · 사유) — 데미지 게이지의 근거 */
    val scars: List<Scar> = emptyList(),
    val cheers: List<Cheer> = emptyList(),
)

/** 프로토타입용 로컬 저장소 (모의 서버 역할). 실제 서비스에서는 서버 KP 원장으로 이동 */
class KarmaStore(context: Context) {
    private val prefs = context.getSharedPreferences("karma", Context.MODE_PRIVATE)

    private val _totalKp = MutableStateFlow(prefs.getInt(K_TOTAL, START_KP))
    val totalKp: StateFlow<Int> = _totalKp

    private val _runs = MutableStateFlow(loadRuns())
    val runs: StateFlow<List<RunSummary>> = _runs

    private val _owned = MutableStateFlow(prefs.getStringSet(K_OWNED, emptySet())!!.toSet())
    val ownedCoupons: StateFlow<Set<String>> = _owned

    private val _skin = MutableStateFlow(prefs.getString(K_SKIN, "mint")!!)
    val skin: StateFlow<String> = _skin

    private val _game = MutableStateFlow(loadGame())
    val game: StateFlow<GameState> = _game

    /** 실물 쿠폰 카탈로그 (상세기획서 LOOT) — 역삼역 주변 예시 사용처 */
    val coupons = listOf(
        Coupon("park", "주차 1시간권", "역삼 공영주차장", "PARKING", 3_000, 37.5003, 127.0369),
        Coupon("fuel", "주유 · EV 충전 5,000원권", "에코 충전소 역삼", "FUEL", 5_000, 37.5006, 127.0364),
        Coupon("cafe", "드라이브스루 커피", "모닝 커피 테헤란로", "CAFE", 5_000, 37.5010, 127.0396),
        Coupon("wash", "자동 세차 1회", "그린 세차장 강남", "CAR_WASH", 8_000, 37.4979, 127.0276),
        Coupon("check", "무상 종합 점검", "블루 정비소 선릉", "MAINTENANCE", 10_000, 37.5045, 127.0490),
        Coupon("oil", "엔진오일 교환 30% 할인", "블루 정비소 선릉", "MAINTENANCE", 15_000, 37.5045, 127.0490),
    )

    /** 튜닝샵 컬러 */
    val skins = listOf(
        Skin("mint", "클래식 블루", 0xFF8EC5E8, 0),
        Skin("gold", "레트로 옐로", 0xFFFFC857, 500),
        Skin("sky", "체리 핑크", 0xFFFF8FB1, 1_000),
    )

    /** 가상 아이템샵 */
    val items = listOf(
        ShopItem("wash", "가상 세차", "먼지를 씻어 내요 · 데미지 −2", 300, 2),
        ShopItem("dent", "판금 수리", "찌그러진 곳을 펴요 · 데미지 −5", 800, 5),
        ShopItem("full", "풀 리페어", "쇼룸 컨디션으로 · 데미지 0", 3_000, 100),
    )

    val milestones = listOf(10_000 to "카르마 루키 배지 + 세차권", 50_000 to "블루핸즈 정비 패키지", 150_000 to "신차 시승 VIP 초대", 300_000 to "신차 구매 할인 쿠폰")

    init {
        applyNaturalRecovery()
    }

    // ---------- 조회 ----------

    fun isWarningAvailableToday(): Boolean = prefs.getString(K_WARN_DATE, "") != today()

    fun markWarningUsed() = prefs.edit().putString(K_WARN_DATE, today()).apply()

    fun todayRuns(): List<RunSummary> {
        val d = today()
        return _runs.value.filter { DAY.format(Date(it.startMs)) == d }
    }

    fun monthRuns(): List<RunSummary> {
        val cut = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        return _runs.value.filter { it.startMs >= cut }
    }

    fun avgMq(): Int = monthRuns().takeIf { it.isNotEmpty() }?.map { it.mq }?.average()?.toInt() ?: 0

    fun tier(): Tier = Tier.of(avgMq(), monthRuns().size)

    fun damageLevel() = Damage.levelOf(_game.value.damage)

    /** 연속 무흑역사 주행일 */
    fun streakDays(): Int {
        val byDay = _runs.value.groupBy { DAY.format(Date(it.startMs)) }
        var n = 0
        val cal = java.util.Calendar.getInstance()
        while (true) {
            val runs = byDay[DAY.format(cal.time)] ?: break
            if (runs.any { r -> r.events.any { it.type.kind == Kind.BLACK } }) break
            n++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    /** 오늘의 골든 로드 — 날짜마다 다른 숨은 보너스 지점(출발 후 km) */
    fun goldenKmToday(): Double = Random(today().hashCode()).nextDouble(0.8, 3.0)

    fun couponMallOpen(): Boolean =
        tier().ordinal >= Tier.SILVER.ordinal && _totalKp.value >= 5_000 && damageLevel().level < 5

    fun mallLockReason(): String = when {
        damageLevel().level >= 5 -> "데미지 Lv.5 — 회생 미션을 완료하면 다시 열려요"
        tier().ordinal < Tier.SILVER.ordinal -> "실버 리그 이상에서 열려요 (지금 ${tier().label})"
        _totalKp.value < 5_000 -> "잔액 5,000 KP 이상일 때 열려요 (지금 ${_totalKp.value} KP)"
        else -> ""
    }

    // ---------- 변경 ----------

    fun saveRun(run: RunSummary) {
        val list = (listOf(run) + _runs.value).take(MAX_RUNS)
        _runs.value = list
        var g = _game.value
        val lvBefore = Damage.levelOf(g.damage).level
        var gain = run.kp
        var note = ""
        if (gain > 0 && lvBefore >= 4) {
            gain /= 2
            note = "데미지 Lv.$lvBefore — KP 적립 절반 (${run.kp} → $gain)"
        }
        // 데미지 · 상처는 주행 중 흑역사가 생기는 순간 applyLiveBlack()으로 이미 반영됨
        if (run.events.any { it.type.kind == Kind.BLACK }) g = g.copy(lastBlackDay = today())
        // 회생 미션 진행
        val m = g.mission
        if (m != null) {
            val within = list.filter { it.startMs >= m.acceptedAt && it.startMs - m.acceptedAt <= 48L * 3600 * 1000 }
            val good = within.count { it.grade == Grade.S || it.grade == Grade.A }
            if (good >= m.need) {
                g = healed(g, m.reward).copy(mission = null, missionsDone = g.missionsDone + 1, missionDoneDay = today())
                note = (note + " 회생 미션 완료! 데미지 −${m.reward}").trim()
            } else if (System.currentTimeMillis() - m.acceptedAt > 48L * 3600 * 1000) {
                g = g.copy(mission = null)
            }
        }
        _game.value = g.copy(lastNote = note)
        addKp(gain)
        saveRunsAndGame()
    }

    fun spinRoulette(): Int? {
        val g = _game.value
        if (g.rouletteDay == today()) return null
        val v = listOf(10, 10, 10, 20, 20, 30, 30, 50, 100).random()
        _game.value = g.copy(rouletteDay = today(), rouletteWon = v)
        addKp(v)
        saveGame()
        return v
    }

    fun rouletteDoneToday() = _game.value.rouletteDay == today()

    fun markMoodChecked() {
        if (_game.value.moodCheckDay != today()) {
            _game.value = _game.value.copy(moodCheckDay = today())
            saveGame()
        }
    }

    fun questProgress(id: String): Int {
        val q = Quests.daily.first { it.id == id }
        val g = _game.value
        return Quests.progress(q, todayRuns(), g.rouletteDay == today(), g.moodCheckDay == today())
    }

    fun questClaimed(id: String) = _game.value.claimed.contains("${today()}:$id")

    fun claimQuest(id: String): Boolean {
        val q = Quests.daily.first { it.id == id }
        if (questClaimed(id) || questProgress(id) < q.goal) return false
        _game.value = _game.value.copy(claimed = _game.value.claimed.filter { it.startsWith(today()) }.toSet() + "${today()}:$id")
        addKp(q.reward)
        saveGame()
        return true
    }

    fun buyCoupon(c: Coupon): Boolean {
        if (!couponMallOpen() || _owned.value.contains(c.id) || _totalKp.value < c.costKp) return false
        addKp(-c.costKp)
        _owned.value = _owned.value + c.id
        prefs.edit().putStringSet(K_OWNED, _owned.value).apply()
        return true
    }

    fun buyItem(item: ShopItem): Boolean {
        if (_totalKp.value < item.costKp || _game.value.damage == 0) return false
        addKp(-item.costKp)
        _game.value = healed(_game.value, item.repair)
        saveGame()
        return true
    }

    /** 미니게임(세차 · 정비)으로 직접 고치기 — KP 대신 손맛 */
    fun repairByMiniGame(amount: Int) {
        _game.value = healed(_game.value, amount)
        saveGame()
    }

    // ---------- 트윈카 상처 (라이브) ----------

    /** 주행 중 흑역사가 판정되는 순간 호출 — 트윈카에 바로 상처가 생기고 라이브 공유 화면에 반영된다 */
    fun applyLiveBlack(e: KarmaEvent) {
        val scar = Scars.fromEvent(e) ?: return
        val g = _game.value
        if (g.scars.any { it.id == scar.id }) return
        _game.value = g.copy(
            damage = (g.damage + e.damage).coerceIn(0, 100),
            scars = (g.scars + scar).takeLast(40),
            lastBlackDay = today(),
        )
        saveGame()
    }

    /** 정비소: 부위 하나 수리 */
    fun repairScar(id: String): Boolean {
        val g = _game.value
        val s = g.scars.firstOrNull { it.id == id } ?: return false
        if (_totalKp.value < s.repairCost) return false
        addKp(-s.repairCost)
        _game.value = g.copy(damage = (g.damage - s.damage).coerceAtLeast(0), scars = g.scars - s, lastNote = "${s.label} 수리 완료 (−${s.repairCost} KP)")
        saveGame()
        return true
    }

    /** 정비소: 전체 수리 (20% 할인) */
    fun repairAllCost(): Int = (_game.value.scars.sumOf { it.repairCost } * 0.8).toInt()

    fun repairAll(): Boolean {
        val g = _game.value
        if (g.scars.isEmpty() && g.damage == 0) return false
        val cost = repairAllCost().coerceAtLeast(if (g.damage > 0) 300 else 0)
        if (_totalKp.value < cost) return false
        addKp(-cost)
        _game.value = g.copy(damage = 0, scars = emptyList(), lastNote = "풀 리페어 완료 — 쇼룸 컨디션! (−$cost KP)")
        saveGame()
        return true
    }

    /** 라이브 트윈에서 보낸 응원 스티커 */
    fun addCheer(emoji: String, text: String, from: String) {
        val g = _game.value
        _game.value = g.copy(cheers = (g.cheers + Cheer(emoji.take(4), text.take(40), from.take(12).ifBlank { "동승자" }, System.currentTimeMillis())).takeLast(30))
        saveGame()
    }

    fun cheersSince(t: Long): List<Cheer> = _game.value.cheers.filter { it.t >= t }

    /** 데미지가 줄 때 상처도 가벼운 것부터 함께 사라진다 */
    private fun healed(g: GameState, amount: Int): GameState {
        val nd = (g.damage - amount).coerceAtLeast(0)
        val diff = g.damage - nd
        return g.copy(damage = nd, scars = if (nd == 0) emptyList() else Scars.heal(g.scars, diff))
    }

    fun buySkin(s: Skin): Boolean {
        if (_game.value.ownedSkins.contains(s.id)) {
            equipSkin(s.id)
            return true
        }
        if (_totalKp.value < s.costKp) return false
        addKp(-s.costKp)
        _game.value = _game.value.copy(ownedSkins = _game.value.ownedSkins + s.id)
        saveGame()
        equipSkin(s.id)
        return true
    }

    fun equipSkin(id: String) {
        _skin.value = id
        prefs.edit().putString(K_SKIN, id).apply()
    }

    fun upgradeStage(): Boolean {
        val next = Ladder.stages.getOrNull(_game.value.stage + 1) ?: return false
        if (_totalKp.value < next.cost) return false
        addKp(-next.cost)
        _game.value = _game.value.copy(stage = next.index)
        saveGame()
        return true
    }

    fun rename(name: String) {
        _game.value = _game.value.copy(carName = name.take(12).ifBlank { "똥차1호" })
        saveGame()
    }

    fun setPersonality(p: Personality) {
        _game.value = _game.value.copy(personality = p)
        saveGame()
    }

    fun acceptMission(text: String, need: Int, reward: Int) {
        _game.value = _game.value.copy(mission = Mission(text, System.currentTimeMillis(), need, reward))
        saveGame()
    }

    fun vote(color: String): Boolean {
        if (_game.value.vote.isNotEmpty() || _totalKp.value < 100) return false
        addKp(-100)
        _game.value = _game.value.copy(vote = color)
        saveGame()
        return true
    }

    /** 개발 · 시연용 */
    fun devAddKp(v: Int) = addKp(v)

    fun devAddDamage(v: Int) {
        if (v < 0) {
            _game.value = healed(_game.value, -v)
        } else {
            // 시연용: 흑역사 표에서 무작위로 골라 상처를 함께 만든다
            val pool = listOf(KarmaType.OVERSPEED_SCANDAL, KarmaType.RED_LIGHT, KarmaType.SCHOOL_ZONE_VIOLATION, KarmaType.RIGHT_TURN_PASS, KarmaType.SIGNAL_MISSING, KarmaType.IDLE_MEDITATION, KarmaType.NO_BELT)
            val type = pool.random()
            val ev = KarmaEvent(type, System.currentTimeMillis(), 0, v, "개발자 설정에서 추가한 시연용 데미지", place = "시연")
            val scar = Scars.fromEvent(ev)
            val g = _game.value
            _game.value = g.copy(damage = (g.damage + v).coerceIn(0, 100), scars = if (scar != null) (g.scars + scar).takeLast(40) else g.scars)
        }
        saveGame()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _totalKp.value = START_KP
        _runs.value = emptyList()
        _owned.value = emptySet()
        _skin.value = "mint"
        _game.value = GameState()
    }

    // ---------- 내부 ----------

    private fun addKp(v: Int) {
        _totalKp.value = (_totalKp.value + v).coerceAtLeast(0)
        prefs.edit().putInt(K_TOTAL, _totalKp.value).apply()
    }

    /** 자연 회복: 3일 연속 흑역사 0 → 데미지 5 회복 */
    private fun applyNaturalRecovery() {
        val g = _game.value
        if (g.damage == 0) return
        val ref = listOf(g.lastBlackDay, g.lastRecoveryDay).filter { it.isNotEmpty() }.maxOrNull() ?: return
        val days = try {
            ((DAY.parse(today())!!.time - DAY.parse(ref)!!.time) / (24L * 3600 * 1000)).toInt()
        } catch (e: Exception) {
            0
        }
        if (days >= 3) {
            _game.value = healed(g, 5).copy(lastRecoveryDay = today(), lastNote = "3일 무흑역사 — 자연 회복 데미지 −5")
            saveGame()
        }
    }

    private fun saveRunsAndGame() {
        prefs.edit().putString(K_RUNS, JSONArray(_runs.value.map { it.toJson() }).toString()).apply()
        saveGame()
    }

    private fun saveGame() {
        val g = _game.value
        val o = JSONObject()
            .put("damage", g.damage).put("carName", g.carName).put("personality", g.personality.name)
            .put("stage", g.stage).put("ownedSkins", JSONArray(g.ownedSkins.toList()))
            .put("rouletteDay", g.rouletteDay).put("rouletteWon", g.rouletteWon).put("moodCheckDay", g.moodCheckDay)
            .put("claimed", JSONArray(g.claimed.toList())).put("missionsDone", g.missionsDone)
            .put("missionDoneDay", g.missionDoneDay).put("lastBlackDay", g.lastBlackDay)
            .put("lastRecoveryDay", g.lastRecoveryDay).put("vote", g.vote).put("lastNote", g.lastNote)
        o.put("scars", JSONArray(g.scars.map {
            JSONObject().put("id", it.id).put("type", it.type.name).put("part", it.part.name).put("kind", it.kind.name)
                .put("damage", it.damage).put("kp", it.kp).put("reason", it.reason).put("place", it.place)
                .put("source", it.source).put("t", it.t)
        }))
        o.put("cheers", JSONArray(g.cheers.map {
            JSONObject().put("emoji", it.emoji).put("text", it.text).put("from", it.from).put("t", it.t)
        }))
        g.mission?.let {
            o.put("mission", JSONObject().put("text", it.text).put("at", it.acceptedAt).put("need", it.need).put("reward", it.reward))
        }
        prefs.edit().putString(K_GAME, o.toString()).apply()
    }

    private fun loadGame(): GameState = try {
        val o = JSONObject(prefs.getString(K_GAME, "{}")!!)
        fun strings(key: String): Set<String> = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        GameState(
            damage = o.optInt("damage", 0),
            carName = o.optString("carName", "똥차1호"),
            personality = runCatching { Personality.valueOf(o.optString("personality", "KIND")) }.getOrDefault(Personality.KIND),
            stage = o.optInt("stage", 0),
            ownedSkins = strings("ownedSkins").ifEmpty { setOf("mint") },
            rouletteDay = o.optString("rouletteDay", ""),
            rouletteWon = o.optInt("rouletteWon", 0),
            moodCheckDay = o.optString("moodCheckDay", ""),
            claimed = strings("claimed"),
            mission = o.optJSONObject("mission")?.let { Mission(it.getString("text"), it.getLong("at"), it.getInt("need"), it.getInt("reward")) },
            missionsDone = o.optInt("missionsDone", 0),
            missionDoneDay = o.optString("missionDoneDay", ""),
            lastBlackDay = o.optString("lastBlackDay", ""),
            lastRecoveryDay = o.optString("lastRecoveryDay", ""),
            vote = o.optString("vote", ""),
            lastNote = o.optString("lastNote", ""),
            scars = o.optJSONArray("scars")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    runCatching {
                        val so = a.getJSONObject(i)
                        Scar(
                            id = so.getString("id"),
                            type = KarmaType.valueOf(so.getString("type")),
                            part = CarPart.valueOf(so.getString("part")),
                            kind = ScarKind.valueOf(so.getString("kind")),
                            damage = so.getInt("damage"),
                            kp = so.optInt("kp", 0),
                            reason = so.optString("reason", ""),
                            place = so.optString("place", ""),
                            source = so.optString("source", ""),
                            t = so.getLong("t"),
                        )
                    }.getOrNull()
                }
            } ?: emptyList(),
            cheers = o.optJSONArray("cheers")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    runCatching {
                        val co = a.getJSONObject(i)
                        Cheer(co.getString("emoji"), co.getString("text"), co.optString("from", "동승자"), co.getLong("t"))
                    }.getOrNull()
                }
            } ?: emptyList(),
        )
    } catch (e: Exception) {
        GameState()
    }

    private fun loadRuns(): List<RunSummary> = try {
        val arr = JSONArray(prefs.getString(K_RUNS, "[]"))
        (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toRun() }.getOrNull() }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val START_KP = 120
        private const val K_TOTAL = "totalKp"
        private const val K_RUNS = "runs"
        private const val K_OWNED = "owned"
        private const val K_SKIN = "skin"
        private const val K_GAME = "game"
        private const val K_WARN_DATE = "warnDate"
        private const val MAX_RUNS = 60
        private val DAY = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        fun today(): String = DAY.format(Date())
    }
}

private fun RunSummary.toJson() = JSONObject().apply {
    put("runId", runId); put("start", startMs); put("end", endMs)
    put("distanceKm", distanceKm); put("kp", kp); put("damage", damage)
    put("combo", maxCombo); put("grade", grade.name); put("simulated", simulated); put("mq", mq)
    startLat?.let { put("lat", it) }
    startLon?.let { put("lon", it) }
    put("events", JSONArray(events.map {
        JSONObject().put("type", it.type.name).put("t", it.t).put("kp", it.kp)
            .put("damage", it.damage).put("detail", it.detail).put("place", it.place).put("x", it.x.toDouble()).put("y", it.y.toDouble())
    }))
    val p = JSONArray()
    path.forEach { p.put(it.x.toDouble()); p.put(it.y.toDouble()) }
    put("path", p)
}

private fun JSONObject.toRun(): RunSummary {
    val ev = getJSONArray("events")
    val p = getJSONArray("path")
    return RunSummary(
        runId = getString("runId"),
        startMs = getLong("start"),
        endMs = getLong("end"),
        distanceKm = getDouble("distanceKm"),
        kp = getInt("kp"),
        damage = getInt("damage"),
        maxCombo = getInt("combo"),
        grade = Grade.valueOf(getString("grade")),
        events = (0 until ev.length()).mapNotNull {
            val o = ev.getJSONObject(it)
            val type = runCatching { KarmaType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
            KarmaEvent(
                type = type, t = o.getLong("t"), kp = o.getInt("kp"), damage = o.getInt("damage"), detail = o.getString("detail"),
                place = o.optString("place", ""), x = o.getDouble("x").toFloat(), y = o.getDouble("y").toFloat(),
            )
        },
        path = (0 until p.length() / 2).map { PathPoint(p.getDouble(it * 2).toFloat(), p.getDouble(it * 2 + 1).toFloat()) },
        simulated = optBoolean("simulated", false),
        mq = optInt("mq", 100),
        startLat = if (has("lat")) getDouble("lat") else null,
        startLon = if (has("lon")) getDouble("lon") else null,
    )
}
