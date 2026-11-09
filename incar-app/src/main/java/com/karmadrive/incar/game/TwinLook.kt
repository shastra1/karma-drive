package com.karmadrive.incar.game

/**
 * 트윈카 외형 결정 — 성장 단계 · 스킨 · 데미지 단계 → 그림 1장.
 * 인카 앱(Compose)과 라이브 공유 웹(KARMA 앱)이 같은 규칙을 쓴다.
 *
 * 그림: 화면 기획안의 트윈카 일러스트 (old_lv0~5 = 데미지 6단계, old_yellow · old_pink = 튜닝 컬러,
 * ev_blue · ev_red = 성장 단계 EV, concept_white = Legend 컨셉카)
 */
data class TwinLook(
    val model: CarModel,
    /** 그림 이름 (확장자 없음) — drawable / web img 공통 */
    val image: String,
    /** 그림 자체에 데미지 단계가 그려져 있으면 true (아니면 먼지 · 녹을 덧그린다) */
    val damageBaked: Boolean,
)

object Twin {
    fun look(stage: Int, skinId: String, damageLevel: Int): TwinLook = when {
        stage >= 7 -> TwinLook(CarModel.EV, "concept_white", false)
        stage >= 5 -> TwinLook(CarModel.EV, "ev_red", false)
        stage >= 3 -> TwinLook(CarModel.EV, "ev_blue", false)
        skinId == "gold" -> TwinLook(CarModel.OLD, "old_yellow", false)
        skinId == "sky" -> TwinLook(CarModel.OLD, "old_pink", false)
        else -> TwinLook(CarModel.OLD, "old_lv${damageLevel.coerceIn(0, 5)}", true)
    }

    /** 그림 가로:세로 비율 */
    fun aspect(image: String): Float = when (image) {
        "old_lv4" -> 1090f / 1113f
        "old_lv5" -> 1148f / 1169f
        "ev_blue" -> 616f / 387f
        "ev_red" -> 660f / 415f
        "concept_white" -> 927f / 583f
        "old_yellow", "old_pink" -> 286f / 197f
        else -> 1090f / 753f
    }

    /**
     * 연기가 그려진 Lv.4·5 그림은 위쪽에 연기 여백이 있어 차체가 아래쪽에 작게 있다 —
     * 차체가 차지하는 영역(left, top, right, bottom 비율)으로 부위 좌표를 보정한다.
     */
    fun bodyRect(image: String): FloatArray = when (image) {
        "old_lv4" -> floatArrayOf(0.0f, 0.33f, 0.98f, 1.0f)
        "old_lv5" -> floatArrayOf(0.04f, 0.32f, 0.97f, 0.97f)
        else -> floatArrayOf(0f, 0f, 1f, 1f)
    }

    /** 부위 좌표(이미지 비율) */
    fun anchor(part: CarPart, look: TwinLook): Pair<Float, Float> {
        val (ax, ay) = part.anchor(look.model)
        val r = bodyRect(look.image)
        return (r[0] + ax * (r[2] - r[0])) to (r[1] + ay * (r[3] - r[1]))
    }
}
