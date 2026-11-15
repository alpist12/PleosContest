package ai.pleos.contest

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import android.view.WindowInsets as PlatformInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.ui.ArrivalScreen
import ai.pleos.contest.ui.ChaegimMobileTheme
import ai.pleos.contest.ui.ChaegimTheme
import ai.pleos.contest.ui.DepartureScreen
import ai.pleos.contest.ui.MobileApp
import ai.pleos.contest.ui.RecoverScreen

/**
 * 차량과 모바일을 한 APK에 담았다. 화면 너비로 어느 쪽인지 정한다.
 *
 * 차량 화면은 저장된 여정 단계에서 재개한다.
 */
class MainActivity : ComponentActivity() {
    private var carConnection: ai.pleos.contest.vehicle.CarRestrictionConnection? = null

    override fun onStart() {
        super.onStart()
        ai.pleos.contest.data.ReminderScheduler.reschedule()
    }

    private var bottomOverlap by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        ai.pleos.contest.data.SyncEngine.foreground(true)
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE))
            carConnection = ai.pleos.contest.vehicle.CarRestrictionConnection(this).also { it.start() }
    }
    override fun onPause() {
        carConnection?.stop(); carConnection = null
        ai.pleos.contest.data.SyncEngine.foreground(false)
        super.onPause()
    }

    private enum class Screen { DEPARTURE, ARRIVAL, RECOVER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 차량 디스플레이는 1280dp급 가로 화면이고 폰은 그보다 좁다.
        val debugMobilePreview = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("mobile_preview", false)
        val vehicle = !debugMobilePreview && (
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE) ||
                resources.configuration.smallestScreenWidthDp >= 600)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
                val metrics = windowManager.maximumWindowMetrics
                val bottom = metrics.windowInsets.getInsetsIgnoringVisibility(PlatformInsets.Type.navigationBars()).bottom
                val position = IntArray(2)
                window.decorView.getLocationOnScreen(position)
                // Pleos can consume inset values while its GNB still overlaps the app window.
                bottomOverlap = (position[1] + window.decorView.height - (metrics.bounds.bottom - bottom)).coerceAtLeast(0)
            }
        }

        setContent {
            if (!vehicle) {
                ChaegimMobileTheme {
                    val extra = with(LocalDensity.current) { (bottomOverlap - WindowInsets.systemBars.getBottom(this)).coerceAtLeast(0).toDp() }
                    Box(Modifier.fillMaxSize().padding(bottom = extra)) { MobileApp() }
                }
                return@setContent
            }

            val screen = when (TripStore.trip.status) {
                ai.pleos.contest.domain.TripStatus.ARRIVED -> Screen.ARRIVAL
                ai.pleos.contest.domain.TripStatus.RETURN_CHECK -> Screen.RECOVER
                else -> Screen.DEPARTURE
            }

            ChaegimTheme {
                var vehiclePage by rememberSaveable { mutableStateOf("check") }
                BackHandler(enabled = vehiclePage != "check") { vehiclePage = "check" }
                val density = LocalDensity.current
                val extra = with(density) { (bottomOverlap - WindowInsets.safeDrawing.getBottom(this)).coerceAtLeast(0).toDp() }
                Column(Modifier.fillMaxSize().background(ai.pleos.contest.ui.Tok.bg).safeDrawingPadding().padding(bottom = extra)) {
                    Row {
                        TextButton(onClick = { vehiclePage = "check" }) { Text("준비물 확인") }
                        TextButton(enabled = ai.pleos.contest.vehicle.DrivingGate.parked, onClick = {
                            startActivity(android.content.Intent(this@MainActivity, RecommendationActivity::class.java))
                        }) { Text("준비물 추천") }
                        TextButton(enabled = ai.pleos.contest.vehicle.DrivingGate.parked, onClick = { vehiclePage = "explore" }) { Text("목적지 3D 탐색") }
                        TextButton(onClick = { vehiclePage = "record" }) { Text("가족 여행 기록") }
                        TextButton(enabled = ai.pleos.contest.vehicle.DrivingGate.parked, onClick = { vehiclePage = "settings" }) { Text("연결 설정") }
                    }
                    Box(Modifier.weight(1f)) {
                    when (vehiclePage) {
                        "settings" -> if (ai.pleos.contest.vehicle.DrivingGate.parked) {
                            ChaegimMobileTheme {
                                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = ai.pleos.contest.ui.MTok.page, contentColor = ai.pleos.contest.ui.MTok.ink) {
                                    ai.pleos.contest.ui.SettingsScreen()
                                }
                            }
                        } else androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                            Text("차량 정책에 따라 연결 설정을 잠갔어요. 허용되는 상태에서 다시 열어 주세요.", Modifier.padding(androidx.compose.ui.unit.Dp(24f)))
                        }
                        "explore" -> ai.pleos.contest.ui.ExploreScreen(true, { vehiclePage = "check" }, { vehiclePage = "record" })
                        "record" -> ai.pleos.contest.ui.JourneyRecordScreen(true) { vehiclePage = "check" }
                        else ->
                    when (screen) {
                        Screen.DEPARTURE -> DepartureScreen(onArrive = {})
                        Screen.ARRIVAL -> ArrivalScreen(onDone = {})
                        Screen.RECOVER -> RecoverScreen(onFinish = {})
                    }
                    }
                    }
                }
            }
        }
    }
}
