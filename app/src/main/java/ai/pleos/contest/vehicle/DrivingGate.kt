package ai.pleos.contest.vehicle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Unknown platform state fails closed; debug simulation cannot override a platform lock. */
object DrivingGate {
    private var platformRestricted by mutableStateOf<Boolean?>(null)
    private var simulatedRestriction by mutableStateOf<Boolean?>(null)
    val driving: Boolean get() = platformRestricted == true || simulatedRestriction == true ||
        (platformRestricted == null && simulatedRestriction != false)
    val parked: Boolean get() = !driving
    val source: String get() = when {
        platformRestricted != null -> "차량 정책"
        simulatedRestriction != null -> "시연"
        else -> "신호 없음"
    }
    fun update(restricted: Boolean?) { platformRestricted = restricted; simulatedRestriction = null }
    fun toggle() { simulatedRestriction = !driving }
}
