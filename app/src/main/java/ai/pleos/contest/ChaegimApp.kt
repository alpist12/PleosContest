package ai.pleos.contest

import android.app.Application
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.data.SyncEngine
import ai.pleos.contest.data.ReminderScheduler

class ChaegimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TripStore.attach(this)
        ReminderScheduler.init(this)
        SyncEngine.init(this)
        TripStore.onChanged = { local ->
            ReminderScheduler.reschedule()
            if (local) SyncEngine.request()
        }
        ReminderScheduler.reschedule()
    }
}
