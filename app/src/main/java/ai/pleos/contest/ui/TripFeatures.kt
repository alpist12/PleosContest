package ai.pleos.contest.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.Item
import ai.pleos.contest.domain.TripStatus
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ai.pleos.contest.data.ReminderScheduler

@Composable
internal fun ItemEditorDialog(item: Item?, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(item?.name ?: "") }
    var owner by rememberSaveable { mutableStateOf(item?.owner ?: "") }
    var place by rememberSaveable { mutableStateOf(item?.place ?: "") }
    var recoverable by rememberSaveable { mutableStateOf(item?.recoverable ?: true) }
    var dropOff by rememberSaveable { mutableStateOf(item?.dropOff ?: true) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting) {
        AlertDialog(onDismissRequest = { deleting = false }, title = { Text("${item?.name} 삭제할까요?") },
            text = { Text("삭제 후 준비물 화면에서 되돌릴 수 있어요.") },
            confirmButton = { TextButton(onClick = { TripStore.deleteItem(item!!.id); dismiss() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("취소") } })
    } else AlertDialog(onDismissRequest = dismiss, title = { Text(if (item == null) "준비물 추가" else "준비물 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("물건 이름 (필수)") }, singleLine = true)
                OutlinedTextField(owner, { if (it.length <= 40) owner = it }, label = { Text("담당자") }, singleLine = true)
                OutlinedTextField(place, { if (it.length <= 80) place = it }, label = { Text("차량 보관 위치") }, singleLine = true)
                Row { Checkbox(dropOff, { dropOff = it }); Text("도착하면 차에서 꺼내기", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(recoverable, { recoverable = it }); Text("돌아갈 때 다시 챙기기", Modifier.padding(top = 12.dp)) }
                if (item != null) TextButton(onClick = { deleting = true }) { Text("준비물 삭제") }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            TripStore.saveItem(item?.id, name, owner, place, recoverable, dropOff); dismiss()
        }) { Text("저장") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}

@Composable
internal fun TripEditorScreen() {
    val trip = TripStore.trip
    var title by rememberSaveable(trip.id) { mutableStateOf(trip.title) }
    var destination by rememberSaveable(trip.id) { mutableStateOf(trip.destination) }
    var departureEpoch by rememberSaveable(trip.id, trip.departureEpochMillis) { mutableStateOf(trip.departureEpochMillis) }
    var lead by rememberSaveable(trip.id, trip.reminderMinutes) { mutableStateOf(trip.reminderMinutes) }
    var reminder by rememberSaveable(trip.id, trip.reminderEnabled) { mutableStateOf(trip.reminderEnabled) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminder = granted
        if (granted) ReminderScheduler.setReceives(true) else error = "알림 권한을 허용해야 예약 알림을 받을 수 있어요"
    }
    var template by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("일정 관리", style = MaterialTheme.typography.titleLarge)
        Text("이번 여정의 목적지와 출발 시간을 정해요.", color = MTok.muted)
        OutlinedTextField(title, { if (it.length <= 80) { title = it; saved = false } }, label = { Text("일정 이름 (필수)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(destination, { if (it.length <= 120) { destination = it; saved = false } }, label = { Text("목적지") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = {
            val initial = java.util.Calendar.getInstance().apply { if (departureEpoch > 0) timeInMillis = departureEpoch else add(java.util.Calendar.DAY_OF_MONTH, 1) }
            android.app.DatePickerDialog(context, { _, year, month, day ->
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    departureEpoch = java.util.Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                    saved = false; error = ""
                }, if (departureEpoch > 0) initial.get(java.util.Calendar.HOUR_OF_DAY) else 10, initial.get(java.util.Calendar.MINUTE), true).show()
            }, initial.get(java.util.Calendar.YEAR), initial.get(java.util.Calendar.MONTH), initial.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (departureEpoch > 0) "출발: " + java.text.SimpleDateFormat("yyyy년 M월 d일 HH:mm", java.util.Locale.KOREAN).format(java.util.Date(departureEpoch)) else "출발 날짜·시간 선택")
        }
        Row {
            Checkbox(reminder, onCheckedChange = { enable ->
                saved = false
                if (enable && android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else { reminder = enable; if (enable) ReminderScheduler.setReceives(true) }
            })
            Text("미확인 준비물 출발 알림", Modifier.padding(top = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 30, 60, 120).forEach { value ->
                FilterChip(selected = lead == value, onClick = { lead = value; saved = false }, label = { Text(if (value == 0) "출발 때" else "${value}분 전") })
            }
        }
        Text(ReminderScheduler.status, color = MTok.muted)
        if (error.isNotEmpty()) Text(error, color = MTok.amber)
        Button(enabled = title.isNotBlank(), onClick = {
            runCatching {
                val departure = if (departureEpoch > 0) java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREAN).format(java.util.Date(departureEpoch)) else trip.departAt
                TripStore.updateTrip(title, destination, departure, departureEpoch, lead, reminder)
                saved = true; error = ""
            }.onFailure { error = "출발 일시와 알림 시점을 확인해 주세요. 알림은 현재 시각보다 뒤여야 합니다." }
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (saved) "저장했어요" else "일정 저장") }
        HorizontalDivider()
        Button(onClick = { context.startActivity(android.content.Intent(context, ai.pleos.contest.RecommendationActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("우리 가족 준비물 추천") }
        Text("준비물 템플릿", style = MaterialTheme.typography.titleMedium)
        Text("선택한 활동에 맞는 기본 목록으로 바꿔요. 날씨·AI 기반 추천은 아니에요.", color = MTok.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("캠핑", "운동", "출근").forEach { type -> OutlinedButton(onClick = { template = type }) { Text(type) } }
        }
        Text("현재 ${trip.type} · 준비물 ${trip.activeCount}개", color = MTok.muted)
        if (TripStore.canUndo) TextButton(onClick = { TripStore.undo(); saved = false }) { Text("최근 변경 되돌리기") }
    }
    template?.let { type ->
        AlertDialog(onDismissRequest = { template = null }, title = { Text("$type 준비물로 바꿀까요?") },
            text = { Text("현재 준비물 ${trip.activeCount}개와 확인 상태를 새 목록으로 바꿉니다. 일정 이름과 목적지는 유지해요.") },
            confirmButton = { TextButton(onClick = { TripStore.useTemplate(type); template = null }) { Text("목록 바꾸기") } },
            dismissButton = { TextButton(onClick = { template = null }) { Text("취소") } })
    }
}

@Composable
internal fun MobileReturnScreen() {
    val trip = TripStore.trip
    val arrival = trip.status == TripStatus.ARRIVED
    val items = if (arrival) trip.dropOffItems else trip.recoverItems
    var finish by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (arrival) "도착 · 차에서 꺼내기" else "귀가 · 다시 챙기기", style = MaterialTheme.typography.titleLarge)
        when (trip.status) {
            TripStatus.CLOSED -> {
                Text("이번 여정 확인 완료", style = MaterialTheme.typography.titleLarge, color = MTok.sky)
                Text("모든 회수 대상을 직접 확인했어요.")
            }
            TripStatus.ARRIVED, TripStatus.RETURN_CHECK -> {
                Text("${items.count { !it.recovered }}개 확인 필요", color = MTok.amber)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(item.recovered, { TripStore.toggleRecovered(item.id) })
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(item.name)
                                Text(listOf(item.owner, item.place).filter { it.isNotBlank() }.joinToString(" · "), color = MTok.muted)
                            }
                        }
                        HorizontalDivider(color = MTok.lineSoft)
                    }
                    if (items.isEmpty()) Text("이 단계에 확인할 물건이 없어요.")
                }
                Button(onClick = { TripStore.confirmRecovered(items.map { it.id }.toSet()) }, enabled = items.any { !it.recovered }, modifier = Modifier.fillMaxWidth()) { Text("남은 물건 모두 확인") }
                Button(onClick = { if (arrival) TripStore.beginRecovery() else finish = true }, enabled = items.all { it.recovered }, modifier = Modifier.fillMaxWidth()) { Text(if (arrival) "귀가 준비로 이동" else "이번 여정 마치기") }
            }
            else -> {
                Text("도착하면 차에서 꺼낼 물건을 확인하고, 돌아가기 전 다시 챙겨요.", color = MTok.muted)
                Button(onClick = { TripStore.startArrival() }, modifier = Modifier.fillMaxWidth()) { Text("도착했어요 · 하차 확인 시작") }
                OutlinedButton(onClick = { TripStore.beginRecovery() }, modifier = Modifier.fillMaxWidth()) { Text("바로 귀가 준비하기") }
            }
        }
        if (TripStore.canUndo) TextButton(onClick = { TripStore.undo() }) { Text("최근 변경 되돌리기") }
        Text("물건이 실제로 있는지는 직접 확인해 주세요.", color = MTok.muted)
    }
    if (finish) AlertDialog(onDismissRequest = { finish = false }, title = { Text("이번 여정을 마칠까요?") },
        text = { Text("회수 대상 ${items.size}개를 모두 확인했어요.") },
        confirmButton = { TextButton(onClick = { TripStore.finishTrip(); finish = false }) { Text("여정 마치기") } },
        dismissButton = { TextButton(onClick = { finish = false }) { Text("취소") } })
}

@Composable
internal fun SettingsScreen() {
    var reset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정 · 앱 정보", style = MaterialTheme.typography.titleLarge)
        Text("챙김 CHAEGIM · 1.3")
        Text("저장 위치: 이 기기 내부\n앱을 종료해도 일정과 준비물 확인 상태를 유지합니다. 앱 삭제 시 데이터도 삭제됩니다.", color = MTok.muted)
        HorizontalDivider()
        SyncSettings()
        HorizontalDivider()
        DeviceReminderSettings()
        HorizontalDivider()
        Text("차량에서는 Car UX 정책을 읽고, 신호가 없으면 잠급니다. 디버그 시연으로 실제 차량 정책의 잠금을 해제할 수는 없습니다.", color = MTok.muted)
        OutlinedButton(onClick = { reset = true }) { Text("시연 데이터로 초기화") }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("시연 데이터로 초기화할까요?") },
        text = { Text("현재 일정과 준비물을 지우고 가족 캠핑 예제로 돌아갑니다. 동기화가 연결돼 있다면 공유 여정에도 반영됩니다.") },
        confirmButton = { TextButton(onClick = { TripStore.reset(); reset = false }) { Text("초기화") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("취소") } })
}
