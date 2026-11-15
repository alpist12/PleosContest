package ai.pleos.contest.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ai.pleos.contest.MainActivity
import ai.pleos.contest.domain.ReminderPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object ReminderScheduler {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private const val CHANNEL = "departure"
    var status by androidx.compose.runtime.mutableStateOf("예약 없음")
        private set
    // Automotive defaults to quiet; the phone owns departure reminders unless explicitly opted in.
    fun receives(): Boolean = prefs.getBoolean("receive", !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    fun setReceives(value: Boolean) { prefs.edit().putBoolean("receive", value).apply(); reschedule() }
    fun permitted(): Boolean = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun exact(): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun init(app: Context) {
        context = app.applicationContext
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "출발 전 준비물", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun alarm(key: String): PendingIntent = PendingIntent.getBroadcast(context, 101,
        Intent(context, ReminderReceiver::class.java).setAction("ai.pleos.contest.DEPARTURE").putExtra("key", key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reschedule() {
        val trip = TripStore.trip
        val manager = context.getSystemService(AlarmManager::class.java)
        val key = ReminderPolicy.key(trip)
        val operation = alarm(key)
        manager.cancel(operation)
        val at = ReminderPolicy.at(trip)
        if (trip.pending.isEmpty() || at == null || !receives()) NotificationManagerCompat.from(context).cancel(101)
        status = when {
            !receives() -> "이 기기 알림 꺼짐"
            !trip.reminderEnabled -> "예약 알림 꺼짐"
            !permitted() -> "알림 권한이 필요해요"
            at == null -> "미확인 준비물이 없거나 출발 확인이 끝났어요"
            prefs.getString("delivered", null) == key -> "이 출발 알림은 이미 보냈어요"
            at <= System.currentTimeMillis() -> "알림 시각이 지났어요 · 출발 일시를 다시 선택해 주세요"
            else -> {
                try {
                    if (exact()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                    if (exact()) "출발 ${trip.reminderMinutes}분 전 알림 예약됨" else "알림 예약됨 · 절전 정책에 따라 늦어질 수 있어요"
                } catch (_: SecurityException) { "정확한 알림 권한이 변경됐어요 · 앱을 다시 열어 주세요" }
            }
        }
    }
    fun deliver(expectedKey: String) {
        val trip = TripStore.trip
        val now = System.currentTimeMillis()
        val at = ReminderPolicy.at(trip) ?: return
        if (expectedKey != ReminderPolicy.key(trip) || at > now || trip.departureEpochMillis + 15 * 60_000L < now ||
            prefs.getString("delivered", null) == expectedKey || !receives() || !permitted()) return
        val open = PendingIntent.getActivity(context, 102, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val names = trip.pending.take(3).joinToString(", ") { it.name }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("${trip.title} · 준비 확인 ${trip.pending.size}개 남음")
            .setContentText(names).setStyle(NotificationCompat.BigTextStyle().bigText("이 기기에 저장된 확인 상태: $names\n앱에서 최신 가족 준비 상황을 확인하세요."))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            NotificationManagerCompat.from(context).notify(101, notification)
            prefs.edit().putString("delivered", expectedKey).commit()
            status = "출발 알림을 보냈어요"
        } catch (_: SecurityException) { status = "알림 권한이 필요해요" }
    }
}
