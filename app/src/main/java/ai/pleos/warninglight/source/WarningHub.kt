package ai.pleos.warninglight.source

import ai.pleos.warninglight.data.ActiveWarning
import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.WarningId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 경고 입력 계층. 실제 차량 신호(VehicleSdkWarningSource)와
 * 시뮬레이션(SimulatedWarningSource)이 같은 방식으로 이곳에 보고한다.
 * 같은 경고가 양쪽에서 들어오면 실제 차량 신호를 우선한다.
 */
class WarningHub {
    private val vehicle = MutableStateFlow<Map<WarningId, ActiveWarning>>(emptyMap())
    private val simulation = MutableStateFlow<Map<WarningId, ActiveWarning>>(emptyMap())
    private val _active = MutableStateFlow<Map<WarningId, ActiveWarning>>(emptyMap())
    val active: StateFlow<Map<WarningId, ActiveWarning>> = _active.asStateFlow()

    /** 한 출처의 경고 전체 상태를 교체한다 (SDK 경고등 목록은 매번 전체가 온다). */
    fun replace(origin: Origin, states: Map<WarningId, LampState>) {
        val target = flowOf(origin)
        target.update { old ->
            states.mapValues { (id, state) ->
                val prev = old[id]
                // 같은 상태가 계속 들어오면 최초 발생 시각을 유지한다.
                if (prev != null && prev.state == state) prev
                else ActiveWarning(id, state, origin, System.currentTimeMillis())
            }
        }
        merge()
    }

    /** 한 출처의 경고 하나를 켜거나(state != null) 끈다(state == null). */
    fun set(origin: Origin, id: WarningId, state: LampState?) {
        val target = flowOf(origin)
        target.update { old ->
            if (state == null) old - id
            else {
                val prev = old[id]
                if (prev != null && prev.state == state) old
                else old + (id to ActiveWarning(id, state, origin, System.currentTimeMillis()))
            }
        }
        merge()
    }

    fun clear(origin: Origin) {
        flowOf(origin).value = emptyMap()
        merge()
    }

    private fun flowOf(origin: Origin) = when (origin) {
        Origin.VEHICLE -> vehicle
        Origin.SIMULATION -> simulation
    }

    @Synchronized
    private fun merge() {
        _active.value = simulation.value + vehicle.value
    }
}
