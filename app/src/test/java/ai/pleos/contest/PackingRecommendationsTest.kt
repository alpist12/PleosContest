package ai.pleos.contest

import ai.pleos.contest.data.TripStore
import ai.pleos.contest.data.SyncMerge
import ai.pleos.contest.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PackingRecommendationsTest {
    @Before fun reset() = TripStore.reset()

    @Test fun quantities_follow_party_and_duration_without_inventing_weather() {
        val p = PackingProfile(nights = 2, adults = 2, children = 1, infants = 1)
        val rows = PackingRecommendations.suggest(p, emptyList()).associateBy { it.name }
        assertEquals(4, rows.getValue("침낭").quantity)
        assertEquals(8, rows.getValue("여벌 옷").quantity)
        assertEquals(3, rows.getValue("기저귀").quantity)
        assertEquals(1, rows.getValue("아이 담요").quantity)
        assertEquals(3, rows.getValue("쓰레기봉투").quantity)
        assertFalse(rows.getValue("기저귀").recoverable)
    }
    @Test fun day_trip_has_no_sleeping_or_infant_equipment_without_infants() {
        val names = PackingRecommendations.suggest(PackingProfile(nights = 0, children = 0), emptyList()).map { it.name }
        assertFalse(names.contains("침낭")); assertFalse(names.contains("여벌 옷"))
        assertFalse(names.contains("아이 담요")); assertFalse(names.contains("기저귀"))
    }
    @Test fun legacy_quantity_labels_and_excluded_rows_are_not_recommended_again() {
        val existing = listOf(Item("a", "캠핑 의자 4개"), Item("b", "수건 4장"), Item("c", "랜턴", state = ItemState.EXCLUDED))
        val names = PackingRecommendations.suggest(PackingProfile(), existing).map { it.name }
        assertFalse(names.contains("캠핑 의자")); assertFalse(names.contains("수건")); assertFalse(names.contains("랜턴"))
    }
    @Test fun selection_appends_once_preserves_checks_and_is_one_undo() {
        val original = TripStore.trip
        val p = PackingProfile(nights = 2, infants = 1)
        val keys = PackingRecommendations.suggest(p, original.items).map { it.key }.toSet()
        val count = TripStore.addRecommendations(p, keys)
        assertTrue(count > 0)
        assertEquals(original.items, TripStore.trip.items.take(original.items.size))
        assertTrue(TripStore.trip.items.drop(original.items.size).all { !it.prepared && !it.loaded && it.owner.isEmpty() })
        assertEquals(0, TripStore.addRecommendations(p, keys))
        TripStore.undo(); assertEquals(original, TripStore.trip)
    }
    @Test fun profile_roundtrips_and_old_documents_get_supported_defaults() {
        val p = PackingProfile("나들이", 0, 3, 1, 0)
        TripStore.savePackingProfile(p)
        val saved = TripStore.snapshot()
        TripStore.reset(); TripStore.acceptSync(saved, saved)
        assertEquals(p, TripStore.trip.packingProfile)
        saved.remove("packingProfile"); TripStore.acceptSync(saved, saved)
        assertEquals("캠핑", TripStore.trip.packingProfile.activity)
    }
    @Test fun gleo_accepts_both_documented_transports_and_json_quoted_values() {
        val p = PackingProfile("나들이", 0, 2, 1, 0)
        assertEquals(p, GleoPackingRequest.parse(p.json().toString()))
        assertEquals(p, GleoPackingRequest.parse(JSONObject.quote(p.json().toString())))
        assertEquals(p, GleoPackingRequest.parse(null, p.json()))
    }
    @Test fun invalid_profiles_and_voice_parameters_are_rejected() {
        val invalid = listOf("{}", "[]", "broken", PackingProfile().json().put("nights", -1).toString(),
            PackingProfile().json().put("adults", 0).toString(), PackingProfile().json().put("nights", 1.5).toString(),
            PackingProfile().json().put("children", "2").toString(),
            PackingProfile().json().put("activity", "폭우").toString(), " ".repeat(8193))
        invalid.forEach { assertTrue(it.take(60), runCatching { GleoPackingRequest.parse(it) }.isFailure) }
        assertTrue(runCatching { PackingProfile(adults = 12, children = 12) }.isFailure)
    }
    @Test fun closed_trip_cannot_be_modified_by_recommendations() {
        TripStore.setStatus(TripStatus.CLOSED)
        val before = TripStore.snapshot().toString()
        assertTrue(runCatching { TripStore.addRecommendations(PackingProfile(), setOf("여벌옷")) }.isFailure)
        assertEquals(before, TripStore.snapshot().toString())
    }
    @Test fun activity_changes_actual_candidates() {
        val names = PackingRecommendations.suggest(PackingProfile("출근", 0, 1, 0, 0), emptyList()).map { it.name }
        assertTrue(names.contains("사원증")); assertFalse(names.contains("텐트"))
    }
    @Test fun concurrent_family_additions_merge_without_duplicate_recommended_rows() {
        val base = TripStore.snapshot()
        val p = PackingProfile()
        val keys = PackingRecommendations.suggest(p, TripStore.trip.items).map { it.key }.toSet()
        TripStore.addRecommendations(p, keys)
        val phone = TripStore.snapshot()
        TripStore.reset(); TripStore.addRecommendations(p, keys)
        val car = TripStore.snapshot()
        val merged = SyncMerge.merge(base, phone, car)
        assertEquals(phone.getJSONArray("items").length(), merged.getJSONArray("items").length())
        assertTrue(SyncMerge.equal(phone, merged))
    }
}
