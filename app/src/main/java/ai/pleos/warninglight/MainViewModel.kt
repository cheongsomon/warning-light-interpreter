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

data class UiState(
    val warnings: List<ActiveWarning> = emptyList(),
    val vehicle: VehicleSdkWarningSource.Status = VehicleSdkWarningSource.Status(),
    val voiceEngine: String = "음성 준비 중",
    val rangeKm: Float? = null,
    val vin: String? = null,
    val nearest: Pair<ServiceCenter, Double>? = null,
    val location: Navigator.Location? = null,
    val message: String? = null,
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

    private val _ui = MutableStateFlow(UiState())
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
                _ui.update { it.copy(warnings = sorted) }
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
        announcer.speak(text)
    }

    private fun onTopChanged(sorted: List<ActiveWarning>) {
        val top = sorted.firstOrNull()
        val key = top?.let { "${it.id}:${it.state}" }
        if (key == lastAnnouncedKey) return

        if (top == null) {
            markAnnounced(null)
            announcer.speak("켜져 있던 경고가 모두 꺼졌습니다.")
            _ui.update { it.copy(showEmergency = false) }
            return
        }
        val severity = PriorityEngine.severityOf(top)
        val escalated = lastAnnouncedSeverity != null && severity.rank > lastAnnouncedSeverity!!.rank
        markAnnounced(top)
        announcer.speak(buildAnnouncement(top, escalated, sorted.size))
    }

    private fun markAnnounced(top: ActiveWarning?) {
        lastAnnouncedKey = top?.let { "${it.id}:${it.state}" }
        lastAnnouncedSeverity = top?.let { PriorityEngine.severityOf(it) }
    }

    fun repeatCurrent() {
        val top = _ui.value.top ?: return briefing()
        announcer.speak(buildAnnouncement(top, escalated = false, total = _ui.value.warnings.size))
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
            announcer.speak("가장 가까운 ${kind}는 약 ${"%.1f".format(nearest.second)}킬로미터 거리의 ${nearest.first.name.removeSuffix(" (데모)")}입니다. 길안내를 시작합니다.")
        }
    }

    fun openEmergency() {
        navigator.currentLocation { loc -> _ui.update { it.copy(location = loc) } }
        _ui.update { it.copy(showEmergency = true, emergencySince = it.top?.since) }
    }

    fun closeEmergency() = _ui.update { it.copy(showEmergency = false) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // ---------------- 시뮬레이션 ----------------

    fun simulate(id: WarningId, state: LampState?) = simulation.set(id, state)

    fun clearSimulation() = simulation.clearAll()

    override fun onCleared() {
        announcer.release()
        navigator.release()
        vehicleSource.release()
    }
}
