package ai.pleos.contest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.ConfirmContext
import ai.pleos.contest.domain.Item

/** UI 시안의 모바일 라이트 토큰. 차량용 [Tok]과 섞어 쓰지 않는다. */
object MTok {
    val page = Color(0xFFF4F7F9)
    val card = Color(0xFFFFFFFF)
    val ink = Color(0xFF152534)
    val muted = Color(0xFF5B6B78)
    val line = Color(0xFFE2E8ED)
    val lineSoft = Color(0xFFEEF2F5)
    val sky = Color(0xFF2F6F94)
    val cta = Color(0xFF8EC9E6)
    val onCta = Color(0xFF0E2233)
    val amber = Color(0xFF8A5100)
    val amberDot = Color(0xFFB76B00)
    val pendingRow = Color(0xFFFFFBF4)
    val heroBg = Color(0xFF0E1A24)
    val heroInk = Color(0xFFF2F6F7)
    val heroMuted = Color(0xFFC7D3DA)
    val accent = Color(0xFFA9D8F0)
    val off = Color(0xFF7D8C97)
    val box = Color(0xFFCBD5DD)
}

private val PhoneType = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = 13.sp),
)

@Composable
fun ChaegimMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = MTok.page,
            surface = MTok.card,
            onBackground = MTok.ink,
            onSurface = MTok.ink,
            primary = MTok.cta,
            onPrimary = MTok.onCta,
        ),
        typography = PhoneType,
        content = content,
    )
}

/** 받침이 있으면 "과", 없으면 "와". */
internal fun withJosa(a: String, b: String): String {
    val c = a.lastOrNull()?.code ?: 0
    val josa = if (c in 0xAC00..0xD7A3 && (c - 0xAC00) % 28 != 0) "과" else "와"
    return "$a$josa $b"
}

/** 기존 시안의 색과 목록을 유지하는 모바일 화면 전환. */
@Composable
fun MobileApp() {
    var page by rememberSaveable { mutableStateOf("today") }
    BackHandler(enabled = page != "today") { page = "today" }
    Column(Modifier.fillMaxSize().background(MTok.page)
        .padding(androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues())) {
        if (TripStore.storageError != null) Text(TripStore.storageError!!, color = MTok.amber, modifier = Modifier.padding(12.dp))
        Box(Modifier.weight(1f)) {
            when (page) {
                "list" -> ChecklistScreen { page = "today" }
                "schedule" -> TripEditorScreen()
                "return" -> MobileReturnScreen()
                "settings" -> SettingsScreen()
                "explore" -> ExploreScreen(false, { page = "today" }, { page = "record" })
                "record" -> JourneyRecordScreen(false) { page = "today" }
                else -> HomeScreen({ page = "list" }, { page = "schedule" }, { page = "settings" }, { page = "return" }, { page = "explore" }, { page = "record" })
            }
        }
        if (page != "today") Row(Modifier.fillMaxWidth().background(MTok.card)) {
            listOf("today" to "오늘", "schedule" to "일정", "list" to "준비물", "return" to "귀가", "settings" to "설정").forEach { (key, title) ->
                TextButton(onClick = { page = key }, modifier = Modifier.weight(1f)) { Text(title, fontSize = 12.sp, color = if (page == key) MTok.sky else MTok.muted) }
            }
        }
    }
}

// ---- 공통 조각 ----

@Composable
private fun Hairline(color: Color = MTok.lineSoft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun PendingDot() {
    Box(Modifier.size(6.dp).background(MTok.amberDot, CircleShape))
}

/** 숫자가 헤드라인. 숫자와 라벨을 아래쪽 기준선에 맞춘다. */
@Composable
private fun BigCount(number: Int, label: String, numberColor: Color, numberSize: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            number.toString(),
            fontSize = numberSize.sp,
            lineHeight = (numberSize * 0.8f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-6).sp,
            color = numberColor,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            modifier = Modifier.padding(bottom = 10.dp),
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
    }
}

/** 되돌리기 한 줄. 시안의 스낵바 자리이고, 타이머 없이 다음 조작까지 떠 있다. */
@Composable
private fun UndoPill(note: String, onUndo: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(MTok.ink, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(note, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.White)
        TextButton(onClick = onUndo, modifier = Modifier.height(44.dp)) {
            Text("되돌리기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MTok.accent)
        }
    }
}

@Composable
private fun BottomNav(onList: () -> Unit, onToday: () -> Unit, todayActive: Boolean, onSchedule: () -> Unit, onSettings: () -> Unit, onReturn: () -> Unit) {
    Column {
        Hairline(MTok.line)
        Row(Modifier.background(MTok.card).fillMaxWidth().padding(top = 6.dp, bottom = 20.dp)) {
            NavTab("오늘", todayActive, onToday)
            NavTab("일정", false, onSchedule)
            NavTab("내 준비물", !todayActive, onList)
            NavTab("귀가", false, onReturn)
            NavTab("설정", false, onSettings)
        }
    }
}

@Composable
private fun RowScope.NavTab(label: String, active: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .weight(1f)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MTok.ink else if (onClick == null) MTok.box else MTok.muted,
        )
    }
}

// ---- M01 오늘 ----

@Composable
fun HomeScreen(onOpenChecklist: () -> Unit, onSchedule: () -> Unit = {}, onSettings: () -> Unit = {}, onReturn: () -> Unit = {}, onExplore: () -> Unit = {}, onRecord: () -> Unit = {}) {
    val trip = TripStore.trip
    val pend = trip.pending
    val returning = trip.status in setOf(ai.pleos.contest.domain.TripStatus.ARRIVED, ai.pleos.contest.domain.TripStatus.RETURN_CHECK, ai.pleos.contest.domain.TripStatus.CLOSED)
    val stageItems = if (trip.status == ai.pleos.contest.domain.TripStatus.ARRIVED) trip.dropOffItems else trip.recoverItems
    val stageLeft = stageItems.count { !it.recovered }
    val ctaLabel = when (pend.size) {
        0 -> "준비물 목록 보기"
        1 -> "${pend[0].name} 확인하기"
        2 -> "${withJosa(pend[0].name, pend[1].name)} 확인하기"
        else -> "${pend.size}가지 확인하기"
    }
    val ownerLeft = pend.groupBy { it.owner }.map { (owner, list) -> "$owner ${list.size}개" }.joinToString(" · ")
    val owners = trip.items.map { it.owner }.filter { it.isNotEmpty() }.distinct().size

    Surface(color = MTok.page, contentColor = MTok.ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 사진 자리. 에셋이 들어오면 Image로 바꾸고 같은 그라데이션을 위에 얹는다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B3040), MTok.heroBg)))
                    .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 22.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides MTok.heroInk) {
                    Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDCEFF9))
                    Spacer(Modifier.height(28.dp))
                    BigCount(
                        number = if (returning) stageLeft else if (pend.isEmpty()) trip.confirmedCount else pend.size,
                        label = if (trip.status == ai.pleos.contest.domain.TripStatus.CLOSED) "이번 여정\n확인 완료" else if (returning) "개 회수\n확인 필요" else if (pend.isEmpty()) "가지 모두\n확인했어요" else "가지만\n확인하면 끝",
                        numberColor = MTok.accent,
                        numberSize = 112,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(trip.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("  ·  ${trip.departAt} 출발  ·  ${trip.destination}", fontSize = 14.sp, color = MTok.heroMuted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = if (returning) onReturn else onOpenChecklist,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MTok.cta, contentColor = MTok.onCta),
                ) { Text(if (returning) "도착·귀가 확인 보기" else ctaLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }

            Column(Modifier.background(MTok.card).fillMaxWidth()) {
                HomeRow(
                    num = "${trip.confirmedCount}",
                    suffix = "/${trip.activeCount}",
                    title = "준비물 점검",
                    sub = null,
                    onClick = onOpenChecklist,
                )
                Hairline()
                HomeRow(
                    num = "$owners",
                    suffix = "명",
                    title = "함께 챙기기",
                    sub = if (pend.isEmpty()) "모두 챙겼어요" else "$ownerLeft 남음",
                    subPending = pend.isNotEmpty(),
                    onClick = onOpenChecklist,
                )
                Hairline(MTok.line)
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                TextButton(onClick = onExplore, modifier = Modifier.weight(1f)) { Text("목적지 3D 탐색", color = MTok.sky) }
                TextButton(onClick = onRecord, modifier = Modifier.weight(1f)) { Text("가족 여행 기록", color = MTok.sky) }
            }
            Spacer(Modifier.weight(1f))
            BottomNav(onList = onOpenChecklist, onToday = {}, todayActive = true, onSchedule, onSettings, onReturn)
        }
    }
}

@Composable
private fun HomeRow(
    num: String,
    suffix: String,
    title: String,
    sub: String?,
    subPending: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(62.dp), verticalAlignment = Alignment.Bottom) {
            Text(num, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = MTok.ink)
            Text(suffix, fontSize = 15.sp, color = MTok.muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MTok.ink)
            if (sub != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (subPending) PendingDot()
                    Text(sub, fontSize = 13.sp, color = if (subPending) MTok.amber else MTok.muted)
                }
            }
        }
        Text("›", fontSize = 20.sp, color = MTok.muted)
    }
}

// ---- M03 준비물 점검 ----

private enum class Filter { UNCHECKED, ALL, OWNER }

@Composable
fun ChecklistScreen(onBack: () -> Unit) {
    val trip = TripStore.trip
    var filter by rememberSaveable { mutableStateOf(Filter.UNCHECKED) }
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Item?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding || editing != null) ItemEditorDialog(editing) { adding = false; editing = null }

    val items = trip.items
    val left = trip.pending.size
    val shown = when (filter) {
        Filter.UNCHECKED -> items.filter { !it.prepared && it.state != ai.pleos.contest.domain.ItemState.EXCLUDED }
        Filter.ALL -> items
        Filter.OWNER -> items.sortedBy { it.owner }
    }

    Surface(color = MTok.page, contentColor = MTok.ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Text("‹", fontSize = 26.sp, color = MTok.ink)
                }
                Text("준비물 점검", style = MaterialTheme.typography.titleMedium, color = MTok.ink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("+ 추가", color = MTok.sky) }
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp)) {
                BigCount(
                    number = if (left == 0) trip.confirmedCount else left,
                    label = if (left == 0) "개 모두\n확인했어요" else "개만 더\n확인하면 돼요",
                    numberColor = if (left == 0) MTok.sky else MTok.amber,
                    numberSize = 96,
                )
                Spacer(Modifier.height(6.dp))
                Text("모바일 준비 ${trip.confirmedCount} / ${trip.activeCount} · 차량 적재는 별도 확인", fontSize = 14.sp, color = MTok.muted)
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                FilterTab("미확인", filter == Filter.UNCHECKED) { filter = Filter.UNCHECKED }
                FilterTab("전체", filter == Filter.ALL) { filter = Filter.ALL }
                FilterTab("담당자별", filter == Filter.OWNER) { filter = Filter.OWNER }
            }
            Hairline(MTok.line)

            Column(Modifier.weight(1f).background(MTok.card).verticalScroll(rememberScrollState())) {
                shown.forEach { item ->
                    ItemRow(item, onEdit = { editing = item }) {
                        note = item.name + if (item.confirmed) " 확인을 취소했어요" else "을(를) 확인했어요"
                        TripStore.toggleConfirm(item.id, ConfirmContext.MOBILE)
                    }
                }
            }

            if (TripStore.canUndo) {
                Spacer(Modifier.height(10.dp))
                UndoPill(note.ifEmpty { "변경 내용을 저장했어요" }) {
                    note = ""
                    TripStore.undo()
                }
            }

            Hairline(MTok.line)
            Row(
                Modifier.background(MTok.card).fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            ) {
                Button(
                    onClick = {
                        note = "남은 ${left}개를 확인했어요"
                        TripStore.confirmAllPending(ConfirmContext.MOBILE)
                    },
                    enabled = left > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MTok.cta,
                        contentColor = MTok.onCta,
                        disabledContainerColor = MTok.line,
                        disabledContentColor = MTok.off,
                    ),
                ) {
                    Text(
                        if (left == 0) "모두 확인했어요" else "${left}개 모두 확인",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.FilterTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f)) {
        Box(Modifier.height(46.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MTok.ink else MTok.muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) MTok.ink else Color.Transparent))
    }
}

@Composable
private fun ItemRow(item: Item, onEdit: () -> Unit, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (item.confirmed) MTok.card else MTok.pendingRow)
                .clickable(enabled = item.state in setOf(ai.pleos.contest.domain.ItemState.NOT_CHECKED, ai.pleos.contest.domain.ItemState.USER_CONFIRMED), onClick = onToggle)
                .padding(start = 14.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.confirmed,
                enabled = item.state in setOf(ai.pleos.contest.domain.ItemState.NOT_CHECKED, ai.pleos.contest.domain.ItemState.USER_CONFIRMED),
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MTok.sky, uncheckedColor = MTok.box),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.name,
                    fontSize = 16.sp,
                    fontWeight = if (item.confirmed) FontWeight.Normal else FontWeight.Bold,
                    color = if (item.confirmed) MTok.muted else MTok.ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.owner} 담당", fontSize = 13.sp, color = MTok.muted)
                    if (!item.confirmed) {
                        PendingDot()
                        Text("확인 필요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MTok.amber)
                    }
                }
                Text(if (item.loaded) "차량 적재 확인됨" else "차량 적재 확인 전", fontSize = 12.sp, color = MTok.muted)
            }
            TextButton(onClick = onEdit) { Text("편집", color = MTok.sky) }
        }
        Hairline()
    }
}
