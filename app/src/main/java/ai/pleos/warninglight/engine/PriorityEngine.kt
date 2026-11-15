package ai.pleos.warninglight.engine

import ai.pleos.warninglight.data.ActiveWarning
import ai.pleos.warninglight.data.Severity
import ai.pleos.warninglight.data.WarningCatalog

/**
 * 복수 경고 동시 발생 시 가장 높은 심각도의 경고를 대표로 고른다.
 * 심각도가 같으면 가장 최근에 켜진 경고를 고른다.
 * 점멸 상태는 WarningInfo.blinkSeverity로 승격 반영된다.
 */
object PriorityEngine {

    fun severityOf(w: ActiveWarning): Severity = WarningCatalog[w.id].severityFor(w.state)

    fun sorted(active: Collection<ActiveWarning>): List<ActiveWarning> =
        active.sortedWith(
            compareByDescending<ActiveWarning> { severityOf(it).rank }
                .thenByDescending { it.since }
        )

    fun top(active: Collection<ActiveWarning>): ActiveWarning? = sorted(active).firstOrNull()
}
