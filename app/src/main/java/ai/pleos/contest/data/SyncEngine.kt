package ai.pleos.contest.data

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object SyncEngine {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor()
    private lateinit var context: Context
    private var periodic: ScheduledFuture<*>? = null
    private var debounce: ScheduledFuture<*>? = null
    private var generation = 0
    var configured by mutableStateOf(false); private set
    var status by mutableStateOf("연결하지 않음"); private set
    var lastSynced by mutableStateOf(0L); private set
    private val prefs get() = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun init(app: Context) {
        context = app.applicationContext
        configured = prefs.contains("endpoint")
        scheduleBackground()
    }
    private fun <T> ui(block: () -> T): T {
        val result = CompletableFuture<T>()
        main.post { try { result.complete(block()) } catch (e: Exception) { result.completeExceptionally(e) } }
        return result.get(10, TimeUnit.SECONDS)
    }
    fun foreground(active: Boolean) {
        periodic?.cancel(false); periodic = null
        if (active && configured) periodic = io.scheduleWithFixedDelay({ runCatching { cycle() } }, 0, 5, TimeUnit.SECONDS)
    }
    fun request() {
        if (!configured) return
        debounce?.cancel(false)
        debounce = io.schedule({ cycle() }, 400, TimeUnit.MILLISECONDS)
    }
    fun once(done: () -> Unit = {}) { io.execute { try { cycle() } finally { main.post(done) } } }
    fun invitation(): String = prefs.getString("endpoint", "") + "#" + prefs.getString("token", "")

    fun connect(invitation: String) {
        val parsed = runCatching {
            val uri = URI(invitation.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.path == "/trip")
            require(uri.fragment?.matches(Regex("[a-f0-9]{64}")) == true)
            (invitation.trim().substringBefore('#')) to uri.fragment
        }.getOrElse { status = "올바른 HTTPS 초대 정보를 입력해 주세요"; return }
        status = "공유 여정 확인 중…"
        val attempt = ++generation
        io.execute {
            try {
                val remote = exchange(parsed.first, parsed.second, "GET")
                ui {
                    if (attempt != generation) return@ui
                    TripStore.backupBeforeJoin()
                    check(prefs.edit().putString("endpoint", parsed.first).putString("token", parsed.second).commit())
                    configured = true
                    if (!remote.isNull("document")) TripStore.acceptSync(remote.getJSONObject("document"), remote.getJSONObject("document"))
                    else TripStore.clearSyncBase()
                    status = "연결됨 · 자동 동기화 중"
                    scheduleBackground(); foreground(true)
                }
            } catch (_: Exception) { ui { if (attempt == generation) status = "연결 실패 · 초대 정보와 인터넷을 확인해 주세요" } }
        }
    }

    fun disconnect() {
        generation++
        prefs.edit().clear().commit()
        configured = false; status = "연결 해제 · 기기 데이터는 유지됨"
        periodic?.cancel(false); periodic = null
        debounce?.cancel(false)
        TripStore.clearSyncBase()
        scheduleBackground()
    }
    private fun cycle() {
        val config = ui { if (!configured) null else Triple(prefs.getString("endpoint", "")!!, prefs.getString("token", "")!!, generation) } ?: return
        try {
            repeat(3) {
                val response = exchange(config.first, config.second, "GET")
                val snapshot = ui { if (generation != config.third || !configured) null else TripStore.snapshot() to TripStore.acknowledged() } ?: return
                val remote = response.optJSONObject("document")
                val proposed = when {
                    remote == null -> snapshot.first
                    snapshot.second == null -> remote // Joining never uploads the old device's list over the shared list.
                    else -> SyncMerge.merge(snapshot.second!!, snapshot.first, remote)
                }
                val accepted = if (remote != null && SyncMerge.equal(proposed, remote)) remote else {
                    try { exchange(config.first, config.second, "PUT", JSONObject().put("version", response.getLong("version")).put("document", proposed)).getJSONObject("document") }
                    catch (_: Conflict) { return@repeat }
                }
                ui {
                    if (generation != config.third || !configured) return@ui
                    val current = TripStore.snapshot()
                    TripStore.acceptSync(SyncMerge.merge(snapshot.first, current, accepted), accepted)
                    lastSynced = System.currentTimeMillis()
                    status = "동기화 완료"
                }
                return
            }
            ui { status = "동시 변경을 조정 중 · 자동 재시도" }
        } catch (_: Exception) {
            ui { if (generation == config.third) status = "연결 대기 · 변경 내용은 기기에 보관 중" }
        }
    }
    private class Conflict : Exception()
    private fun exchange(endpoint: String, token: String, method: String, body: JSONObject? = null): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3500; connection.readTimeout = 3500
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("User-Agent", "CHAEGIM/1.1 (Android)")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 65536) { "공유 목록이 너무 큽니다" }
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(bytes) }
            }
            if (connection.responseCode == 409) throw Conflict()
            require(connection.responseCode == 200)
            val bytes = connection.inputStream.use { it.readBytesLimited(70000) }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= max); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun scheduleBackground() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!configured) { scheduler.cancel(210); return }
        scheduler.schedule(JobInfo.Builder(210, ComponentName(context, SyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).setPersisted(true).build())
    }
}
