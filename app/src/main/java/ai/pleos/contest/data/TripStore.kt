package ai.pleos.contest.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.contest.domain.ConfirmContext
import ai.pleos.contest.domain.Item
import ai.pleos.contest.domain.ItemState
import ai.pleos.contest.domain.Trip
import ai.pleos.contest.domain.TripStatus
import ai.pleos.contest.domain.Observation
import ai.pleos.contest.domain.defaultObservations
import ai.pleos.contest.domain.PackingProfile
import ai.pleos.contest.domain.PackingRecommendations
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 여정 하나를 들고 있는 저장소. 화면은 여기만 본다.
 *
 * Local trip and acknowledged sync base are committed together for offline reconciliation.
 */
object TripStore {

    private const val FILE = "trip.json"

    var trip by mutableStateOf(seed())
        private set

    /** 한 단계 되돌리기. "전체 완료"를 되돌릴 수 있어야 한다는 요구(§09). */
    private var previous by mutableStateOf<Trip?>(null)
    val canUndo: Boolean get() = previous != null

    private var file: File? = null
    private var syncBase: JSONObject? = null
    var onChanged: ((Boolean) -> Unit)? = null
    var storageError by mutableStateOf<String?>(null)
        private set

    fun updateTrip(title: String, destination: String, departure: String,
        departureEpoch: Long = trip.departureEpochMillis, minutes: Int = trip.reminderMinutes, reminder: Boolean = trip.reminderEnabled) {
        require(title.isNotBlank() && title.length <= 80)
        require(destination.length <= 120 && departure.length <= 80)
        require(departureEpoch >= 0 && minutes in setOf(0, 30, 60, 120))
        require(!reminder || departureEpoch - minutes * 60_000L > System.currentTimeMillis()) { "알림 시각이 이미 지났어요" }
        edit { it.copy(title = title.trim(), destination = destination.trim(), departAt = departure.trim(),
            departureEpochMillis = departureEpoch, reminderMinutes = minutes, reminderEnabled = reminder) }
    }

    fun setReminder(departure: Long, minutes: Int, enabled: Boolean) {
        require(departure >= 0 && minutes in setOf(0, 30, 60, 120))
        require(!enabled || departure > System.currentTimeMillis())
        edit { it.copy(departureEpochMillis = departure, reminderMinutes = minutes, reminderEnabled = enabled,
            departAt = if (departure > 0) java.time.Instant.ofEpochMilli(departure).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm")) else it.departAt) }
    }

    fun snapshot(): JSONObject = toJson(trip)
    fun acknowledged(): JSONObject? = syncBase?.let { JSONObject(it.toString()) }
    fun acceptSync(document: JSONObject, acknowledged: JSONObject) {
        val next = fromJson(document)
        if (next == trip && syncBase?.let { SyncMerge.equal(it, acknowledged) } == true) return
        if (next != trip) { trip = next; previous = null }
        syncBase = JSONObject(acknowledged.toString())
        persist(false)
    }
    fun backupBeforeJoin() {
        val parent = file?.parentFile ?: error("저장소를 열지 못했습니다")
        File(parent, "trip-before-sync.json").writeText(snapshot().toString())
    }
    val hasSyncBackup: Boolean get() = file?.parentFile?.let { File(it, "trip-before-sync.json").exists() } == true
    fun restoreSyncBackup() {
        val backup = File(file!!.parentFile, "trip-before-sync.json")
        val restored = fromJson(JSONObject(backup.readText()))
        syncBase = null
        edit { restored }
    }
    fun clearSyncBase() { syncBase = null; persist() }

    fun saveItem(id: String?, name: String, owner: String, place: String, recoverable: Boolean, dropOff: Boolean) {
        require(name.isNotBlank() && name.length <= 80)
        require(owner.length <= 40 && place.length <= 80)
        edit { t ->
            val old = t.items.firstOrNull { it.id == id }
            require(id == null || old != null)
            val item = (old ?: Item(java.util.UUID.randomUUID().toString(), name.trim())).copy(
                name = name.trim(), owner = owner.trim(), place = place.trim(),
                recoverable = recoverable, dropOff = dropOff, revision = (old?.revision ?: -1) + 1,
                loaded = old?.loaded == true && old.name == name.trim() && old.place == place.trim(),
            )
            t.copy(items = if (old == null) t.items + item else t.items.map { if (it.id == id) item else it })
        }
    }

    fun deleteItem(id: String) = edit { it.copy(items = it.items.filterNot { item -> item.id == id }) }

    fun savePackingProfile(profile: PackingProfile) = edit { it.copy(packingProfile = profile) }

    /** Append only fresh selections; never replace checks, assignees or excluded items. */
    fun addRecommendations(profile: PackingProfile, selected: Set<String>): Int {
        require(trip.status in setOf(TripStatus.DRAFT, TripStatus.PREPARING, TripStatus.DEPARTURE_CHECK)) { "준비 단계에서만 추가할 수 있어요" }
        val suggestions = PackingRecommendations.suggest(profile, trip.items).filter { it.key in selected }
        require(trip.items.size + suggestions.size <= 250) { "준비물은 최대 250개까지 저장할 수 있어요" }
        if (suggestions.isEmpty()) return 0
        edit { t -> t.copy(packingProfile = profile, items = t.items + suggestions.map {
            ai.pleos.contest.domain.Item(java.util.UUID.nameUUIDFromBytes("${t.id}:packing:${it.key}".toByteArray(Charsets.UTF_8)).toString(), it.label,
                recoverable = it.recoverable, dropOff = true)
        }) }
        return suggestions.size
    }

    /** Replace the current list only after the user confirms; undo restores the previous trip. */
    fun useTemplate(type: String) {
        val names = when (type) {
            "캠핑" -> listOf("텐트", "랜턴", "보조배터리", "침낭", "방수포", "구급킷")
            "운동" -> listOf("운동화", "운동복", "수건", "물병")
            "출근" -> listOf("지갑", "휴대폰", "사원증", "노트북", "충전기")
            else -> error("알 수 없는 템플릿")
        }
        edit { t -> t.copy(type = type, status = TripStatus.PREPARING, packingProfile = t.packingProfile.copy(activity = type),
            items = names.map { Item(java.util.UUID.randomUUID().toString(), it, dropOff = true) }) }
    }

    fun finishTrip() {
        require(trip.status == TripStatus.RETURN_CHECK && trip.recoverItems.all { it.recovered })
        setStatus(TripStatus.CLOSED)
    }

    fun selectLandmark(id: String) {
        require(id in setOf("windmill", "tower"))
        edit { if (it.landmark == id) it else it.copy(landmark = id, observations = defaultObservations(id)) }
    }

    fun saveObservation(id: String, done: Boolean, note: String) {
        require(note.length <= 500)
        require(trip.observations.any { it.id == id })
        edit { it.copy(observations = it.observations.map { o -> if (o.id == id) o.copy(done = done, note = note.trim()) else o }) }
    }

    fun attach(context: Context) {
        val f = File(context.filesDir, FILE)
        file = f
        if (f.exists()) {
            runCatching {
                val saved = JSONObject(String(android.util.AtomicFile(f).readFully(), Charsets.UTF_8))
                trip = fromJson(saved); syncBase = saved.optJSONObject("_syncBase")
            }
                .onFailure {
                    runCatching { f.copyTo(File(f.parentFile, "trip-unreadable-${System.currentTimeMillis()}.json")) }
                        .onFailure { file = null }
                    storageError = "저장 파일을 읽지 못했습니다. 기존 파일은 보존됩니다."
                }
        }
    }

    // ---- 쓰기 ----

    /** 확인 표시를 켜거나 끈다. 어디서 눌렀는지 함께 기록한다. */
    fun toggleConfirm(itemId: String, where: ConfirmContext) = edit { t ->
        require(where != ConfirmContext.NONE)
        t.copy(items = t.items.map { item ->
            if (item.id != itemId || item.state !in setOf(ItemState.NOT_CHECKED, ItemState.USER_CONFIRMED)) item
            else if (where == ConfirmContext.VEHICLE) item.copy(loaded = !item.loaded, confirmedIn = where, revision = item.revision + 1)
            else item.copy(
                prepared = !item.prepared,
                state = if (item.prepared) ItemState.NOT_CHECKED else ItemState.USER_CONFIRMED,
                confirmedIn = where,
                revision = item.revision + 1,
            )
        })
    }

    /** 남은 것을 한 번에 확인. 추가 확인 팝업 없이 진행하고 되돌리기로 받는다. */
    fun confirmAllPending(where: ConfirmContext) = edit { t ->
        require(where != ConfirmContext.NONE)
        t.copy(
            status = if (where == ConfirmContext.VEHICLE) TripStatus.TRAVELING else TripStatus.DEPARTURE_CHECK,
            items = t.items.map { item ->
                if (item.state !in setOf(ItemState.NOT_CHECKED, ItemState.USER_CONFIRMED)) item
                else if (where == ConfirmContext.VEHICLE) {
                    if (item.loaded) item else item.copy(loaded = true, confirmedIn = where, revision = item.revision + 1)
                }
                else if (item.prepared) item
                else item.copy(
                    prepared = true,
                    state = ItemState.USER_CONFIRMED,
                    confirmedIn = where,
                    revision = item.revision + 1,
                )
            },
        )
    }

    /** 회수 확인. 돌아가기 전 목록에서 쓴다. */
    fun toggleRecovered(itemId: String) = edit { t ->
        t.copy(items = t.items.map { item ->
            if (item.id != itemId || item.state == ItemState.EXCLUDED ||
                (t.status == TripStatus.ARRIVED && !item.dropOff) ||
                (t.status == TripStatus.RETURN_CHECK && !item.recoverable) ||
                t.status !in setOf(TripStatus.ARRIVED, TripStatus.RETURN_CHECK)) item
            else item.copy(
                state = if (item.recovered) ItemState.RETURN_PENDING else ItemState.RETURN_CONFIRMED,
                revision = item.revision + 1,
            )
        })
    }

    /** 도착하면 회수 대상을 모두 대기 상태로 돌린다. */
    fun startReturnCheck() = edit { t ->
        t.copy(
            status = TripStatus.RETURN_CHECK,
            items = t.items.map { item ->
                if (!item.recoverable || item.state == ItemState.EXCLUDED) item
                else if (item.recovered) item
                else item.copy(state = ItemState.RETURN_PENDING, revision = item.revision + 1)
            },
        )
    }

    fun setStatus(status: TripStatus) = edit { it.copy(status = status) }

    fun startArrival() = edit { t -> t.copy(status = TripStatus.ARRIVED, items = t.items.map {
        if (it.dropOff && it.state != ItemState.EXCLUDED) it.copy(state = ItemState.RETURN_PENDING, revision = it.revision + 1) else it
    }) }

    /** A batch is one edit so one undo restores the whole list. */
    fun confirmRecovered(ids: Set<String>) = edit { t ->
        t.copy(items = t.items.map { item ->
            if (item.id !in ids || item.recovered || item.state == ItemState.EXCLUDED ||
                (t.status == TripStatus.ARRIVED && !item.dropOff) ||
                (t.status == TripStatus.RETURN_CHECK && !item.recoverable) ||
                t.status !in setOf(TripStatus.ARRIVED, TripStatus.RETURN_CHECK)) item
            else item.copy(state = ItemState.RETURN_CONFIRMED, revision = item.revision + 1)
        })
    }

    /** Taking an item out of the car does not confirm bringing it home later. */
    fun beginRecovery() = edit { t ->
        t.copy(status = TripStatus.RETURN_CHECK, items = t.items.map { item ->
            if (!item.recoverable || item.state == ItemState.EXCLUDED) item
            else item.copy(state = ItemState.RETURN_PENDING, revision = item.revision + 1)
        })
    }

    /** 시드 상태로 되돌린다. 시연을 다시 시작할 때와 테스트에서 쓴다. */
    fun reset() {
        previous = null
        trip = seed()
        persist()
    }

    fun undo() {
        val p = previous ?: return
        previous = null
        trip = p
        persist()
    }

    private fun edit(f: (Trip) -> Trip) {
        val next = f(trip)
        if (next == trip) return
        previous = trip
        trip = next
        persist()
    }

    private fun persist(local: Boolean = true) {
        val f = file ?: return
        val atomic = android.util.AtomicFile(f)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(toJson(trip).put("_syncBase", syncBase ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            storageError = null
        } catch (_: Exception) {
            stream?.let { atomic.failWrite(it) }
            storageError = "변경 내용을 저장하지 못했습니다. 저장 공간을 확인해 주세요."
        }
        onChanged?.invoke(local)
    }

    // ---- JSON ----

    private fun toJson(t: Trip): JSONObject {
        val items = JSONArray()
        t.items.forEach { i ->
            items.put(
                JSONObject()
                    .put("id", i.id)
                    .put("name", i.name)
                    .put("place", i.place)
                    .put("owner", i.owner)
                    .put("state", i.state.name)
                    .put("confirmedIn", i.confirmedIn.name)
                    .put("recoverable", i.recoverable)
                    .put("dropOff", i.dropOff)
                    .put("revision", i.revision)
                    .put("prepared", i.prepared)
                    .put("loaded", i.loaded)
            )
        }
        return JSONObject()
            .put("id", t.id)
            .put("title", t.title)
            .put("type", t.type)
            .put("departAt", t.departAt)
            .put("destination", t.destination)
            .put("status", t.status.name)
            .put("departureEpochMillis", t.departureEpochMillis)
            .put("reminderMinutes", t.reminderMinutes)
            .put("reminderEnabled", t.reminderEnabled)
            .put("packingProfile", t.packingProfile.json())
            .put("items", items)
            .put("landmark", t.landmark)
            .put("observations", JSONArray().apply { t.observations.forEach { o ->
                put(JSONObject().put("id", o.id).put("title", o.title).put("done", o.done).put("note", o.note))
            } })
    }

    private fun fromJson(o: JSONObject): Trip {
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { idx ->
            val i = arr.getJSONObject(idx)
            Item(
                id = i.getString("id"),
                name = i.getString("name"),
                place = i.optString("place"),
                owner = i.optString("owner"),
                state = enumValueOf(i.optString("state"), ItemState.NOT_CHECKED),
                confirmedIn = enumValueOf(i.optString("confirmedIn"), ConfirmContext.NONE),
                recoverable = i.optBoolean("recoverable", true),
                dropOff = i.optBoolean("dropOff", false),
                revision = i.optInt("revision"),
                prepared = i.optBoolean("prepared", i.optString("state") == "USER_CONFIRMED" && i.optString("confirmedIn") == "MOBILE"),
                loaded = i.optBoolean("loaded", i.optString("state") == "USER_CONFIRMED" && i.optString("confirmedIn") == "VEHICLE"),
            )
        }
        return Trip(
            id = o.getString("id"),
            title = o.getString("title"),
            type = o.optString("type"),
            departAt = o.optString("departAt"),
            destination = o.optString("destination"),
            status = enumValueOf(o.optString("status"), TripStatus.PREPARING),
            items = items,
            departureEpochMillis = o.optLong("departureEpochMillis", 0).coerceAtLeast(0),
            reminderMinutes = o.optInt("reminderMinutes", 60).takeIf { it in setOf(0, 30, 60, 120) } ?: 60,
            reminderEnabled = o.optBoolean("reminderEnabled", false),
            packingProfile = o.optJSONObject("packingProfile")?.let { runCatching { PackingProfile.parse(it) }.getOrNull() }
                ?: PackingProfile(activity = o.optString("type").takeIf { it in PackingProfile.activities } ?: "나들이"),
            landmark = o.optString("landmark", "windmill").takeIf { it in setOf("windmill", "tower") } ?: "windmill",
            observations = o.optJSONArray("observations")?.let { a -> (0 until a.length()).map { idx ->
                val obs = a.getJSONObject(idx)
                Observation(obs.getString("id"), obs.getString("title"), obs.optBoolean("done"), obs.optString("note"))
            } } ?: defaultObservations(o.optString("landmark", "windmill").takeIf { it == "tower" } ?: "windmill"),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOf(name: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    // ---- 시드 ----

    /** "가족 캠핑" 템플릿. 시안과 같은 12개이고, 랜턴·보조배터리만 미확인으로 시작한다. */
    private fun seed(): Trip {
        fun item(
            id: String,
            name: String,
            owner: String,
            place: String = "",
            confirmed: Boolean = true,
            recoverable: Boolean = true,
            dropOff: Boolean = false,
        ) = Item(
            id = id,
            name = name,
            owner = owner,
            place = place,
            state = if (confirmed) ItemState.USER_CONFIRMED else ItemState.NOT_CHECKED,
            confirmedIn = if (confirmed) ConfirmContext.MOBILE else ConfirmContext.NONE,
            recoverable = recoverable,
            dropOff = dropOff,
            prepared = confirmed,
            loaded = confirmed,
        )
        return Trip(
            id = "trip-camping",
            title = "가족 캠핑",
            type = "캠핑",
            departAt = "토 10:00",
            destination = "강원도 평창",
            status = TripStatus.DEPARTURE_CHECK,
            items = listOf(
                item("i1", "랜턴", "아빠", "트렁크", confirmed = false, dropOff = true),
                item("i2", "보조배터리", "지호", "센터 콘솔", confirmed = false, dropOff = true),
                item("i3", "텐트", "아빠", "트렁크", dropOff = true),
                item("i4", "방수포", "아빠", "트렁크", dropOff = true),
                item("i5", "캠핑 의자 4개", "엄마", "트렁크"),
                item("i6", "아이스박스", "아빠", "2열 바닥"),
                item("i7", "침낭 4개", "아빠", "트렁크"),
                item("i8", "구급킷", "엄마", "글로브 박스"),
                item("i9", "아이 담요", "엄마", "2열 좌석"),
                item("i10", "수건 4장", "하은", "트렁크"),
                item("i11", "물 2L", "엄마", "", recoverable = false),
                item("i12", "쓰레기봉투", "하은", "", recoverable = false),
            ),
        )
    }
}
