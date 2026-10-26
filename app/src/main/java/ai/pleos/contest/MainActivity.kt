package ai.pleos.contest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener

/** 화면에 띄울 문 목록. 차종에 따라 지원 안 하는 문은 ERROR_NOT_SUPPORTED_AREA로 떨어진다. */
private val DOORS = listOf(
    "운전석" to DoorValue.ROW_1_LEFT,
    "조수석" to DoorValue.ROW_1_RIGHT,
    "2열 좌" to DoorValue.ROW_2_LEFT,
    "2열 우" to DoorValue.ROW_2_RIGHT,
    "트렁크" to DoorValue.REAR,
)

private data class DoorStatus(val position: String = "…", val lock: String = "…")

class MainActivity : ComponentActivity() {

    private lateinit var vehicle: Vehicle
    private val status = mutableStateMapOf<DoorValue, DoorStatus>()
    private val listeners = mutableListOf<Pair<DoorArea, DoorPositionListener>>()

    /** DOOR_LOCK이 받아 주는 area. 쪼개지 말고 이 객체 그대로 다시 넘겨야 한다. */
    private var lockAreas by mutableStateOf<List<DoorArea>>(emptyList())

    /** 문마다 열림 위치가 가질 수 있는 범위. 이게 없으면 쓰기 버튼을 못 누른다. */
    private val posBounds = mutableStateMapOf<DoorValue, AreaBounds<Int>>()

    /** 마지막 쓰기 결과. 주행 중 제한 같은 거부 사유가 여기로 온다. */
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(this)
        vehicle.initialize()

        setContent {
            MaterialTheme {
                Surface {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("문 상태", style = MaterialTheme.typography.headlineMedium)

                        DOORS.forEach { (label, door) ->
                            val s = status[door] ?: DoorStatus()
                            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("열림 ${s.position} · 잠금 ${s.lock}")
                                        Row(Modifier.padding(start = 24.dp)) {
                                            OutlinedButton(
                                                onClick = { moveDoor(label, door, open = true) },
                                                enabled = posBounds[door] != null,
                                            ) { Text("열기") }
                                            OutlinedButton(
                                                onClick = { moveDoor(label, door, open = false) },
                                                enabled = posBounds[door] != null,
                                                modifier = Modifier.padding(start = 8.dp),
                                            ) { Text("닫기") }
                                        }
                                    }
                                }
                            }
                        }

                        Row(Modifier.padding(top = 12.dp)) {
                            Button(
                                onClick = { setLock(true) },
                                enabled = lockAreas.isNotEmpty(),
                            ) { Text("전체 잠금") }
                            Button(
                                onClick = { setLock(false) },
                                enabled = lockAreas.isNotEmpty(),
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("전체 해제") }
                            // initialize()는 서비스 바인딩이 끝났다는 신호를 주지 않는다.
                            // 첫 읽기가 ERROR_SERVICE_CONNECTION_FAILURE로 떨어지면 이걸 누른다.
                            OutlinedButton(
                                onClick = ::refresh,
                                modifier = Modifier.padding(start = 24.dp),
                            ) { Text("다시 읽기") }
                        }

                        if (message.isNotEmpty()) {
                            Text(message, Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
        }
        refresh()
    }

    /** 문마다 현재 열림 위치를 읽고, 잠금은 이 차가 지원하는 문에만 물어본다. */
    private fun refresh() {
        DOORS.forEach { (_, door) ->
            vehicle.getDoor().getCurrentDoorPosition(
                DoorArea(door),
                { pos -> update(door) { it.copy(position = pos?.toString() ?: "없음") } },
                { e -> update(door) { it.copy(position = e.message ?: "실패") } },
            )
        }
        // 쓰기에 넣을 값의 범위. 문마다 완전 개방 값이 다를 수 있어 하드코딩하지 않는다.
        vehicle.getDoor().checkDoorPositionCapabilities(
            { _: CapabilityConfig, bounds: Map<DoorArea, AreaBounds<Int>> ->
                posBounds.clear()
                bounds.forEach { (area, b) -> area.values.forEach { posBounds[it] = b } }
            },
            { e -> message = "위치 범위 조회 실패: ${e.message}" },
        )
        // DOOR_LOCK은 문별 area를 따로 받지 않는다. 지원 목록이 돌려준 area를
        // 쪼개지 말고 그대로 넘겨야 한다 (문 하나로 다시 만들면 area ID가 어긋남).
        vehicle.getDoor().checkDoorLockCapabilities(
            { _: CapabilityConfig, areas: List<DoorArea> ->
                lockAreas = areas
                val supported = areas.flatMap { it.values.toList() }.toSet()
                DOORS.map { it.second }.filterNot { it in supported }
                    .forEach { door -> update(door) { it.copy(lock = "미지원") } }
                areas.forEach { area ->
                    vehicle.getDoor().getDoorLockState(
                        area,
                        { locked ->
                            val text = if (locked == true) "잠김" else "해제"
                            area.values.forEach { d -> update(d) { it.copy(lock = text) } }
                        },
                        { e ->
                            area.values.forEach { d -> update(d) { it.copy(lock = e.message ?: "실패") } }
                        },
                    )
                }
            },
            { e -> DOORS.forEach { (_, door) -> update(door) { it.copy(lock = e.message ?: "실패") } } },
        )
    }

    /** 문을 끝까지 열거나 닫는다. 중간값을 쓰려면 bounds 사이 아무 값이나 넣으면 된다. */
    private fun moveDoor(label: String, door: DoorValue, open: Boolean) {
        val bounds = posBounds[door] ?: run {
            message = "$label: 위치 범위를 아직 못 받았다"
            return
        }
        val target = if (open) bounds.maxValue else bounds.minValue
        message = "$label ${if (open) "열기" else "닫기"} 요청…"
        vehicle.getDoor().setDoorPosition(
            DoorArea(door),
            target,
            { _, value -> message = "$label 위치 $value" },
            { e -> message = "$label 실패: ${e.message}" },
        )
    }

    /** 잠금은 area 단위라 지원 area 전체에 건다. */
    private fun setLock(locked: Boolean) {
        message = "${if (locked) "잠금" else "해제"} 요청…"
        lockAreas.forEach { area ->
            vehicle.getDoor().setDoorLock(
                area,
                locked,
                { _, value ->
                    val text = if (value == true) "잠김" else "해제"
                    area.values.forEach { d -> update(d) { it.copy(lock = text) } }
                    message = "잠금 $text"
                },
                { e -> message = "잠금 실패: ${e.message}" },
            )
        }
    }

    /** 열림 위치는 주행 중에도 바뀌므로 리스너로 받아 둔다. */
    override fun onStart() {
        super.onStart()
        DOORS.forEach { (_, door) ->
            val area = DoorArea(door)
            val position = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) =
                    update(door) { it.copy(position = pos?.toString() ?: "없음") }

                override fun onFailed(e: Exception) =
                    update(door) { it.copy(position = e.message ?: "실패") }
            }
            vehicle.getDoor().registerDoorPosition(area, position)
            listeners += area to position
        }
    }

    override fun onStop() {
        super.onStop()
        listeners.forEach { (area, l) -> vehicle.getDoor().unregisterDoorPosition(area, l) }
        listeners.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        vehicle.release()
    }

    private fun update(door: DoorValue, f: (DoorStatus) -> DoorStatus) {
        status[door] = f(status[door] ?: DoorStatus())
    }
}
