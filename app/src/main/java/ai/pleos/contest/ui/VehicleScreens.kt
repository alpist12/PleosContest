package ai.pleos.contest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.contest.data.TripStore
import ai.pleos.contest.domain.ConfirmContext
import ai.pleos.contest.domain.Item
import ai.pleos.contest.vehicle.DrivingGate

/** 차량 화면 세 장. 한 번에 "지금 확인할 것" 하나만 보여준다. */

@Composable
internal fun StatusBadge() {
    val driving = DrivingGate.driving
    val context = androidx.compose.ui.platform.LocalContext.current
    val demo = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    // 실제 신호를 붙이기 전까지는 눌러서 주행/정차를 바꿀 수 있게 둔다(시연·테스트용).
    OutlinedButton(
        onClick = { DrivingGate.toggle() },
        enabled = demo,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (driving) Tok.warn.copy(alpha = 0.45f) else Tok.accent.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (driving) Tok.warnInk else Tok.accent),
        modifier = Modifier.height(44.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (driving) Tok.warn else Tok.accent, CircleShape)
        )
        Text(
            "${DrivingGate.source} · " + if (driving) "조작 제한" else "확인 가능",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ScreenFrame(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Tok.bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 52.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Tok.muted)
                Spacer(Modifier.weight(1f))
                StatusBadge()
            }
            content()
        }
    }
}


@Composable
private fun Headline(text: String, accentPart: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        color = Tok.ink,
        modifier = Modifier.padding(top = 34.dp),
    )
    if (accentPart != null) {
        Text(accentPart, style = MaterialTheme.typography.displayLarge, color = Tok.accent)
    }
}

@Composable
private fun ItemTile(
    item: Item,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, Tok.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Tok.surface,
            contentColor = Tok.ink,
            disabledContentColor = Tok.dim,
        ),
        modifier = Modifier
            .width(330.dp)
            .height(104.dp)
            .semantics { stateDescription = if (checked) "확인함" else "확인 필요" },
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(Tok.accent.copy(alpha = if (checked) 0.30f else 0.12f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (checked) "✓" else "", fontSize = 30.sp, color = Tok.onCta, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(item.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            if (item.place.isNotEmpty()) {
                Text(item.place, fontSize = 17.sp, color = Tok.dim)
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .then(
                    if (checked) Modifier.background(Tok.accent, CircleShape)
                    else Modifier.border(2.5.dp, Tok.dim, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", fontSize = 22.sp, color = Tok.bg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BigCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Tok.cta, contentColor = Tok.onCta),
        modifier = Modifier.width(290.dp).height(104.dp),
    ) {
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    }
}

@Composable
private fun DrivingNotice() {
    Box(
        Modifier
            .width(290.dp)
            .height(104.dp)
            .border(1.5.dp, Tok.muted.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("정차 중에 확인할 수 있어요.", fontSize = 22.sp, color = Tok.muted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UndoRow(note: String) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = Tok.dim)
        Spacer(Modifier.weight(1f))
        if (TripStore.canUndo) {
            TextButton(onClick = { TripStore.undo() }, enabled = DrivingGate.parked, modifier = Modifier.height(44.dp)) {
                Text("되돌리기", fontSize = 18.sp, color = Tok.accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** V01 출발 전 확인. */
@Composable
fun DepartureScreen(onArrive: () -> Unit) {
    val trip = TripStore.trip
    val pending = trip.vehiclePending
    val parked = DrivingGate.parked

    ScreenFrame(trip.title) {
        when {
            trip.status == ai.pleos.contest.domain.TripStatus.CLOSED -> Headline("이번 여정", "확인 완료")
            pending.isEmpty() -> Headline("오늘 필요한 물건을", "모두 확인했어요.")
            pending.size == 1 -> Headline("출발 전,", "한 가지만 남았어요.")
            else -> Headline("출발 전,", "${pending.size}가지만 확인하세요.")
        }

        Text(
            "${trip.departAt} 출발 · ${trip.destination}",
            style = MaterialTheme.typography.bodyLarge,
            color = Tok.muted,
            modifier = Modifier.padding(top = 22.dp),
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("확인이 필요한 물건", style = MaterialTheme.typography.bodyMedium, color = Tok.muted)
                    Box(
                        Modifier
                            .padding(horizontal = 18.dp)
                            .width(220.dp)
                            .height(6.dp)
                            .background(Tok.hairline, CircleShape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(if (trip.activeCount == 0) 0f else trip.loadedCount.toFloat() / trip.activeCount)
                                .background(Tok.accent, CircleShape)
                        )
                    }
                    Text(
                        "${trip.loadedCount} / ${trip.activeCount} 적재 확인 · 모바일 준비와 별도",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tok.muted,
                    )
                }
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 화면에는 미확인 두 개까지만. 전체 목록은 모바일에서 본다.
                    pending.take(2).forEach { item ->
                        ItemTile(item, checked = false, enabled = parked) {
                            TripStore.toggleConfirm(item.id, ConfirmContext.VEHICLE)
                        }
                    }
                    if (pending.isEmpty()) {
                        trip.items.take(2).forEach { item ->
                            ItemTile(item, checked = true, enabled = parked) {
                                TripStore.toggleConfirm(item.id, ConfirmContext.VEHICLE)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(28.dp))
            when {
                trip.status == ai.pleos.contest.domain.TripStatus.CLOSED -> Text("여정을 마쳤어요", color = Tok.accent)
                !parked -> DrivingNotice()
                pending.isNotEmpty() -> BigCta("모두 챙겼어요") {
                    TripStore.confirmAllPending(ConfirmContext.VEHICLE)
                }
                else -> BigCta("도착했어요") {
                    TripStore.startArrival()
                    onArrive()
                }
            }
        }

        UndoRow("확인 완료는 사용자가 표시한 상태입니다.")
    }
}

/** V03 목적지 하차 확인. */
@Composable
fun ArrivalScreen(onDone: () -> Unit) {
    val trip = TripStore.trip
    val items = trip.dropOffItems
    val left = items.count { !it.recovered }
    val parked = DrivingGate.parked

    ScreenFrame("${trip.destination} 도착") {
        Headline("차에서 가져갈 물건이", "있나요?")

        Text(
            "지금 내릴 물건 ${items.size}개 중 ${items.size - left}개 확인",
            style = MaterialTheme.typography.bodyLarge,
            color = Tok.muted,
            modifier = Modifier.padding(top = 22.dp),
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items.forEach { item ->
                    ItemTile(item, checked = item.recovered, enabled = parked) {
                        TripStore.toggleRecovered(item.id)
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            if (parked) BigCta(if (left > 0) "${left}개 모두 내렸어요" else "귀가 준비하기", onClick = {
                if (left > 0) TripStore.confirmRecovered(items.map { it.id }.toSet())
                else { TripStore.beginRecovery(); onDone() }
            }) else DrivingNotice()
        }

        UndoRow("물건이 실제로 남았는지는 직접 한 번 봐주세요.")
    }
}

/** V04 귀가 전 회수 확인. */
@Composable
fun RecoverScreen(onFinish: () -> Unit) {
    val trip = TripStore.trip
    val items = trip.recoverItems
    val left = items.count { !it.recovered }
    val parked = DrivingGate.parked
    val finishing = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    ScreenFrame("돌아가기 전 회수 확인") {
        if (left == 0) Headline("회수 대상을", "모두 챙겼어요.")
        else Headline("회수할 ${left}개를", "확인해 주세요.")

        Text(
            "회수 대상 ${items.size}개 중 ${items.size - left}개 확인",
            style = MaterialTheme.typography.bodyLarge,
            color = Tok.muted,
            modifier = Modifier.padding(top = 22.dp),
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 남은 것부터 보여준다. 다 끝나면 마지막에 확인한 것들을 보여 준다.
                val shown = if (left > 0) items.filter { !it.recovered } else items
                shown.take(3).forEach { item ->
                    ItemTile(item, checked = item.recovered, enabled = parked) {
                        TripStore.toggleRecovered(item.id)
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            when {
                !parked -> DrivingNotice()
                left > 0 -> BigCta("${left}개 모두 챙겼어요") {
                    TripStore.confirmRecovered(items.filter { !it.recovered }.map { it.id }.toSet())
                }
                else -> BigCta("이번 여정 마치기", onClick = { finishing.value = true })
            }
        }

        UndoRow("회수 확인도 사용자가 표시한 상태입니다.")
    }
    if (finishing.value) androidx.compose.material3.AlertDialog(
        onDismissRequest = { finishing.value = false },
        title = { Text("이번 여정을 마칠까요?") },
        text = { Text("회수 대상 ${items.size}개를 모두 확인했어요.") },
        confirmButton = { TextButton(enabled = parked, onClick = {
            TripStore.finishTrip(); finishing.value = false; onFinish()
        }) { Text("여정 마치기") } },
        dismissButton = { TextButton(onClick = { finishing.value = false }) { Text("취소") } },
    )
}
