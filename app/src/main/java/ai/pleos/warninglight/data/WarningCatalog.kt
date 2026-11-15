package ai.pleos.warninglight.data

import ai.pleos.warninglight.data.Severity.INFO
import ai.pleos.warninglight.data.Severity.SERVICE_SOON
import ai.pleos.warninglight.data.Severity.STOP_NOW

/**
 * 경고 → 안내 문구 매핑표.
 *
 * 주의: 데모용 초안이다. 실제 서비스에서는 차종별 제조사 매뉴얼 문구로 확정해야 한다.
 * 앱은 진단하지 않으며, 클러스터 경고 표시를 대체하지 않는다.
 */
object WarningCatalog {

    private val entries: Map<WarningId, WarningInfo> = listOf(
        WarningInfo(
            id = WarningId.BRAKE_FLUID,
            name = "브레이크 경고",
            meaning = "브레이크액이 부족하거나 제동 장치에 이상이 있을 수 있다는 신호입니다.",
            severity = STOP_NOW,
            action = "제동력이 약해질 수 있습니다. 안전한 곳에 즉시 정차하고 긴급출동을 요청하세요.",
            affected = listOf("제동 성능"),
        ),
        WarningInfo(
            id = WarningId.ENGINE_OIL_PRESSURE,
            name = "엔진오일 압력 경고",
            meaning = "엔진오일 압력이 낮아 엔진이 제대로 윤활되지 않고 있다는 신호입니다.",
            severity = STOP_NOW,
            action = "계속 달리면 엔진이 손상될 수 있습니다. 안전한 곳에 즉시 정차한 뒤 시동을 끄고 긴급출동을 요청하세요.",
            affected = listOf("엔진 윤활", "엔진 보호"),
        ),
        WarningInfo(
            id = WarningId.ENGINE_TEMPERATURE,
            name = "엔진 과열 경고",
            meaning = "엔진 냉각수 온도가 너무 높다는 신호입니다.",
            severity = STOP_NOW,
            action = "안전한 곳에 즉시 정차하세요. 엔진이 식기 전에는 냉각수 캡을 열지 말고 긴급출동을 요청하세요.",
            affected = listOf("엔진 냉각", "엔진 출력"),
        ),
        WarningInfo(
            id = WarningId.ENGINE,
            name = "엔진 경고등",
            meaning = "엔진이나 배출가스 관련 장치에 점검이 필요하다는 신호입니다.",
            severity = SERVICE_SOON,
            blinkSeverity = STOP_NOW,
            action = "급가속을 피하고 오늘 중 가까운 정비소에서 점검을 받으세요.",
            blinkAction = "경고등이 깜박이고 있습니다. 엔진 손상 위험이 있으니 속도를 줄이고 안전한 곳에 즉시 정차하세요.",
            affected = listOf("엔진 출력", "배출가스 정화 장치"),
        ),
        WarningInfo(
            id = WarningId.EV_SYSTEM,
            name = "전기차 시스템 경고",
            meaning = "구동 모터나 고전압 배터리 등 전기차 시스템에 이상이 있을 수 있다는 신호입니다.",
            severity = STOP_NOW,
            action = "안전한 곳에 즉시 정차하고 긴급출동을 요청하세요.",
            affected = listOf("구동 출력", "주행 기능"),
        ),
        WarningInfo(
            id = WarningId.MDPS,
            name = "전동식 조향장치 경고",
            meaning = "핸들을 가볍게 돌려주는 조향 보조 장치에 이상이 있다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "핸들이 무거워질 수 있습니다. 서행하여 가까운 정비소에서 점검을 받으세요.",
            affected = listOf("조향 보조 (핸들이 무거워짐)"),
        ),
        WarningInfo(
            id = WarningId.AIRBAG,
            name = "에어백 경고",
            meaning = "에어백 장치에 이상이 있어 사고 시 에어백이 작동하지 않을 수 있다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "주행은 가능하지만 오늘 중 정비소에서 점검을 받으세요.",
            affected = listOf("사고 시 에어백 전개", "안전벨트 프리텐셔너"),
        ),
        WarningInfo(
            id = WarningId.ABS,
            name = "ABS 경고",
            meaning = "급제동 때 바퀴 잠김을 막아주는 ABS 장치에 이상이 있다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "일반 제동은 가능하지만 급제동을 피하고 오늘 중 점검을 받으세요.",
            affected = listOf("급제동 시 바퀴 잠김 방지(ABS)", "차체 자세 제어(ESC)"),
        ),
        WarningInfo(
            id = WarningId.CHARGING_SYSTEM,
            name = "충전 경고",
            meaning = "배터리 충전 장치에 이상이 있어 전기 장치가 곧 멈출 수 있다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "에어컨 등 전기 사용을 줄이고 가까운 정비소로 바로 이동하세요.",
            affected = listOf("배터리 충전", "에어컨·열선 등 전기 장치", "시동 유지"),
        ),
        WarningInfo(
            id = WarningId.TPMS,
            name = "타이어 공기압 경고",
            meaning = "공기압이 낮은 타이어가 있다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "급가속과 급제동을 피하고 서행하여 오늘 중 공기압을 점검하세요.",
            blinkAction = "타이어 공기압 감지 장치에 이상이 있을 수 있습니다. 서행하여 오늘 중 점검을 받으세요.",
            affected = listOf("타이어 공기압 감시", "제동·코너링 안정성"),
        ),
        WarningInfo(
            id = WarningId.ENGINE_OIL_LEVEL,
            name = "엔진오일 부족",
            meaning = "엔진오일 양이 적정량보다 적다는 신호입니다.",
            severity = SERVICE_SOON,
            action = "가까운 정비소에서 엔진오일을 점검하고 보충하세요.",
            affected = listOf("엔진 윤활"),
        ),
        WarningInfo(
            id = WarningId.LOW_FUEL,
            name = "연료 부족",
            meaning = "연료가 얼마 남지 않았다는 신호입니다.",
            severity = INFO,
            action = "아래 버튼으로 가까운 주유소 길안내를 받을 수 있습니다.",
        ),
        WarningInfo(
            id = WarningId.WASHER_FLUID,
            name = "워셔액 부족",
            meaning = "앞유리 워셔액이 얼마 남지 않았다는 신호입니다.",
            severity = INFO,
            action = "다음 정차 때 워셔액을 보충하세요.",
            affected = listOf("앞유리 세정"),
        ),
    ).associateBy { it.id }

    operator fun get(id: WarningId): WarningInfo = entries.getValue(id)

    val all: List<WarningInfo> get() = entries.values.toList()
}
