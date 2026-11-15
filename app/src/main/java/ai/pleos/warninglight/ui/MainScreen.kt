package ai.pleos.warninglight.ui

import ai.pleos.warninglight.MainViewModel
import ai.pleos.warninglight.UiState
import ai.pleos.warninglight.data.ActiveWarning
import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.NextAction
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.Severity
import ai.pleos.warninglight.data.WarningCatalog
import ai.pleos.warninglight.engine.PriorityEngine
import ai.pleos.warninglight.navi.Emergency
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel, state: UiState) {
    var showSim by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(state, showSim) { showSim = !showSim }
                Spacer(Modifier.height(20.dp))
                Box(Modifier.weight(1f)) {
                    val top = state.top
                    if (top == null) AllClearCard(onBriefing = vm::briefing)
                    else WarningCard(vm, top, state)
                }
                if (state.warnings.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    OtherWarnings(vm, state.warnings.drop(1))
                }
                state.message?.let {
                    Spacer(Modifier.height(16.dp))
                    MessageBar(it, vm::dismissMessage)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "제조사 매뉴얼 기준 안내이며 차량 진단이 아닙니다. 계기판 경고 표시를 우선 확인하세요.",
                    color = Palette.textDim, fontSize = 18.sp,
                )
            }
            if (showSim) SimulationPanel(vm, state, Modifier.width(500.dp).fillMaxHeight())
        }
        if (state.showEmergency) EmergencyScreen(vm, state)
    }
}

@Composable
private fun TopBar(state: UiState, showSim: Boolean, onToggleSim: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("경고등 통역사", color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(24.dp))
        Chip(
            if (state.vehicle.telltales) "차량 경고등 신호 수신 중" else state.vehicle.message,
            if (state.vehicle.telltales) Palette.green else Palette.textDim,
        )
        Spacer(Modifier.width(12.dp))
        Chip("음성: ${state.voiceEngine}", Palette.textDim)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onToggleSim) {
            Text(if (showSim) "시뮬레이션 패널 닫기" else "시뮬레이션 패널", fontSize = 18.sp, color = Palette.textDim)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.surface2).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Palette.text, fontSize = 18.sp)
    }
}

@Composable
private fun WarningCard(vm: MainViewModel, w: ActiveWarning, state: UiState) {
    val info = WarningCatalog[w.id]
    val severity = PriorityEngine.severityOf(w)
    val color = Palette.of(severity)
    val onColor = Palette.on(severity)

    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Palette.surface)
            .border(3.dp, color, RoundedCornerShape(28.dp))
    ) {
        // 상단: "계속 달려도 되나요?"에 대한 답
        Row(
            Modifier.fillMaxWidth().background(color).padding(horizontal = 36.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(severity.verdict, color = onColor, fontSize = 58.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (w.state == LampState.BLINK) Tag("점멸", onColor)
            Spacer(Modifier.width(10.dp))
            Tag(if (w.origin == Origin.SIMULATION) "SIM" else "차량 신호", onColor)
        }
        Column(Modifier.weight(1f).padding(36.dp).verticalScroll(rememberScrollState())) {
            Text(vm.displayName(w), color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(info.meaning, color = Palette.textDim, fontSize = 28.sp, lineHeight = 38.sp)
            if (info.affected.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("영향을 받을 수 있는 기능", color = Palette.textDim, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.affected.forEach { f ->
                        Text(
                            f, color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.border(2.dp, color, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(info.actionFor(w.state), color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)
            if (w.id == ai.pleos.warninglight.data.WarningId.LOW_FUEL) {
                state.rangeKm?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("남은 주행가능거리 약 ${it.toInt()}km", color = Palette.text, fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("발생 ${timeOf(w.since)}", color = Palette.textDim, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            when (info.nextActionFor(w.state)) {
                NextAction.EMERGENCY -> BigButton("긴급출동 호출", Palette.red, Color.White, Modifier.weight(1f), vm::openEmergency)
                NextAction.SERVICE_CENTER -> BigButton("가까운 블루핸즈 길안내", Palette.amber, Color(0xFF1A1300), Modifier.weight(1f), vm::findNearestAndRoute)
                NextAction.NONE -> BigButton("다시 듣기", Palette.blue, Color.White, Modifier.weight(1f), vm::repeatCurrent)
            }
            if (info.nextActionFor(w.state) != NextAction.NONE) {
                Spacer(Modifier.width(20.dp))
                OutlinedButton(onClick = vm::repeatCurrent, modifier = Modifier.height(96.dp)) {
                    Text("다시 듣기", fontSize = 26.sp, color = Palette.text)
                }
            }
        }
    }
}

@Composable
private fun AllClearCard(onBriefing: () -> Unit) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Palette.surface).padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.green))
        Spacer(Modifier.height(24.dp))
        Text("확인이 필요한 경고가 없습니다", color = Palette.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("경고등이 켜지면 무슨 경고인지, 계속 달려도 되는지 음성으로 알려드립니다.", color = Palette.textDim, fontSize = 26.sp)
        Spacer(Modifier.height(36.dp))
        OutlinedButton(onClick = onBriefing, modifier = Modifier.height(80.dp)) {
            Text("차량 상태 브리핑 듣기", fontSize = 24.sp, color = Palette.text)
        }
    }
}

@Composable
private fun OtherWarnings(vm: MainViewModel, rest: List<ActiveWarning>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("함께 켜진 경고", color = Palette.textDim, fontSize = 22.sp)
        rest.take(4).forEach { w ->
            val s = PriorityEngine.severityOf(w)
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.surface2).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.of(s)))
                Spacer(Modifier.width(8.dp))
                Text(vm.displayName(w), color = Palette.text, fontSize = 22.sp)
            }
        }
        if (rest.size > 4) Text("외 ${rest.size - 4}건", color = Palette.textDim, fontSize = 22.sp)
    }
}

@Composable
private fun MessageBar(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.surface2).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Palette.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDismiss) { Text("닫기", fontSize = 18.sp, color = Palette.textDim) }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.border(2.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun BigButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
    ) { Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
}

// ---------------- 긴급출동 화면 ----------------

@Composable
private fun EmergencyScreen(vm: MainViewModel, state: UiState) {
    val context = LocalContext.current
    val top = state.top
    Column(Modifier.fillMaxSize().background(Palette.bg).padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("긴급출동 요청", color = Palette.red, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = vm::closeEmergency, modifier = Modifier.height(72.dp)) {
                Text("닫기", fontSize = 24.sp, color = Palette.text)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("통화 시 아래 내용을 그대로 읽어 주세요.", color = Palette.textDim, fontSize = 24.sp)
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val loc = state.location
            SummaryRow("경고", top?.let { vm.displayName(it) } ?: "-")
            SummaryRow("발생 시각", top?.let { timeOf(it.since) } ?: "-")
            SummaryRow(
                "현재 위치",
                when {
                    loc == null -> "확인 중…"
                    loc.address != null -> "${loc.address} (${"%.5f".format(loc.lat)}, ${"%.5f".format(loc.lon)})"
                    else -> "${"%.5f".format(loc.lat)}, ${"%.5f".format(loc.lon)}" + if (!loc.fromSdk) " · 데모 기준 위치" else ""
                },
            )
            SummaryRow("차량", state.vin?.let { "차대번호 $it" } ?: "차량 정보 확인 불가")
            if (state.warnings.size > 1) {
                SummaryRow("함께 켜진 경고", state.warnings.drop(1).joinToString { vm.displayName(it) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Emergency.PHONE}"))) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.red, contentColor = Color.White),
        ) {
            Text("${Emergency.LABEL} ${Emergency.PHONE} 전화 걸기", fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row {
        Text(label, color = Palette.textDim, fontSize = 28.sp, modifier = Modifier.width(240.dp))
        Text(value, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- 시뮬레이션 패널 ----------------

@Composable
private fun SimulationPanel(vm: MainViewModel, state: UiState, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(20.dp)) {
        Text("시뮬레이션 패널", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("데모·테스트용 경고 발생 (화면에 SIM 표시)", color = Palette.textDim, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WarningCatalog.all.forEach { info ->
                val current = state.warnings.firstOrNull { it.id == info.id && it.origin == Origin.SIMULATION }?.state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.of(info.severity)))
                    Spacer(Modifier.width(8.dp))
                    Text(info.name, color = Palette.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    SimButton("끔", current == null) { vm.simulate(info.id, null) }
                    SimButton("점등", current == LampState.ON) { vm.simulate(info.id, LampState.ON) }
                    SimButton("점멸", current == LampState.BLINK) { vm.simulate(info.id, LampState.BLINK) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = vm::clearSimulation, modifier = Modifier.weight(1f)) {
                Text("모두 끄기", fontSize = 18.sp, color = Palette.text)
            }
            OutlinedButton(onClick = vm::briefing, modifier = Modifier.weight(1f)) {
                Text("시동 브리핑", fontSize = 18.sp, color = Palette.text)
            }
        }
    }
}

@Composable
private fun SimButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Palette.bg else Palette.textDim,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Palette.text else Palette.surface2)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun timeOf(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(ms))
