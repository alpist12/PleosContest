package ai.pleos.contest

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ai.pleos.contest.data.SyncEngine
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.PackingProfile
import ai.pleos.contest.domain.PackingRecommendations
import ai.pleos.contest.ui.ChaegimMobileTheme
import ai.pleos.contest.ui.ChaegimTheme
import ai.pleos.contest.ui.RecommendationScreen
import ai.pleos.contest.vehicle.CarRestrictionConnection
import ai.pleos.contest.vehicle.DrivingGate
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Read-only Gleo query. Writing selected candidates always requires an in-app confirmation. */
class RecommendationActivity : ComponentActivity() {
    private var carConnection: CarRestrictionConnection? = null
    private val automotive get() = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private var responded = false
    private var bottomOverlap by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        SyncEngine.foreground(true)
        if (automotive) carConnection = CarRestrictionConnection(this).also { it.start() }
    }
    override fun onPause() {
        carConnection?.stop(); carConnection = null
        SyncEngine.foreground(false)
        super.onPause()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 30) window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val metrics = windowManager.maximumWindowMetrics
            val bottom = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
            val position = IntArray(2)
            window.decorView.getLocationOnScreen(position)
            bottomOverlap = (position[1] + window.decorView.height - (metrics.bounds.bottom - bottom)).coerceAtLeast(0)
        }
        responded = savedInstanceState?.getBoolean("responded") ?: false
        val gleo = intent.action == ACTION
        val parsed = runCatching {
            if (!gleo) TripStore.trip.packingProfile else {
                val direct = JSONObject()
                for (key in listOf("activity", "nights", "adults", "children", "infants")) {
                    @Suppress("DEPRECATION")
                    if (intent.hasExtra(key)) direct.put(key, intent.extras?.get(key))
                }
                GleoPackingRequest.parse(intent.getStringExtra("ACTION_PARAM") ?: intent.getStringExtra("actionParameters"), direct)
            }
        }
        setContent {
            val allowed = !automotive || DrivingGate.parked
            LaunchedEffect(Unit) {
                if (gleo && !responded) {
                    // Allow the asynchronous Car UX connection to establish, staying below Gleo's 7s timeout.
                    if (automotive) {
                        var waited = 0
                        while (DrivingGate.source == "신호 없음" && waited < 2000) { delay(100); waited += 100 }
                    }
                    when {
                        automotive && !DrivingGate.parked -> callback("fail", "Vehicle policy currently prevents opening packing recommendations.")
                        parsed.isFailure -> callback("asking", "Provide activity, nights, adults, children and infants within the supported ranges.")
                        else -> {
                            val rows = PackingRecommendations.suggest(parsed.getOrThrow(), TripStore.trip.items)
                            val data = JSONArray().apply { rows.forEach { put(JSONObject().put("name", it.name)
                                .put("quantity", it.quantity).put("unit", it.unit).put("reason", it.reason)) } }
                            callback("success", "Packing candidates are ready for review. No items have been added or marked as packed.", data.toString())
                        }
                    }
                    responded = true
                }
            }
            val content: @Composable () -> Unit = {
                val extra = with(LocalDensity.current) { (bottomOverlap - WindowInsets.safeDrawing.getBottom(this)).coerceAtLeast(0).toDp() }
                Surface(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = extra)) {
                    if (!allowed) Column(Modifier.padding(24.dp)) {
                        Text("차량 정책에 따라 추천 화면을 잠갔어요. 허용되는 상태에서 다시 열어 주세요.")
                        TextButton(onClick = { finish() }) { Text("돌아가기") }
                    } else RecommendationScreen(parsed.getOrNull() ?: TripStore.trip.packingProfile,
                        if (parsed.isFailure) "음성 요청의 활동·숙박일수·인원을 확인하지 못했어요. 아래에서 직접 선택해 주세요." else null,
                        vehicle = automotive, onBack = { finish() })
                }
            }
            if (automotive) ChaegimTheme(content) else ChaegimMobileTheme(content)
        }
    }
    private fun callback(status: String, message: String, data: String? = null) {
        val response = JSONObject().put("functionName", ACTION).put("status", status).put("message", message)
        data?.let { response.put("data", it) }
        // Only generic candidates are returned: no trip IDs, destination, assignees or family notes.
        sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
            .putExtra("packageName", packageName).putExtra("response", response.toString()))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("responded", responded)
        super.onSaveInstanceState(outState)
    }
    companion object { const val ACTION = "GET_PACKING_RECOMMENDATIONS" }
}

/** Current guide documents both flattened extras and ACTION_PARAM JSON (sometimes JSON-quoted). */
object GleoPackingRequest {
    fun parse(raw: String?, direct: JSONObject = JSONObject()): PackingProfile {
        val o = if (raw == null) direct else {
            require(raw.length <= 8192)
            val parser = org.json.JSONTokener(raw)
            val decoded = parser.nextValue()
            require(parser.nextClean() == '\u0000')
            when (decoded) {
                is JSONObject -> decoded
                is String -> JSONObject(decoded)
                else -> throw IllegalArgumentException("Expected action parameters")
            }
        }
        return PackingProfile.parse(o)
    }
}
