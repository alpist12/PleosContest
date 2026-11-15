package ai.pleos.contest

import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.ConfirmContext
import ai.pleos.contest.domain.ItemState
import ai.pleos.contest.ui.withJosa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 상태 전이만 본다. 저장(org.json)은 Context를 붙이지 않으면 건너뛴다. */
class TripStoreTest {

    // 저장소는 싱글턴이라 테스트마다 시드로 돌려놓는다.
    @Before fun reset() = TripStore.reset()

    @Test fun item_lifecycle_preserves_flags_and_undo_restores_deleted_item() {
        TripStore.saveItem(null, "  카메라  ", "지호", "트렁크", true, true)
        val added = TripStore.trip.items.last()
        assertEquals("카메라", added.name)
        TripStore.saveItem(added.id, "카메라", "엄마", "2열", false, false)
        val updated = TripStore.trip.items.last()
        assertEquals("엄마", updated.owner)
        assertFalse(updated.recoverable)
        TripStore.deleteItem(added.id)
        assertFalse(TripStore.trip.items.any { it.id == added.id })
        TripStore.undo()
        assertEquals(updated, TripStore.trip.items.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_item_is_rejected() { TripStore.saveItem(null, "  ", "", "", true, false) }

    @Test fun template_is_unchecked_and_can_be_undone_without_losing_trip_details() {
        val original = TripStore.trip
        TripStore.useTemplate("출근")
        assertEquals(original.destination, TripStore.trip.destination)
        assertEquals(5, TripStore.trip.pending.size)
        assertEquals(5, TripStore.trip.items.map { it.id }.distinct().size)
        TripStore.undo()
        assertEquals(original, TripStore.trip)
    }

    @Test fun arrival_and_return_are_separate_and_finish_requires_every_recovery() {
        TripStore.startArrival()
        TripStore.confirmRecovered(TripStore.trip.dropOffItems.map { it.id }.toSet())
        assertTrue(TripStore.trip.dropOffItems.all { it.recovered })
        TripStore.beginRecovery()
        assertTrue(TripStore.trip.recoverItems.none { it.recovered })
        assertTrue(runCatching { TripStore.finishTrip() }.isFailure)
        TripStore.confirmRecovered(TripStore.trip.items.map { it.id }.toSet())
        assertTrue(TripStore.trip.items.filterNot { it.recoverable }.none { it.recovered })
        TripStore.finishTrip()
        assertEquals(ai.pleos.contest.domain.TripStatus.CLOSED, TripStore.trip.status)
        TripStore.undo()
        assertEquals(ai.pleos.contest.domain.TripStatus.RETURN_CHECK, TripStore.trip.status)
    }

    @Test fun updating_trip_keeps_existing_items_and_confirmation() {
        val items = TripStore.trip.items
        TripStore.updateTrip("가을 캠핑", "춘천", "11월 20일 10:00")
        assertEquals("가을 캠핑", TripStore.trip.title)
        assertEquals(items, TripStore.trip.items)
    }

    @Test
    fun confirm_records_user_confirmation_with_context() {
        val before = TripStore.trip.vehiclePending.size
        val target = TripStore.trip.vehiclePending.first()

        TripStore.toggleConfirm(target.id, ConfirmContext.VEHICLE)
        val after = TripStore.trip.items.first { it.id == target.id }

        assertTrue(after.loaded)
        assertFalse(after.prepared)
        assertEquals(ConfirmContext.VEHICLE, after.confirmedIn)
        assertEquals(before - 1, TripStore.trip.vehiclePending.size)

        TripStore.undo()
        assertEquals(before, TripStore.trip.vehiclePending.size)
        assertFalse(TripStore.canUndo)
    }

    @Test
    fun confirm_all_is_undoable_in_one_step() {
        val pending = TripStore.trip.vehiclePending.size
        assertTrue(pending > 0)

        TripStore.confirmAllPending(ConfirmContext.VEHICLE)
        assertEquals(0, TripStore.trip.vehiclePending.size)

        TripStore.undo()
        assertEquals(pending, TripStore.trip.vehiclePending.size)
    }

    @Test
    fun return_check_only_marks_recoverable_items() {
        TripStore.startReturnCheck()
        val trip = TripStore.trip

        assertTrue(trip.recoverItems.all { it.state == ItemState.RETURN_PENDING || it.recovered })
        assertTrue(trip.items.filterNot { it.recoverable }.none { it.state == ItemState.RETURN_PENDING })

        val one = trip.recoverItems.first()
        TripStore.toggleRecovered(one.id)
        assertTrue(TripStore.trip.items.first { it.id == one.id }.recovered)
    }

    @Test
    fun recovery_batch_is_undoable_in_one_step() {
        TripStore.startReturnCheck()
        val before = TripStore.trip
        TripStore.confirmRecovered(before.recoverItems.map { it.id }.toSet())
        assertTrue(TripStore.trip.recoverItems.all { it.recovered })
        TripStore.undo()
        assertEquals(before, TripStore.trip)
    }

    @Test
    fun arrival_confirmation_does_not_complete_the_return_check() {
        TripStore.startReturnCheck()
        TripStore.confirmRecovered(TripStore.trip.dropOffItems.map { it.id }.toSet())
        assertTrue(TripStore.trip.dropOffItems.all { it.recovered })
        TripStore.beginRecovery()
        assertTrue(TripStore.trip.recoverItems.none { it.recovered })
        assertTrue(TripStore.trip.recoverItems.all { it.state == ItemState.RETURN_PENDING })
    }

    /** 홈 화면 CTA가 "랜턴과 보조배터리"처럼 읽히게 하는 조사 선택. */
    @Test
    fun josa_follows_the_final_consonant() {
        assertEquals("랜턴과 보조배터리", withJosa("랜턴", "보조배터리"))
        assertEquals("텐트와 랜턴", withJosa("텐트", "랜턴"))
        assertEquals("iPhone와 지갑", withJosa("iPhone", "지갑"))
    }

    @Test fun mobile_preparation_does_not_mark_vehicle_loaded_or_start_travel() {
        val loaded = TripStore.trip.loadedCount
        TripStore.confirmAllPending(ConfirmContext.MOBILE)
        assertEquals(0, TripStore.trip.pending.size)
        assertEquals(loaded, TripStore.trip.loadedCount)
        assertEquals(ai.pleos.contest.domain.TripStatus.DEPARTURE_CHECK, TripStore.trip.status)
        TripStore.confirmAllPending(ConfirmContext.VEHICLE)
        assertEquals(0, TripStore.trip.vehiclePending.size)
    }

    @Test fun editing_object_or_location_invalidates_only_vehicle_loading() {
        val item = TripStore.trip.items.first { it.loaded }
        TripStore.saveItem(item.id, item.name, item.owner, "새 위치", item.recoverable, item.dropOff)
        assertTrue(TripStore.trip.items.first { it.id == item.id }.prepared)
        assertFalse(TripStore.trip.items.first { it.id == item.id }.loaded)
    }

    @Test fun observation_edits_and_model_replacement_are_undoable() {
        val mission = TripStore.trip.observations.first()
        TripStore.saveObservation(mission.id, true, "  세 개의 날개  ")
        assertEquals("세 개의 날개", TripStore.trip.observations.first().note)
        assertTrue(TripStore.trip.observations.first().done)
        val before = TripStore.trip
        TripStore.selectLandmark("tower")
        assertTrue(TripStore.trip.observations.none { it.done })
        TripStore.undo()
        assertEquals(before, TripStore.trip)
    }

    @Test fun models_have_depth_and_rotation_changes_projected_geometry() {
        for (kind in listOf("windmill", "tower")) {
            val vertices = ai.pleos.contest.domain.landmarkGeometry(kind).flatMap { it.points }
            assertTrue(vertices.map { it.z }.distinct().size > 1)
            assertTrue(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            val v = ai.pleos.contest.domain.Vertex(1f, 0f, 0f)
            val turned = ai.pleos.contest.domain.rotateVertex(v, 90f, 0f)
            assertEquals(0f, turned.x, .001f)
            assertEquals(-1f, turned.z, .001f)
        }
    }

    @Test fun live_restriction_cannot_be_bypassed_by_simulation() {
        val gate = ai.pleos.contest.vehicle.DrivingGate
        gate.update(true); gate.toggle()
        assertTrue(gate.driving)
        gate.update(null)
        assertTrue(gate.driving)
        gate.toggle(); assertTrue(gate.parked)
        gate.update(false); assertTrue(gate.parked)
        gate.update(true); assertTrue(gate.driving)
        gate.update(null)
    }
}
