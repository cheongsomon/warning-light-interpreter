package ai.pleos.warninglight.source

import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.WarningId

/**
 * 화면의 시뮬레이션 패널에서 경고를 발생시킨다.
 * 차량 신호를 받을 수 없는 환경(SDK 미지원, 데모 촬영)을 위한 입력이며,
 * 이 경로로 들어온 경고는 화면에 'SIM'으로 표시된다.
 */
class SimulatedWarningSource(private val hub: WarningHub) {
    fun set(id: WarningId, state: LampState?) = hub.set(Origin.SIMULATION, id, state)
    fun clearAll() = hub.clear(Origin.SIMULATION)
}
