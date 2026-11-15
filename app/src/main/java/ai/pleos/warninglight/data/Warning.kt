package ai.pleos.warninglight.data

/** 안내 심각도. 숫자가 클수록 우선한다. */
enum class Severity(val rank: Int, val verdict: String, val shortAction: String) {
    INFO(1, "주행 계속 가능", "다음 정차 때 확인하세요"),
    SERVICE_SOON(2, "서행 주행 가능 · 오늘 중 정비", "가까운 정비소로 이동하세요"),
    STOP_NOW(3, "즉시 정차", "안전한 곳에 즉시 정차하세요"),
}

/** 경고등 표시 상태. SDK TelltaleState(ON/BLINK)와 1:1 대응. */
enum class LampState { ON, BLINK }

/** 경고 신호 출처. 화면에 정직하게 표시한다. */
enum class Origin { VEHICLE, SIMULATION }

/** 안내 후 연결할 다음 행동. */
enum class NextAction { NONE, SERVICE_CENTER, GAS_STATION, EMERGENCY }

/** 앱이 다루는 경고 종류. SDK의 경고등(Telltale) 또는 개별 상태 신호에서 만들어진다. */
enum class WarningId {
    BRAKE_FLUID,
    ENGINE_OIL_PRESSURE,
    ENGINE_TEMPERATURE,
    ENGINE,
    EV_SYSTEM,
    MDPS,
    AIRBAG,
    ABS,
    CHARGING_SYSTEM,
    TPMS,
    ENGINE_OIL_LEVEL,
    LOW_FUEL,
    WASHER_FLUID,
}

data class ActiveWarning(
    val id: WarningId,
    val state: LampState,
    val origin: Origin,
    val since: Long,
)

/**
 * 경고 하나에 대한 안내 문구.
 * 문구는 '판정'이 아니라 제조사 매뉴얼 권고를 쉬운 말로 옮긴 '안내'다.
 */
data class WarningInfo(
    val id: WarningId,
    val name: String,
    val meaning: String,
    val severity: Severity,
    /** 점멸 시 승격되는 심각도 (없으면 점등과 동일). */
    val blinkSeverity: Severity? = null,
    val action: String,
    val blinkAction: String? = null,
    /** 이 경고가 켜졌을 때 제대로 동작하지 않을 수 있는 기능 (매뉴얼 기준, 짧은 명사구). */
    val affected: List<String> = emptyList(),
) {
    fun severityFor(state: LampState): Severity =
        if (state == LampState.BLINK) blinkSeverity ?: severity else severity

    fun actionFor(state: LampState): String =
        if (state == LampState.BLINK) blinkAction ?: action else action

    fun nextActionFor(state: LampState): NextAction = if (id == WarningId.LOW_FUEL) NextAction.GAS_STATION else when (severityFor(state)) {
        Severity.STOP_NOW -> NextAction.EMERGENCY
        Severity.SERVICE_SOON -> NextAction.SERVICE_CENTER
        Severity.INFO -> NextAction.NONE
    }
}
