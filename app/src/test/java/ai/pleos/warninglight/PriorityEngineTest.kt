package ai.pleos.warninglight

import ai.pleos.warninglight.data.ActiveWarning
import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.Severity
import ai.pleos.warninglight.data.WarningId
import ai.pleos.warninglight.engine.PriorityEngine
import ai.pleos.warninglight.source.WarningHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriorityEngineTest {
    private fun w(id: WarningId, state: LampState = LampState.ON, t: Long = 0) =
        ActiveWarning(id, state, Origin.SIMULATION, t)

    @Test fun `경고가 없으면 대표 경고도 없다`() = assertNull(PriorityEngine.top(emptyList()))

    @Test fun `복수 경고는 최고 심각도 기준`() {
        val top = PriorityEngine.top(listOf(w(WarningId.TPMS, t = 2), w(WarningId.BRAKE_FLUID, t = 1), w(WarningId.WASHER_FLUID, t = 3)))
        assertEquals(WarningId.BRAKE_FLUID, top!!.id)
    }

    @Test fun `엔진 경고등 점멸은 즉시 정차로 승격`() {
        assertEquals(Severity.SERVICE_SOON, PriorityEngine.severityOf(w(WarningId.ENGINE)))
        assertEquals(Severity.STOP_NOW, PriorityEngine.severityOf(w(WarningId.ENGINE, LampState.BLINK)))
        val top = PriorityEngine.top(listOf(w(WarningId.TPMS, t = 5), w(WarningId.ENGINE, LampState.BLINK, t = 1)))
        assertEquals(WarningId.ENGINE, top!!.id)
    }

    @Test fun `같은 심각도면 최근 경고 우선`() {
        val top = PriorityEngine.top(listOf(w(WarningId.TPMS, t = 1), w(WarningId.AIRBAG, t = 9)))
        assertEquals(WarningId.AIRBAG, top!!.id)
    }

    @Test fun `같은 경고는 차량 신호가 시뮬레이션보다 우선`() {
        val hub = WarningHub()
        hub.set(Origin.SIMULATION, WarningId.ENGINE, LampState.ON)
        hub.replace(Origin.VEHICLE, mapOf(WarningId.ENGINE to LampState.BLINK))
        val e = hub.active.value.getValue(WarningId.ENGINE)
        assertEquals(Origin.VEHICLE, e.origin)
        assertEquals(LampState.BLINK, e.state)
        hub.replace(Origin.VEHICLE, emptyMap())
        assertEquals(Origin.SIMULATION, hub.active.value.getValue(WarningId.ENGINE).origin)
    }
}
