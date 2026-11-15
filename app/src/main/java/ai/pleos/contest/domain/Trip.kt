package ai.pleos.contest.domain

/**
 * 개발계획서 v2 §06의 상태 설계를 그대로 옮긴 것.
 *
 * 핵심 원칙: "챙겼다"는 사용자가 표시한 상태이고, "실제로 차에 있다"는 센서로 검증된 상태가
 * 아니다. 준비와 적재는 별도로 저장하며, [ConfirmContext]는 마지막 조작 화면이다.
 */
enum class TripStatus { DRAFT, PREPARING, DEPARTURE_CHECK, TRAVELING, ARRIVED, RETURN_CHECK, CLOSED }

enum class ItemState { NOT_CHECKED, USER_CONFIRMED, EXCLUDED, RETURN_PENDING, RETURN_CONFIRMED }

/** 확인을 누른 화면. 기록용이며 "적재 감지"가 아니다. */
enum class ConfirmContext { NONE, MOBILE, VEHICLE }

data class Item(
    val id: String,
    val name: String,
    /** 차에서 어디를 봐야 하는지. 비어 있으면 화면에서 줄을 숨긴다. */
    val place: String = "",
    val owner: String = "",
    val state: ItemState = ItemState.NOT_CHECKED,
    val confirmedIn: ConfirmContext = ConfirmContext.NONE,
    /** 돌아갈 때 다시 챙겨야 하는 물건인지. */
    val recoverable: Boolean = true,
    /** 하차할 때 차에서 꺼낼 물건인지. */
    val dropOff: Boolean = false,
    /** 충돌 해결용. 동기화를 붙이면 작성자·시각과 함께 쓴다. */
    val revision: Int = 0,
    val prepared: Boolean = state == ItemState.USER_CONFIRMED && confirmedIn == ConfirmContext.MOBILE,
    val loaded: Boolean = state == ItemState.USER_CONFIRMED && confirmedIn == ConfirmContext.VEHICLE,
) {
    val confirmed: Boolean get() = prepared
    val recovered: Boolean get() = state == ItemState.RETURN_CONFIRMED
}

data class Trip(
    val id: String,
    val title: String,
    val type: String,
    /** "토 10:00" 처럼 화면에 그대로 쓰는 문자열. 알림을 붙일 때 epoch로 바꾼다. */
    val departAt: String,
    val destination: String,
    val status: TripStatus,
    val items: List<Item>,
    val landmark: String = "windmill",
    val observations: List<Observation> = defaultObservations("windmill"),
    val departureEpochMillis: Long = 0,
    val reminderMinutes: Int = 60,
    val reminderEnabled: Boolean = false,
    val packingProfile: PackingProfile = PackingProfile(),
) {
    val pending: List<Item> get() = items.filter { !it.prepared && it.state != ItemState.EXCLUDED }
    val vehiclePending: List<Item> get() = items.filter { !it.loaded && it.state != ItemState.EXCLUDED }
    val confirmedCount: Int get() = items.count { it.prepared && it.state != ItemState.EXCLUDED }
    val loadedCount: Int get() = items.count { it.loaded && it.state != ItemState.EXCLUDED }
    val activeCount: Int get() = items.count { it.state != ItemState.EXCLUDED }

    /** 하차할 때 차에서 꺼낼 물건. */
    val dropOffItems: List<Item> get() = items.filter { it.dropOff && it.state != ItemState.EXCLUDED }

    /** 돌아가기 전에 회수할 물건. */
    val recoverItems: List<Item> get() = items.filter { it.recoverable && it.state != ItemState.EXCLUDED }
}

data class Observation(val id: String, val title: String, val done: Boolean = false, val note: String = "")

fun defaultObservations(landmark: String): List<Observation> = when (landmark) {
    "windmill" -> listOf(Observation("blades", "발전기 날개의 수를 관찰해요"), Observation("wind", "바람의 방향을 가족과 이야기해요"))
    "tower" -> listOf(Observation("shape", "탑의 위와 아래 모양을 비교해요"), Observation("view", "주변에서 가장 인상적인 풍경을 기록해요"))
    else -> error("알 수 없는 탐색 모델")
}
