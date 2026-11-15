package ai.pleos.warninglight

import ai.pleos.warninglight.data.ActiveWarning
import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.Severity
import ai.pleos.warninglight.data.WarningCatalog
import ai.pleos.warninglight.data.WarningId
import ai.pleos.warninglight.engine.PriorityEngine
import ai.pleos.warninglight.navi.Navigator
import ai.pleos.warninglight.navi.ServiceCenter
import ai.pleos.warninglight.navi.ServiceCenters
import ai.pleos.warninglight.source.SimulatedWarningSource
import ai.pleos.warninglight.source.VehicleSdkWarningSource
import ai.pleos.warninglight.source.WarningHub
import ai.pleos.warninglight.voice.Announcer
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 즉시 정차 경고 시 가족 알림 방식 (앱 설정). */
enum class FamilyMode(val label: String) {
    ASK("확인 후 전송"),
    AUTO("자동 전송"),
    OFF("사용 안 함"),
}

data class UiState(
    val warnings: List<ActiveWarning> = emptyList(),
    val vehicle: VehicleSdkWarningSource.Status = VehicleSdkWarningSource.Status(),
    val voiceEngine: String = "음성 준비 중",
    val rangeKm: Float? = null,
    val vin: String? = null,
    val nearest: Pair<ServiceCenter, Double>? = null,
    val location: Navigator.Location? = null,
    val message: String? = null,
    /** 지금 음성으로 읽고 있는 문장 (화면 자막). */
    val caption: String? = null,
    val showLog: Boolean = false,
    /** 블랙박스 기록이 바뀔 때마다 증가 (화면 갱신용). */
    val logVersion: Int = 0,
    val showFamily: Boolean = false,
    val familyName: String = "",
    val familyPhone: String = "",
    /** 즉시 정차 경고 시 가족 알림 방식. */
    val familyMode: FamilyMode = FamilyMode.ASK,
    /** 화면이 처리해야 할 가족 알림 발송 요청 (한 번 처리 후 null). */
    val familySendRequest: String? = null,
    /** 마지막 가족 알림 요청 시각. */
    val familySentAt: Long? = null,
    val showEmergency: Boolean = false,
    val emergencySince: Long? = null,
) {
    val top: ActiveWarning? get() = warnings.firstOrNull()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val hub = WarningHub()
    private val vehicleSource = VehicleSdkWarningSource(app, hub)
    val simulation = SimulatedWarningSource(hub)
    private val navigator = Navigator(app)
    private val announcer = Announcer(app) { engine -> _ui.update { it.copy(voiceEngine = engine) } }

    val warningLog = ai.pleos.warninglight.data.WarningLog(app)
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private var prevActive: Map<WarningId, ActiveWarning> = emptyMap()

    private val _ui = MutableStateFlow(
        UiState(
            familyName = prefs.getString("familyName", "") ?: "",
            familyPhone = prefs.getString("familyPhone", "") ?: "",
            familyMode = runCatching { FamilyMode.valueOf(prefs.getString("familyMode", "ASK")!!) }.getOrDefault(FamilyMode.ASK),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lastAnnouncedKey: String? = null
    private var lastAnnouncedSeverity: Severity? = null
    private var briefingDone = false

    init {
        announcer.start()
        navigator.onRouteResult = { msg -> _ui.update { it.copy(message = msg) } }
        navigator.start()
        vehicleSource.start(object : VehicleSdkWarningSource.Listener {
            override fun onStatus(status: VehicleSdkWarningSource.Status) = _ui.update { it.copy(vehicle = status) }
            override fun onRangeRemaining(km: Float?) = _ui.update { it.copy(rangeKm = km) }
            override fun onVin(vin: String?) = _ui.update { it.copy(vin = vin) }
        })

        viewModelScope.launch {
            hub.active.collect { map ->
                val sorted = PriorityEngine.sorted(map.values)
                warningLog.record(prevActive, map)
                prevActive = map
                _ui.update { it.copy(warnings = sorted, logVersion = it.logVersion + 1) }
                if (map.containsKey(WarningId.LOW_FUEL)) vehicleSource.refreshRange()
                if (briefingDone) onTopChanged(sorted)
            }
        }

        // 시동 브리핑: 초기 경고 상태를 모을 시간을 잠깐 준 뒤 한 문장으로 요약한다.
        viewModelScope.launch {
            delay(2500)
            briefing()
        }
    }

    // ---------------- 음성 안내 ----------------

    fun briefing() {
        val warnings = _ui.value.warnings
        val top = warnings.firstOrNull()
        val text = if (top == null) {
            "경고등 통역사입니다. 현재 확인이 필요한 경고는 없습니다. 안전 운전하세요."
        } else {
            val info = WarningCatalog[top.id]
            val count = if (warnings.size > 1) "확인이 필요한 경고가 ${warnings.size}건 있습니다. 가장 중요한 것은 " else ""
            "경고등 통역사입니다. $count${displayName(top)}입니다. ${verdictSentence(top)} ${info.actionFor(top.state)}"
        }
        markAnnounced(top)
        briefingDone = true
        say(text)
    }

    private fun onTopChanged(sorted: List<ActiveWarning>) {
        val top = sorted.firstOrNull()
        val key = top?.let { "${it.id}:${it.state}" }
        if (key == lastAnnouncedKey) return

        if (top == null) {
            markAnnounced(null)
            say("켜져 있던 경고가 모두 꺼졌습니다.")
            _ui.update { it.copy(showEmergency = false) }
            return
        }
        val severity = PriorityEngine.severityOf(top)
        val escalated = lastAnnouncedSeverity != null && severity.rank > lastAnnouncedSeverity!!.rank
        markAnnounced(top)
        val newStop = severity == Severity.STOP_NOW && escalatedToStop(top) && familyReady()
        val mode = _ui.value.familyMode
        val tail = when {
            !newStop -> ""
            mode == FamilyMode.AUTO -> " 등록된 가족에게도 상황을 알립니다."
            else -> " 화면의 버튼을 누르면 가족에게도 알릴 수 있습니다."
        } + if (severity == Severity.STOP_NOW && onHighway()) " 고속도로에서는 ${ai.pleos.warninglight.navi.Emergency.HIGHWAY_SAFETY} 한국도로공사 긴급견인은 1588-2504입니다." else ""
        say(buildAnnouncement(top, escalated, sorted.size) + tail)
        if (newStop && mode == FamilyMode.AUTO) requestFamilySend()
    }

    private var captionJob: kotlinx.coroutines.Job? = null

    /** 음성 안내 + 같은 문장을 화면 자막으로 표시. */
    private fun say(text: String) {
        android.util.Log.i("Speech", text)
        announcer.speak(text)
        _ui.update { it.copy(caption = text) }
        captionJob?.cancel()
        captionJob = viewModelScope.launch {
            delay(text.length * 150L + 2000L)
            _ui.update { it.copy(caption = null) }
        }
    }

    private fun markAnnounced(top: ActiveWarning?) {
        lastAnnouncedKey = top?.let { "${it.id}:${it.state}" }
        lastAnnouncedSeverity = top?.let { PriorityEngine.severityOf(it) }
    }

    fun repeatCurrent() {
        val top = _ui.value.top ?: return briefing()
        say(buildAnnouncement(top, escalated = false, total = _ui.value.warnings.size))
    }

    private fun buildAnnouncement(w: ActiveWarning, escalated: Boolean, total: Int): String {
        val info = WarningCatalog[w.id]
        val sb = StringBuilder()
        if (escalated) sb.append("더 위급한 경고가 켜졌습니다. ")
        sb.append("${displayName(w)}입니다. ")
        sb.append(info.meaning).append(' ')
        if (info.affected.isNotEmpty()) {
            sb.append("${info.affected.joinToString(", ") { it.substringBefore(" (").substringBefore("(") }}에 영향이 있을 수 있습니다. ")
        }
        sb.append(verdictSentence(w)).append(' ')
        sb.append(info.actionFor(w.state))
        if (w.id == WarningId.LOW_FUEL) {
            _ui.value.rangeKm?.let { sb.append(" 남은 주행가능거리는 약 ${it.roundToInt()}킬로미터입니다.") }
        }
        if (total > 1) sb.append(" 이 밖에 ${total - 1}건의 경고가 더 켜져 있습니다.")
        return sb.toString()
    }

    private fun verdictSentence(w: ActiveWarning): String = when (PriorityEngine.severityOf(w)) {
        Severity.STOP_NOW -> "제조사 매뉴얼은 주행을 멈출 것을 권고합니다."
        Severity.SERVICE_SOON -> "서행 주행은 가능하지만 오늘 중 정비가 필요합니다."
        Severity.INFO -> "주행은 계속하셔도 됩니다."
    }

    fun displayName(w: ActiveWarning): String {
        val name = WarningCatalog[w.id].name
        return if (w.state == LampState.BLINK) "$name 점멸" else name
    }

    // ---------------- 연결 (정비소 / 긴급출동) ----------------

    fun findNearestAndRoute() = routeToNearest(ServiceCenters.demo, "정비소")

    fun findNearestGasStation() = routeToNearest(ServiceCenters.gasStations, "주유소")

    private fun routeToNearest(places: List<ServiceCenter>, kind: String) {
        navigator.currentLocation { loc ->
            val nearest = ServiceCenters.nearest(loc.lat, loc.lon, places)
            val msg = navigator.routeTo(nearest.first)
            _ui.update { it.copy(location = loc, nearest = nearest, message = msg) }
            say("가장 가까운 ${kind}는 약 ${"%.1f".format(nearest.second)}킬로미터 거리의 ${nearest.first.name.removeSuffix(" (데모)")}입니다. 길안내를 시작합니다.")
        }
    }

    fun openEmergency() {
        navigator.currentLocation { loc -> _ui.update { it.copy(location = loc) } }
        _ui.update { it.copy(showEmergency = true, emergencySince = it.top?.since) }
    }

    fun onHighway(): Boolean = ai.pleos.warninglight.navi.Emergency.isHighway(_ui.value.location?.address ?: navigator.lastKnown().address)

    fun closeEmergency() = _ui.update { it.copy(showEmergency = false) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // ---------------- 경고 블랙박스 ----------------

    fun openLog() = _ui.update { it.copy(showLog = true) }
    fun closeLog() = _ui.update { it.copy(showLog = false) }
    fun clearLog() {
        warningLog.clear()
        _ui.update { it.copy(logVersion = it.logVersion + 1) }
    }

    // ---------------- 가족에게 알리기 ----------------

    fun openFamily() {
        navigator.currentLocation { loc -> _ui.update { it.copy(location = loc) } }
        _ui.update { it.copy(showFamily = true) }
    }

    fun closeFamily() = _ui.update { it.copy(showFamily = false) }

    fun saveFamily(name: String, phone: String, mode: FamilyMode) {
        prefs.edit().putString("familyName", name.trim()).putString("familyPhone", phone.trim()).putString("familyMode", mode.name).apply()
        _ui.update { it.copy(familyName = name.trim(), familyPhone = phone.trim(), familyMode = mode) }
    }

    /** 가족 알림을 쓸 수 있는 상태 (연락처 등록 + 사용 안 함이 아님). */
    fun familyReady() = _ui.value.familyMode != FamilyMode.OFF && _ui.value.familyPhone.isNotBlank()

    /** 같은 즉시 정차 상황에서 중복 발송하지 않도록, 직전 대표 경고가 즉시 정차가 아니었을 때만 자동 발송. */
    private var lastStopKey: String? = null
    private fun escalatedToStop(top: ActiveWarning): Boolean {
        val key = "${top.id}:${top.since}"
        if (key == lastStopKey) return false
        lastStopKey = key
        return true
    }

    /** 가족 알림 발송 요청. 실제 발송 화면(문자 앱) 호출은 UI가 처리한다. */
    fun requestFamilySend() {
        navigator.currentLocation { loc ->
            _ui.update { it.copy(location = loc) }
            _ui.update { it.copy(familySendRequest = familyMessage(), familySentAt = System.currentTimeMillis()) }
        }
    }

    fun familySendHandled() = _ui.update { it.copy(familySendRequest = null) }

    /** 가족에게 보낼 문자 내용: 켜진 경고 전부(심각도·영향 기능), 안내, 위치. */
    fun familyMessage(): String {
        val s = _ui.value
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
        val sb = StringBuilder()
        if (s.warnings.isEmpty()) {
            sb.append("[경고등 통역사] 현재 확인이 필요한 차량 경고는 없습니다.")
        } else {
            val top = s.warnings.first()
            sb.append("[경고등 통역사] ${fmt.format(java.util.Date(top.since))} 차량 경고 ${s.warnings.size}건\n")
            s.warnings.forEach { w ->
                val info = WarningCatalog[w.id]
                sb.append("- ${displayName(w)}: ${PriorityEngine.severityOf(w).verdict}")
                if (info.affected.isNotEmpty()) sb.append(" (영향: ${info.affected.joinToString(", ")})")
                sb.append("\n")
            }
            sb.append("안내: ${WarningCatalog[top.id].actionFor(top.state)}")
        }
        s.location?.let { loc ->
            sb.append("\n위치: ").append(loc.address ?: "현재 위치")
            sb.append("\nhttps://map.kakao.com/link/map/차량위치,${"%.5f".format(loc.lat)},${"%.5f".format(loc.lon)}")
        }
        return sb.toString()
    }

    // ---------------- 시뮬레이션 ----------------

    fun simulate(id: WarningId, state: LampState?) = simulation.set(id, state)

    fun clearSimulation() = simulation.clearAll()

    override fun onCleared() {
        announcer.release()
        navigator.release()
        vehicleSource.release()
    }
}
