package ai.pleos.contest

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import ai.pleos.contest.data.*
import ai.pleos.contest.domain.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Test APK only: drives production sync and AlarmManager on two disposable Android instances. */
class SyncAlarmInstrumentation : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); args = arguments; start() }
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>(); val error = AtomicReference<Throwable>()
        runOnMainSync { try { value.set(block()) } catch (e: Throwable) { error.set(e) } }
        error.get()?.let { throw it }
        return value.get()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 45000
        while (System.currentTimeMillis() < deadline) {
            if (main(condition)) return
            Thread.sleep(500)
        }
        error("Timeout: $label")
    }
    private fun connect() {
        val invitation = File(targetContext.filesDir, "qa-invitation.txt").readText().trim()
        main { SyncEngine.connect(invitation) }
        await("server connection") { SyncEngine.lastSynced > 0 }
    }
    private fun settled() = await("acknowledgement") { TripStore.acknowledged()?.let { SyncMerge.equal(it, TripStore.snapshot()) } == true }
    override fun onStart() {
        val result = Bundle()
        try {
            when (args.getString("action")) {
                "packing" -> PackingChecks.run(this, targetContext)
                "phone-create" -> {
                    connect(); main { TripStore.reset() }; settled()
                    main {
                        TripStore.saveItem(null,"SyncProbe","phone","트렁크",true,true)
                        TripStore.toggleConfirm("i1",ConfirmContext.MOBILE)
                        TripStore.saveObservation(TripStore.trip.observations.first().id,true,"공유 메모")
                    }
                    settled()
                    check(main { !TripStore.trip.items.first { it.id=="i1" }.loaded })
                }
                "car-join" -> {
                    connect()
                    check(main { TripStore.trip.items.any { it.name=="SyncProbe" } })
                    check(main { TripStore.trip.observations.first().note=="공유 메모" })
                    main { TripStore.toggleConfirm("i1",ConfirmContext.VEHICLE) }; settled()
                    check(main { TripStore.trip.items.first { it.id=="i1" }.prepared })
                }
                "phone-read" -> {
                    // Do not request a refresh: the foreground phone app must already have persisted the car update.
                    check(main { TripStore.trip.items.first { it.id=="i1" }.loaded })
                    check(main { TripStore.trip.items.first { it.id=="i1" }.prepared })
                }
                "offline-edit" -> {
                    main {
                        SyncEngine.foreground(true)
                        TripStore.saveObservation(TripStore.trip.observations.first().id,true,"offline-note")
                    }
                    await("offline pending") { SyncEngine.status.contains("연결 대기") }
                    check(main { TripStore.trip.observations.first().note=="offline-note" })
                }
                "car-read-offline" -> {
                    main { SyncEngine.foreground(true) }
                    await("reconnected shared note") { TripStore.trip.observations.first().note=="offline-note" }
                }
                "alarm-arm" -> {
                    main {
                        ReminderScheduler.setReceives(true)
                        TripStore.setStatus(TripStatus.DEPARTURE_CHECK)
                        TripStore.setReminder(System.currentTimeMillis()+30000,0,true)
                    }
                    check(main { ReminderScheduler.exact() && ReminderScheduler.permitted() })
                    result.putLong("due",main { TripStore.trip.departureEpochMillis })
                }
                "alarm-check" -> {
                    val manager=targetContext.getSystemService(NotificationManager::class.java)
                    val notifications=manager.activeNotifications
                    check(notifications.size==1) { "Expected a real departure notification" }
                    val posted=notifications[0].postTime
                    main {
                        val key=ReminderPolicy.key(TripStore.trip)
                        repeat(2) { targetContext.sendBroadcast(Intent(targetContext,ReminderReceiver::class.java).setAction("ai.pleos.contest.DEPARTURE").putExtra("key",key)) }
                    }
                    Thread.sleep(9000)
                    check(manager.activeNotifications.single().postTime==posted) { "Duplicate notification" }
                    main { TripStore.confirmAllPending(ConfirmContext.MOBILE) }
                    await("completed notification cancelled") { manager.activeNotifications.isEmpty() }
                }
                "reset" -> { main { SyncEngine.foreground(true); TripStore.reset() }; settled(); main { SyncEngine.disconnect() } }
                else -> error("Unknown test action")
            }
            result.putString("result","PASS ${args.getString("action")}")
            finish(0,result)
        } catch (e: Throwable) {
            // Never include invitation or network request data in test output.
            result.putString("result","FAIL ${args.getString("action")}: ${e.javaClass.simpleName}: ${e.message?.takeIf { !it.contains("https") } ?: "test failure"}")
            finish(1,result)
        }
    }
}
