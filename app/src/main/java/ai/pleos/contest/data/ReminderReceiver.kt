package ai.pleos.contest.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "ai.pleos.contest.DEPARTURE") { ReminderScheduler.reschedule(); return }
        val key = intent.getStringExtra("key") ?: return
        val pending = goAsync()
        val completed = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val finish = Runnable {
            if (completed.compareAndSet(false, true)) {
                try { ReminderScheduler.deliver(key) } finally { pending.finish() }
            }
        }
        // Refresh shared check marks when possible, but never hold a receiver beyond its deadline.
        handler.postDelayed(finish, 7500)
        SyncEngine.once { handler.removeCallbacks(finish); finish.run() }
    }
}
