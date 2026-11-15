package ai.pleos.contest.domain

import org.json.JSONObject
import java.text.Normalizer

data class PackingProfile(val activity: String = "캠핑", val nights: Int = 1,
    val adults: Int = 2, val children: Int = 2, val infants: Int = 0) {
    init {
        require(activity in activities && nights in 0..14 && adults in 1..12 && children in 0..12 && infants in 0..6)
        require(adults + children + infants <= 20)
    }
    val people get() = adults + children + infants
    fun json() = JSONObject().put("activity", activity).put("nights", nights).put("adults", adults)
        .put("children", children).put("infants", infants)
    companion object {
        val activities = listOf("캠핑", "나들이", "운동", "출근")
        fun parse(o: JSONObject): PackingProfile {
            fun count(key: String): Int {
                val n = o.get(key) as? Number ?: throw IllegalArgumentException("$key must be a number")
                require(n.toDouble().isFinite() && n.toDouble() == n.toInt().toDouble())
                return n.toInt()
            }
            return PackingProfile(o.getString("activity"), count("nights"), count("adults"), count("children"), count("infants"))
        }
    }
}

data class PackingSuggestion(val key: String, val name: String, val quantity: Int, val unit: String,
    val reason: String, val recoverable: Boolean = true) {
    val label get() = if (quantity == 1) name else "$name ${quantity}$unit"
}

/** Local planning rules, not weather forecasts, medical advice or generated AI output. */
object PackingRecommendations {
    fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), "")
        .replace(Regex("\\d+(?:\\.\\d+)?(?:개|장|벌|켤레|묶음|[lL])$"), "")

    fun suggest(p: PackingProfile, existing: List<Item>): List<PackingSuggestion> {
        val rows = mutableListOf<PackingSuggestion>()
        fun add(name: String, quantity: Int = 1, unit: String = "개", reason: String, recoverable: Boolean = true) {
            rows += PackingSuggestion(key(name), name, quantity, unit, reason, recoverable)
        }
        when (p.activity) {
            "캠핑" -> {
                add("텐트", reason = "동행 ${p.people}명이 머물 공간을 확인해 주세요. 수용 인원은 제품별로 확인해요.")
                add("방수포", reason = "텐트 바닥을 보호하기 위한 기본 장비예요. 비 예보에 따른 추천은 아니에요.")
                add("랜턴", reason = "해가 진 뒤 주변과 짐을 확인할 때 사용해요.")
                add("캠핑 의자", p.people, reason = "동행 ${p.people}명이 사용할 자리를 준비해요.")
                if (p.nights > 0) add("침낭", p.people, reason = "${p.nights}박 동안 각자 사용할 침낭을 준비해요. 기온에 맞는 제품인지 확인해 주세요.")
                add("쓰레기봉투", p.nights + 1, reason = "${p.nights + 1}일 동안 사용한 자리를 정리해요.", recoverable = false)
            }
            "나들이" -> {
                add("돗자리", reason = "가족이 함께 쉴 자리를 준비해요.")
                add("모자", p.people, reason = "동행 인원별로 야외 활동용 모자를 확인해요.")
            }
            "운동" -> {
                add("운동화", p.people, "켤레", "운동할 인원별로 신발을 준비해요.")
                add("운동복", p.people, "벌", "운동할 인원별로 옷을 준비해요.")
            }
            "출근" -> {
                add("사원증", p.adults, reason = "출근하는 성인의 출입증을 확인해요.")
                add("노트북", reason = "업무에 필요한 기기인지 확인하고 선택해 주세요.")
                add("충전기", reason = "업무용 기기와 호환되는 충전기를 준비해요.")
            }
        }
        add("물병", p.people, reason = "동행 인원별 물병을 준비해요. 필요한 물의 양은 활동과 상황에 맞게 정해 주세요.")
        add("보조배터리", reason = "이동 중 휴대기기의 전원을 보충해요.")
        if (p.activity != "출근") {
            add("구급킷", reason = "기본 구급용품의 내용물과 사용 기한을 직접 확인해 주세요.")
            add("수건", p.people, "장", "동행 ${p.people}명이 각자 사용할 수건을 준비해요.")
        }
        if (p.nights > 0) {
            add("여벌 옷", p.people * p.nights, "벌", "1인당 숙박 1박에 여벌 1벌을 기준으로 계산했어요. 필요에 맞게 편집해 주세요.")
            add("세면도구", p.people, reason = "숙박하는 가족의 개인 세면도구를 확인해요.")
        }
        if (p.children > 0) add("아이 담요", p.children, reason = "어린이 ${p.children}명이 쉴 때 사용할 담요를 준비해요.")
        if (p.infants > 0) {
            add("기저귀", p.infants * (p.nights + 1), "묶음", "영유아 1명당 하루치 묶음 1개 기준이에요. 평소 사용량에 맞게 수량을 조정해 주세요.", recoverable = false)
            add("영유아 식사용품", p.infants, reason = "영유아 ${p.infants}명의 평소 식사 방식에 맞는 용품을 직접 골라 주세요.")
        }
        // Excluded rows also count as a deliberate decision; never re-add them automatically.
        val known = existing.map { key(it.name) }.toSet()
        return rows.filter { it.key !in known }
    }
}
