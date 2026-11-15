package ai.pleos.contest.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.pleos.contest.data.SyncEngine
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.data.ReminderScheduler

@Composable
internal fun SyncSettings() {
    val context = LocalContext.current
    var invite by rememberSaveable { mutableStateOf("") }
    var connect by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    Text("가족 · 차량 자동 동기화", style = MaterialTheme.typography.titleMedium)
    Text(SyncEngine.status, color = MTok.sky)
    if (SyncEngine.lastSynced > 0) Text("마지막 연결: " + java.text.DateFormat.getTimeInstance().format(java.util.Date(SyncEngine.lastSynced)), color = MTok.muted)
    Text("앱을 열면 약 5초 간격으로 공유합니다. 닫혀 있을 때는 Android가 네트워크·배터리 상태에 따라 주기적으로 연결합니다.", color = MTok.muted)
    if (!SyncEngine.configured) {
        OutlinedTextField(invite, { if (it.length <= 2048) invite = it }, label = { Text("가족 초대 정보") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        TextButton(onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            invite = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.take(2048) ?: ""
        }) { Text("초대 정보 붙여넣기") }
        Button(enabled = invite.isNotBlank(), onClick = { connect = true }) { Text("공유 여정 연결") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { SyncEngine.once() }) { Text("지금 동기화") }
            OutlinedButton(onClick = {
                val clip = ClipData.newPlainText("챙김 가족 초대", SyncEngine.invitation())
                if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                note = "초대 정보를 복사했어요. 함께 사용할 가족에게만 전달해 주세요."
            }) { Text("초대 정보 복사") }
        }
        TextButton(onClick = { disconnect = true }) { Text("이 기기 연결 해제") }
    }
    if (TripStore.hasSyncBackup) TextButton(onClick = { restore = true }) { Text("연결 전 여정 복원") }
    if (note.isNotEmpty()) Text(note, color = MTok.muted)
    if (connect) AlertDialog(onDismissRequest = { connect = false }, title = { Text("공유 여정에 연결할까요?") },
        text = { Text("서버에 공유 여정이 있으면 그 여정을 불러옵니다. 없으면 현재 여정을 공유합니다. 기존 기기 여정은 복원용으로 보관해요. 일정·담당자·물건·관찰 메모가 연결된 서버에 저장됩니다.") },
        confirmButton = { TextButton(onClick = { SyncEngine.connect(invite); invite = ""; connect = false }) { Text("연결") } },
        dismissButton = { TextButton(onClick = { connect = false }) { Text("취소") } })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("이 기기만 연결을 해제할까요?") },
        text = { Text("다른 가족의 공유 여정과 이 기기에 저장한 내용은 삭제하지 않아요.") },
        confirmButton = { TextButton(onClick = { SyncEngine.disconnect(); disconnect = false }) { Text("연결 해제") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("취소") } })
    if (restore) AlertDialog(onDismissRequest = { restore = false }, title = { Text("연결 전 여정으로 복원할까요?") },
        text = { Text("자동 동기화를 끄고 이 기기에 보관한 이전 여정을 불러옵니다. 서버의 공유 여정은 유지됩니다.") },
        confirmButton = { TextButton(onClick = {
            SyncEngine.disconnect()
            note = runCatching { TripStore.restoreSyncBackup(); "이전 여정을 복원했어요" }.getOrElse { "백업을 읽지 못했어요. 현재 여정은 유지됩니다." }
            restore = false
        }) { Text("복원") } }, dismissButton = { TextButton(onClick = { restore = false }) { Text("취소") } })
}

@Composable
internal fun DeviceReminderSettings() {
    val context = LocalContext.current
    var receives by remember { mutableStateOf(ReminderScheduler.receives()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        receives = granted; ReminderScheduler.setReceives(granted)
    }
    Text("이 기기 출발 알림", style = MaterialTheme.typography.titleMedium)
    Row {
        Switch(receives, onCheckedChange = { enable ->
            if (enable && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { receives = enable; ReminderScheduler.setReceives(enable) }
        })
        Text("이 기기에서 알림 받기", Modifier.padding(start = 12.dp, top = 12.dp))
    }
    Text(ReminderScheduler.status, color = MTok.muted)
    Text("출발 일시와 알림 시점은 ‘일정’에서 정합니다. 같은 출발 알림은 이 기기에서 한 번만 보냅니다.", color = MTok.muted)
    OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }) { Text("시스템 알림 설정") }
    if (Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.exact()) TextButton(onClick = {
        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
    }) { Text("정확한 알림 허용 설정") }
}
