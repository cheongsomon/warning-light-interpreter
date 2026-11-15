package ai.pleos.warninglight.source

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.warninglight.data.LampState
import ai.pleos.warninglight.data.Origin
import ai.pleos.warninglight.data.WarningId
import android.content.Context
import android.util.Log

/**
 * Pleos Vehicle SDK에서 경고 신호를 받는다.
 * - Safety.registerTelltales: 경고등 목록 (종류 + OFF/ON/BLINK)
 * - Safety 엔진오일·브레이크액 레벨, 워셔액 부족
 * - Odometer 남은 주행가능거리, CarInfo VIN (안내 문구·긴급출동 요약용)
 *
 * 모든 기능은 capability를 먼저 확인하고, 실패하면 해당 기능만 비활성화한다.
 */
class VehicleSdkWarningSource(
    context: Context,
    private val hub: WarningHub,
) {
    data class Status(
        val connected: Boolean = false,
        val telltales: Boolean = false,
        val message: String = "차량 신호 연결 중…",
    )

    interface Listener {
        fun onStatus(status: Status)
        fun onRangeRemaining(km: Float?) {}
        fun onVin(vin: String?) {}
    }

    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false
    private var listener: Listener? = null
    private var status = Status()

    // 출처별 현재 상태. 경고등 목록과 개별 신호를 합쳐 hub에 전체 교체로 보고한다.
    private val telltaleStates = mutableMapOf<WarningId, LampState>()
    private val signalStates = mutableMapOf<WarningId, LampState>()

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) { telltales?.let(::applyTelltales) }
        override fun onFailed(e: Exception) { log("telltale listener", e) }
    }
    private val brakeFluidListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) = applyLevel(WarningId.BRAKE_FLUID, state)
        override fun onFailed(e: Exception) { log("brake fluid listener", e) }
    }
    private val engineOilListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) = applyLevel(WarningId.ENGINE_OIL_LEVEL, state)
        override fun onFailed(e: Exception) { log("engine oil listener", e) }
    }
    private val washerListener = object : LowWasherFluidWaringListener {
        override fun onLowWasherFluidWaringUpdated(low: Boolean?) =
            setSignal(WarningId.WASHER_FLUID, if (low == true) LampState.ON else null)
        override fun onFailed(e: Exception) { log("washer listener", e) }
    }

    fun start(listener: Listener) {
        this.listener = listener
        try {
            vehicle.initialize()
            initialized = true
        } catch (e: Exception) {
            log("initialize", e)
            update(Status(false, false, "차량 신호 사용 불가 · 시뮬레이션으로 안내"))
            return
        }
        update(status.copy(connected = true, message = "차량 신호 연결됨"))

        val safety = vehicle.getSafety()
        safely("telltale capability") {
            safety.checkTelltalesCapabilities(
                onComplete = { _ ->
                    safely("telltale read") {
                        safety.getTelltalesInfo(onComplete = ::applyTelltales, onFailed = { log("telltale read", it) })
                    }
                    safely("telltale register") {
                        safety.registerTelltales(telltaleListener)
                        update(status.copy(telltales = true, message = "차량 경고등 신호 수신 중"))
                    }
                },
                onFailed = {
                    log("telltale capability", it)
                    update(status.copy(telltales = false, message = "경고등 신호 미지원 · 시뮬레이션으로 안내"))
                },
            )
        }
        safely("brake fluid") {
            safety.checkBrakeFluidLevelStateCapabilities(
                onComplete = { _, _ -> safely("brake fluid register") { safety.registerBrakeFluidLevelState(brakeFluidListener) } },
                onFailed = { log("brake fluid capability", it) },
            )
        }
        safely("engine oil") {
            safety.checkEngineOilLevelStateCapabilities(
                onComplete = { _, _ -> safely("engine oil register") { safety.registerEngineOilLevelState(engineOilListener) } },
                onFailed = { log("engine oil capability", it) },
            )
        }
        safely("washer") {
            safety.checkLowWasherFluidWarningStateCapabilities(
                onComplete = { _ -> safely("washer register") { safety.registerLowWasherFluidWaringState(washerListener) } },
                onFailed = { log("washer capability", it) },
            )
        }
        refreshVin()
    }

    fun refreshRange() = safely("range") {
        vehicle.getOdometer().getRangeRemainingDistance(
            onComplete = { listener?.onRangeRemaining(it) },
            onFailed = { log("range", it); listener?.onRangeRemaining(null) },
        )
    }

    private fun refreshVin() = safely("vin") {
        vehicle.getCarInfo().getVINInfo(
            onComplete = { listener?.onVin(it) },
            onFailed = { log("vin", it) },
        )
    }

    fun release() {
        if (!initialized) return
        val safety = vehicle.getSafety()
        safely("unregister") {
            safety.unregisterTelltales(telltaleListener)
            safety.unregisterBrakeFluidLevelState(brakeFluidListener)
            safety.unregisterEngineOilLevelState(engineOilListener)
            safety.unregisterLowWasherFluidWaringState(washerListener)
        }
        safely("release") { vehicle.release() }
        initialized = false
    }

    // ---- 신호 → 경고 변환 ----

    private fun applyTelltales(telltales: Telltales) {
        val next = mutableMapOf<WarningId, LampState>()
        for (t in telltales.telltales) {
            val id = TELLTALE_MAP[t.type] ?: continue
            val state = when (t.state) {
                TelltaleState.ON -> LampState.ON
                TelltaleState.BLINK -> LampState.BLINK
                else -> null
            } ?: continue
            next[id] = state
        }
        synchronized(this) {
            telltaleStates.clear()
            telltaleStates.putAll(next)
            publish()
        }
    }

    private fun applyLevel(id: WarningId, state: OilLevelState?) {
        val on = state == OilLevelState.LOW || state == OilLevelState.CRITICALLY_LOW
        setSignal(id, if (on) LampState.ON else null)
    }

    private fun setSignal(id: WarningId, state: LampState?) = synchronized(this) {
        if (state == null) signalStates.remove(id) else signalStates[id] = state
        publish()
    }

    private fun publish() {
        hub.replace(Origin.VEHICLE, signalStates + telltaleStates)
    }

    private fun update(s: Status) {
        status = s
        listener?.onStatus(s)
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log(what, e)
        }
    }

    private fun log(what: String, e: Exception) { Log.w(TAG, "$what failed: ${e.message}") }

    companion object {
        private const val TAG = "VehicleSource"

        /** SDK 경고등 종류 → 앱 경고. 매핑되지 않은 경고등(방향지시등 등)은 무시한다. */
        val TELLTALE_MAP: Map<TelltaleType, WarningId> = mapOf(
            TelltaleType.ENGINE_OIL_PRESSURE to WarningId.ENGINE_OIL_PRESSURE,
            TelltaleType.ENGINE_TEMPERATURE to WarningId.ENGINE_TEMPERATURE,
            TelltaleType.ENGINE to WarningId.ENGINE,
            TelltaleType.EV_SYSTEM to WarningId.EV_SYSTEM,
            TelltaleType.MDPS to WarningId.MDPS,
            TelltaleType.AIRBAG to WarningId.AIRBAG,
            TelltaleType.ABS to WarningId.ABS,
            TelltaleType.LOW_VOLT_BATTERY to WarningId.CHARGING_SYSTEM,
            TelltaleType.TPMS to WarningId.TPMS,
            TelltaleType.LOW_FUEL to WarningId.LOW_FUEL,
        )
    }
}
