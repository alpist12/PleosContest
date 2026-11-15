package ai.pleos.contest.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.vehicle.DrivingGate
import java.util.Locale

private class JourneySpeech(context: Context) {
    var ready by mutableStateOf(false)
    var message by mutableStateOf("음성 안내를 준비하고 있어요")
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }.build()
    private var engine: TextToSpeech? = null
    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val language = if (result == TextToSpeech.SUCCESS) engine?.setLanguage(Locale.KOREAN) else null
                ready = language != null && language >= TextToSpeech.LANG_AVAILABLE
                message = if (ready) "" else "한국어 음성을 사용할 수 없어요. 아래 설명을 읽어 주세요."
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (id == "guide") audio.abandonAudioFocusRequest(focus) }
                    @Deprecated("Platform callback") override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
                })
            }
        }
    }
    fun speak(text: String) {
        if (!ready) return
        message = ""
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            message = "지금은 음성을 재생할 수 없어요."; return
        }
        engine?.playSilentUtterance(250, TextToSpeech.QUEUE_FLUSH, "preroll")
        if (engine?.speak(text, TextToSpeech.QUEUE_ADD, null, "guide") == TextToSpeech.ERROR) {
            message = "음성 안내를 재생하지 못했어요."; stop()
        }
    }
    fun stop() { engine?.stop(); audio.abandonAudioFocusRequest(focus) }
    fun close() { stop(); engine?.shutdown(); engine = null }
}

@Composable
fun ExploreScreen(vehicle: Boolean, onBack: () -> Unit, onRecord: () -> Unit) {
    val trip = TripStore.trip
    val allowed = !vehicle || DrivingGate.parked
    var yaw by rememberSaveable(trip.landmark) { mutableStateOf(25f) }
    var pitch by rememberSaveable(trip.landmark) { mutableStateOf(-12f) }
    var zoom by rememberSaveable(trip.landmark) { mutableStateOf(1f) }
    var changeModel by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val speech = remember { JourneySpeech(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speech, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) speech.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech.close() }
    }
    LaunchedEffect(allowed) { if (!allowed) speech.stop() }
    val description = if (trip.landmark == "windmill")
        "풍력발전기는 바람의 힘을 이용해 전기를 만들어요. 세 개의 날개와 높은 기둥을 돌려 보세요. 실제 장소에서는 안전한 관람 구역에서 가족과 함께 관찰해요."
        else "전망탑은 높은 곳에서 주변 풍경을 살펴보는 건축물이에요. 가는 기둥과 넓은 전망 공간을 찾아보세요. 현장에서는 가족과 함께 주변 풍경을 관찰해요."
    Surface(Modifier.fillMaxSize(), color = if (vehicle) Tok.bg else MTok.page) {
        Column(Modifier.fillMaxSize().padding(if (vehicle) 28.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("‹ 돌아가기") }
                Text("목적지 3D 탐색", style = MaterialTheme.typography.titleLarge)
                if (vehicle) StatusBadge()
            }
            Text("${trip.destination} 여정 · 선택한 체험 모델", color = if (vehicle) Tok.muted else MTok.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = allowed, onClick = { changeModel = "windmill" }) { Text("풍력발전기") }
                OutlinedButton(enabled = allowed, onClick = { changeModel = "tower" }) { Text("전망탑") }
            }
            Text("모형 위를 직접 끌어 상하좌우 360° 회전 · 두 손가락으로 확대·축소", style = MaterialTheme.typography.bodySmall, color = if (vehicle) Tok.muted else MTok.muted)
            if (allowed) Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().semantics(mergeDescendants = true) { contentDescription = "3D 탐색 모델. 한 손가락으로 상하좌우 360도 회전, 두 손가락으로 확대와 축소" }) {
                AndroidView(
                factory = { LandmarkView(it) }, modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.kind = trip.landmark; view.interactionAllowed = allowed
                    view.yaw = yaw; view.pitch = pitch; view.zoom = zoom
                    view.onPoseChanged = { y, p, z -> yaw = y; pitch = p; zoom = z }
                    view.invalidate()
                },
                )
            } else Box(Modifier.fillMaxWidth().weight(1f).background(Tok.hairline).padding(24.dp)) {
                Text("차량 정책에 따라 3D 조작을 잠갔어요.\n허용되는 상태에서 탐색을 이어가세요.", color = Tok.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = allowed, onClick = { yaw = (yaw - 30f) % 360f }) { Text("회전") }
                OutlinedButton(enabled = allowed, onClick = { zoom = (zoom + .2f).coerceAtMost(1.8f) }) { Text("확대") }
                OutlinedButton(enabled = allowed, onClick = { yaw = 25f; pitch = -12f; zoom = 1f }) { Text("원래대로") }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = if (vehicle) Tok.ink else MTok.ink)
            Text("직접 제작한 체험용 모형이며 실제 명소를 정밀 재현한 모델은 아닙니다.", style = MaterialTheme.typography.bodySmall, color = if (vehicle) Tok.muted else MTok.muted)
            if (speech.message.isNotEmpty()) Text(speech.message, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = allowed && speech.ready, onClick = { speech.speak(description) }) { Text("설명 듣기") }
                OutlinedButton(onClick = { speech.stop() }) { Text("음성 멈추기") }
                Button(enabled = allowed, onClick = onRecord) { Text("관찰 미션 · 기록") }
            }
        }
    }
    changeModel?.let { id ->
        if (id == trip.landmark) { LaunchedEffect(id) { changeModel = null } }
        else AlertDialog(onDismissRequest = { changeModel = null }, title = { Text("탐색 모델을 바꿀까요?") },
            text = { Text("현재 관찰 미션과 메모는 새 모델의 미션으로 바뀝니다. 변경 후 되돌릴 수 있어요.") },
            confirmButton = { TextButton(enabled = allowed, onClick = { speech.stop(); TripStore.selectLandmark(id); changeModel = null }) { Text("모델 바꾸기") } },
            dismissButton = { TextButton(onClick = { changeModel = null }) { Text("취소") } })
    }
}

@Composable
fun JourneyRecordScreen(vehicle: Boolean, onBack: () -> Unit) {
    val trip = TripStore.trip
    val allowed = !vehicle || DrivingGate.parked
    var editId by remember { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(allowed) { if (!allowed) editId = null }
    Surface(Modifier.fillMaxSize(), color = if (vehicle) Tok.bg else MTok.page) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = allowed).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = onBack) { Text("‹ 돌아가기") }
            Text("가족 여행 기록", style = MaterialTheme.typography.titleLarge)
            Text("${trip.title} · ${trip.destination}")
            Text("모바일 준비 ${trip.confirmedCount}/${trip.activeCount} · 차량 적재 ${trip.loadedCount}/${trip.activeCount}")
            Text("귀가 회수 ${trip.recoverItems.count { it.recovered }}/${trip.recoverItems.size}")
            HorizontalDivider()
            Text("현지 관찰 미션", style = MaterialTheme.typography.titleMedium)
            Text("도착 후 안전한 관람 구역에서 가족과 함께 찾아봐요.")
            trip.observations.forEach { observation ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(observation.done, enabled = allowed, onCheckedChange = { TripStore.saveObservation(observation.id, it, observation.note) })
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Text(observation.title)
                        Text(observation.note.ifEmpty { "아직 남긴 발견이 없어요" }, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = allowed, onClick = { editId = observation.id; note = observation.note }) { Text("메모") }
                }
            }
            if (!allowed) Text("차량 정책에 따라 기록 편집을 잠갔어요.")
            if (TripStore.canUndo) TextButton(enabled = allowed, onClick = { TripStore.undo() }) { Text("최근 변경 되돌리기") }
            Text("이 기기에 저장된 현재 여정의 기록입니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    val observation = trip.observations.firstOrNull { it.id == editId }
    if (observation != null) AlertDialog(onDismissRequest = { editId = null }, title = { Text("가족이 발견한 것") },
        text = { OutlinedTextField(note, { if (it.length <= 500) note = it }, enabled = allowed, label = { Text("관찰 메모 · ${note.length}/500") }, minLines = 3) },
        confirmButton = { TextButton(enabled = allowed, onClick = { TripStore.saveObservation(observation.id, observation.done, note); editId = null }) { Text("기록 저장") } },
        dismissButton = { TextButton(onClick = { editId = null }) { Text("취소") } })
}
