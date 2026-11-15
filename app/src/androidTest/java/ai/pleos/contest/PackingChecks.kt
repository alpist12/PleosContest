package ai.pleos.contest

import android.app.Instrumentation
import android.content.*
import ai.pleos.contest.data.SyncEngine
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Disposable AVD only. Verifies real Android action delivery and callback, not Gleo speech recognition. */
object PackingChecks {
    fun run(test: Instrumentation, context: Context) {
        check(!SyncEngine.configured) { "Packing QA requires an unpaired disposable installation" }
        val original = TripStore.snapshot()
        val replies = LinkedBlockingQueue<JSONObject>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getStringExtra("packageName") == context.packageName)
                    replies.offer(JSONObject(intent.getStringExtra("response")!!))
            }
        }
        context.registerReceiver(receiver, IntentFilter("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"), Context.RECEIVER_EXPORTED)
        try {
            val schema = JSONObject(context.assets.open("schema.json").bufferedReader().use { it.readText() })
            check(schema.getJSONArray("actionCalls").getJSONObject(0).getJSONObject("function").getString("name") == RecommendationActivity.ACTION)
            val p = PackingProfile("캠핑", 2, 2, 1, 1)
            val activity = test.startActivitySync(Intent(context, RecommendationActivity::class.java)
                .setAction(RecommendationActivity.ACTION).putExtra("ACTION_PARAM", p.json().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val reply = replies.poll(12, TimeUnit.SECONDS) ?: error("Missing Android callback")
            check(reply.getString("status") == "success") { "Expected recommendations, got ${reply.getString("status")}" }
            check(JSONArray(reply.getString("data")).length() > 0)
            check(TripStore.snapshot().toString() == original.toString()) { "Voice query mutated trip" }
            test.runOnMainSync { activity.finish() }
            test.waitForIdleSync()

            val invalid = test.startActivitySync(Intent(context, RecommendationActivity::class.java)
                .setAction(RecommendationActivity.ACTION).putExtra("ACTION_PARAM", "{}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            check(replies.poll(12, TimeUnit.SECONDS)?.getString("status") == "asking")
            test.runOnMainSync { invalid.finish() }
            test.waitForIdleSync()
            test.runOnMainSync {
                val keys = PackingRecommendations.suggest(p, TripStore.trip.items).map { it.key }.toSet()
                check(TripStore.addRecommendations(p, keys) > 0)
                val saved = JSONObject(java.io.File(context.filesDir, "trip.json").readText())
                check(saved.getJSONObject("packingProfile").getInt("nights") == 2)
                val fresh = TripStore.trip.items.drop(original.getJSONArray("items").length())
                check(fresh.all { !it.prepared && !it.loaded })
                check(TripStore.addRecommendations(p, keys) == 0)
                TripStore.undo()
                check(TripStore.snapshot().toString() == original.toString())
            }
        } finally {
            context.unregisterReceiver(receiver)
            test.runOnMainSync { TripStore.acceptSync(original, original); TripStore.clearSyncBase() }
        }
    }
}
