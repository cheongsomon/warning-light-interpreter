package ai.pleos.warninglight.ui

import ai.pleos.warninglight.MainViewModel
import ai.pleos.warninglight.UiState
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.WarningCatalog
import ai.pleos.warninglight.data.WarningLog
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- 경고 블랙박스 ----------------

@Composable
fun BlackBoxScreen(vm: MainViewModel, state: UiState) {
    @Suppress("UNUSED_VARIABLE") val version = state.logVersion // 기록 변경 시 다시 그림
    val events = vm.warningLog.all().asReversed()
    val summaries = vm.warningLog.summaries()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.bg).padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("경고 블랙박스", color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "경고가 언제 켜지고 꺼졌는지 시간순으로 기록합니다. 정비소에서 이 화면을 보여주세요 — 잠깐 켜졌다 꺼진 간헐 경고도 시점이 남습니다.",
                    color = Palette.textDim, fontSize = 22.sp,
                )
            }
            OutlinedButton(onClick = { if (confirmClear) { vm.clearLog(); confirmClear = false } else confirmClear = true }) {
                Text(if (confirmClear) "한 번 더 누르면 전체 삭제" else "기록 삭제", fontSize = 20.sp, color = if (confirmClear) Palette.red else Palette.textDim)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = vm::closeLog, modifier = Modifier.height(64.dp)) {
                Text("닫기", fontSize = 24.sp, color = Palette.text)
            }
        }
        Spacer(Modifier.height(20.dp))

        // 경고별 요약
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (summaries.isEmpty()) Text("아직 기록된 경고가 없습니다.", color = Palette.textDim, fontSize = 24.sp)
            summaries.forEach { s ->
                val info = WarningCatalog[s.id]
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.surface)
                        .border(2.dp, if (s.intermittent) Palette.amber else Palette.line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.of(info.severity)))
                        Spacer(Modifier.width(8.dp))
                        Text(info.name, color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "발생 ${s.count}회" + (if (s.blinkCount > 0) " · 점멸 ${s.blinkCount}회" else "") +
                            (s.shortestOnMs?.let { " · 최단 ${durationText(it)}" } ?: ""),
                        color = Palette.textDim, fontSize = 18.sp,
                    )
                    if (s.intermittent) Text("간헐 경고 의심", color = Palette.amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 타임라인
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Header("시각", 220); Header("경고", 300); Header("상태", 110); Header("지속", 140); Header("함께 켜진 경고", 440); Header("출처 · 보관", 220)
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Palette.surface),
        ) {
            items(events) { e ->
                val info = WarningCatalog[e.id]
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cell(SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA).format(Date(e.time)), 220, Palette.textDim)
                    Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Palette.of(e.severity)))
                        Spacer(Modifier.width(8.dp))
                        Text(info.name, color = Palette.text, fontSize = 22.sp)
                    }
                    Cell(e.kind.label, 110, when (e.kind) {
                        WarningLog.Kind.BLINK -> Palette.red
                        WarningLog.Kind.ON -> Palette.amber
                        WarningLog.Kind.OFF -> Palette.green
                    })
                    Cell(e.durationMs?.let { durationText(it) } ?: "-", 140, Palette.text)
                    Cell(e.concurrent.joinToString { WarningCatalog[it].name }.ifBlank { "-" }, 440, Palette.textDim)
                    Cell(
                        (if (e.origin == Origin.SIMULATION) "SIM" else "차량") + " · " + WarningLog.retentionLabel(e.severity).removeSuffix(" 보관"),
                        220, Palette.textDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("보관 기간: 정보 7일 · 정비 권장 30일 · 즉시 정차 경고는 직접 삭제하기 전까지 영구 보관", color = Palette.textDim, fontSize = 18.sp)
    }
}

@Composable
private fun Header(text: String, width: Int) =
    Text(text, color = Palette.textDim, fontSize = 18.sp, modifier = Modifier.width(width.dp))

@Composable
private fun Cell(text: String, width: Int, color: Color) =
    Text(text, color = color, fontSize = 22.sp, modifier = Modifier.width(width.dp), maxLines = 1)

private fun durationText(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}초"
        s < 3600 -> "${s / 60}분 ${s % 60}초"
        else -> "${s / 3600}시간 ${(s % 3600) / 60}분"
    }
}

// ---------------- 가족 연락처 등록 ----------------

@Composable
fun FamilyScreen(vm: MainViewModel, state: UiState) {
    var name by remember { mutableStateOf(state.familyName) }
    var phone by remember { mutableStateOf(state.familyPhone) }
    var mode by remember { mutableStateOf(state.familyMode) }
    var saved by remember { mutableStateOf(false) }
    val field = TextStyle(fontSize = 26.sp, color = Palette.text)

    Column(Modifier.fillMaxSize().background(Palette.bg).padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가족 연락처", color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::closeFamily, modifier = Modifier.height(64.dp)) {
                Text("닫기", fontSize = 24.sp, color = Palette.text)
            }
        }
        Text("미리 등록해 두면 즉시 정차 경고가 떴을 때 켜진 경고와 현재 위치를 가족에게 바로 알릴 수 있습니다. 연락처는 이 차량에만 저장됩니다.", color = Palette.textDim, fontSize = 22.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { name = it; saved = false }, label = { Text("받는 사람 (예: 딸)", fontSize = 18.sp) }, textStyle = field, singleLine = true, modifier = Modifier.width(420.dp))
            OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '-' }; saved = false }, label = { Text("휴대폰 번호", fontSize = 18.sp) }, textStyle = field, singleLine = true, modifier = Modifier.width(480.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("즉시 정차 경고가 떴을 때", color = Palette.textDim, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ai.pleos.warninglight.FamilyMode.entries.forEach { m ->
                val selected = m == mode
                Text(
                    m.label + when (m) {
                        ai.pleos.warninglight.FamilyMode.ASK -> " (경고 화면에 버튼 표시)"
                        ai.pleos.warninglight.FamilyMode.AUTO -> " (문자 발송 화면 자동 열림)"
                        ai.pleos.warninglight.FamilyMode.OFF -> ""
                    },
                    color = if (selected) Color.White else Palette.text, fontSize = 24.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Palette.blue else Palette.surface)
                        .clickable { mode = m; saved = false }.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("전달되는 내용 (예시)", color = Palette.textDim, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            vm.familyMessage(), color = Palette.text, fontSize = 24.sp, lineHeight = 36.sp,
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Palette.surface).padding(28.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { vm.saveFamily(name, phone, mode); saved = true },
                enabled = phone.isNotBlank(),
                modifier = Modifier.weight(1f).height(110.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.blue, contentColor = Color.White),
            ) { Text(if (saved) "저장됨 ✓" else "저장", fontSize = 34.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { vm.saveFamily(name, phone, mode); vm.requestFamilySend() },
                enabled = phone.isNotBlank(),
                modifier = Modifier.height(110.dp),
                shape = RoundedCornerShape(24.dp),
            ) { Text("지금 보내기", fontSize = 28.sp, color = Palette.text) }
        }
    }
}
