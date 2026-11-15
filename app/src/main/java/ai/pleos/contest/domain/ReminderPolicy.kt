package ai.pleos.contest.domain

object ReminderPolicy {
    fun key(trip: Trip): String = "${trip.id}:${trip.departureEpochMillis}:${trip.reminderMinutes}"
    fun at(trip: Trip): Long? = if (trip.reminderEnabled && trip.departureEpochMillis > 0 && trip.pending.isNotEmpty() &&
        trip.status in setOf(TripStatus.DRAFT, TripStatus.PREPARING, TripStatus.DEPARTURE_CHECK))
        trip.departureEpochMillis - trip.reminderMinutes * 60_000L else null
}
