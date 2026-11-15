package ai.pleos.contest

import ai.pleos.contest.data.SyncMerge
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.ReminderPolicy
import ai.pleos.contest.domain.TripStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncMergeTest {
    @Before fun reset() { TripStore.reset(); TripStore.clearSyncBase() }
    private fun copy(json: JSONObject) = JSONObject(json.toString())
    @Test fun json_number_types_do_not_create_spurious_sync_writes() {
        val document = TripStore.snapshot()
        assertTrue(SyncMerge.equal(document,copy(document)))
        assertTrue(SyncMerge.equal(JSONObject().put("n",0L),JSONObject("{\"n\":0}")))
    }
    @Test fun independent_phone_and_vehicle_edits_survive() {
        val base = TripStore.snapshot()
        val local = copy(base); local.getJSONArray("items").getJSONObject(0).put("prepared",true)
        val remote = copy(base); remote.getJSONArray("items").getJSONObject(0).put("loaded",true)
        val merged = SyncMerge.merge(base,local,remote).getJSONArray("items").getJSONObject(0)
        assertTrue(merged.getBoolean("prepared")); assertTrue(merged.getBoolean("loaded"))
    }
    @Test fun deleted_row_is_not_resurrected_by_offline_check() {
        val base = TripStore.snapshot(); val local=copy(base); val remote=copy(base)
        local.getJSONArray("items").getJSONObject(0).put("prepared",true)
        remote.getJSONArray("items").remove(0)
        assertEquals(base.getJSONArray("items").length()-1,SyncMerge.merge(base,local,remote).getJSONArray("items").length())
    }
    @Test fun rename_invalidates_concurrent_vehicle_confirmation() {
        val base = TripStore.snapshot(); val local=copy(base); val remote=copy(base)
        local.getJSONArray("items").getJSONObject(0).put("name","다른 랜턴")
        remote.getJSONArray("items").getJSONObject(0).put("loaded",true)
        assertFalse(SyncMerge.merge(base,local,remote).getJSONArray("items").getJSONObject(0).getBoolean("loaded"))
    }
    @Test fun later_local_edit_survives_an_upload_acknowledgement() {
        val before=TripStore.snapshot(); val current=copy(before); val acknowledged=copy(before)
        current.put("destination","춘천"); acknowledged.getJSONArray("items").getJSONObject(0).put("loaded",true)
        val merged=SyncMerge.merge(before,current,acknowledged)
        assertEquals("춘천",merged.getString("destination")); assertTrue(merged.getJSONArray("items").getJSONObject(0).getBoolean("loaded"))
    }
    @Test fun sync_roundtrip_preserves_schedule_and_observations() {
        val json=TripStore.snapshot().put("departureEpochMillis",2000000000000L).put("reminderEnabled",true)
        json.getJSONArray("observations").getJSONObject(0).put("note","가족의 발견")
        TripStore.acceptSync(json,json)
        assertEquals(2000000000000L,TripStore.trip.departureEpochMillis)
        assertEquals("가족의 발견",TripStore.trip.observations.first().note)
        assertTrue(SyncMerge.equal(json,TripStore.acknowledged()))
    }
    @Test fun reminders_only_include_pending_preparation_before_departure() {
        val trip=TripStore.trip.copy(departureEpochMillis=2000000000000L,reminderMinutes=60,reminderEnabled=true)
        assertEquals(1999996400000L,ReminderPolicy.at(trip))
        assertNull(ReminderPolicy.at(trip.copy(status=TripStatus.TRAVELING)))
        assertNull(ReminderPolicy.at(trip.copy(items=trip.items.map { it.copy(prepared=true) })))
        assertNull(ReminderPolicy.at(trip.copy(reminderEnabled=false)))
        assertNotEquals(ReminderPolicy.key(trip),ReminderPolicy.key(trip.copy(reminderMinutes=30)))
    }
}
