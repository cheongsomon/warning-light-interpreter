package ai.pleos.warninglight.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 경고 블랙박스.
 *
 * 기존 고장진단(DTC)은 '현재/과거 고장' 상태만 남기고 언제 발생했는지는 남기지 않는다.
 * 그래서 잠깐 켜졌다 꺼지는 간헐 경고는 정비소에 도착하면 재현되지 않는다.
 * 이 기록은 경고가 켜지고·바뀌고·꺼진 시각과 지속시간, 함께 켜진 경고를 시간순으로 남긴다.
 *
 * 보존 기간 (심각도별):
 *  - 정보(INFO): 7일 후 자동 삭제
 *  - 정비 권장(SERVICE_SOON): 30일 유지
 *  - 즉시 정차(STOP_NOW): 사용자가 지우기 전까지 영구 보존
 */
class WarningLog(context: Context) {

    enum class Kind(val label: String) { ON("점등"), BLINK("점멸"), OFF("꺼짐") }

    data class Event(
        val time: Long,
        val id: WarningId,
        val kind: Kind,
        val origin: Origin,
        /** OFF일 때: 켜져 있던 시간(ms). */
        val durationMs: Long? = null,
        /** 이 시점에 함께 켜져 있던 다른 경고. */
        val concurrent: List<WarningId> = emptyList(),
        /** 기록 당시 심각도 (점멸 승격 반영). 보존 기간을 정한다. */
        val severity: Severity = WarningCatalog[id].severity,
    )

    data class Summary(
        val id: WarningId,
        val count: Int,
        val blinkCount: Int,
        val shortestOnMs: Long?,
        val intermittent: Boolean,
    )

    private val file = File(context.filesDir, "warning_log.json")
    private val events = mutableListOf<Event>()

    init {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) events += arr.getJSONObject(i).toEvent()
            }
        }
        if (prune(System.currentTimeMillis())) save()
    }

    /** 보존 기간이 지난 기록을 지운다. 지운 것이 있으면 true. */
    private fun prune(now: Long): Boolean {
        val before = events.size
        events.removeAll { e -> retentionMs(e.severity)?.let { now - e.time > it } ?: false }
        return events.size != before
    }

    @Synchronized
    fun all(): List<Event> = events.toList()

    /** 이전/현재 활성 경고를 비교해 상태 변화만 기록한다. */
    @Synchronized
    fun record(prev: Map<WarningId, ActiveWarning>, now: Map<WarningId, ActiveWarning>) {
        val t = System.currentTimeMillis()
        var changed = false
        for ((id, w) in now) {
            val before = prev[id]
            if (before == null || before.state != w.state) {
                val kind = if (w.state == LampState.BLINK) Kind.BLINK else Kind.ON
                events += Event(t, id, kind, w.origin, concurrent = (now.keys - id).toList(),
                    severity = WarningCatalog[id].severityFor(w.state))
                changed = true
            }
        }
        for ((id, w) in prev) {
            if (id !in now) {
                events += Event(t, id, Kind.OFF, w.origin, durationMs = t - w.since, concurrent = now.keys.toList(),
                    severity = WarningCatalog[id].severityFor(w.state))
                changed = true
            }
        }
        changed = prune(t) || changed
        if (changed) save()
    }

    @Synchronized
    fun clear() {
        events.clear()
        save()
    }

    /** 경고별 요약. 10분 안에 꺼졌거나 2회 이상 반복되면 '간헐 경고'로 표시한다. */
    @Synchronized
    fun summaries(): List<Summary> = events.groupBy { it.id }.map { (id, list) ->
        val ons = list.count { it.kind != Kind.OFF }
        val shortest = list.mapNotNull { it.durationMs }.minOrNull()
        Summary(
            id = id,
            count = ons,
            blinkCount = list.count { it.kind == Kind.BLINK },
            shortestOnMs = shortest,
            intermittent = ons >= 2 || (shortest != null && shortest < 10 * 60_000),
        )
    }.sortedByDescending { it.count }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            events.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        }
    }

    private fun Event.toJson() = JSONObject().apply {
        put("t", time); put("id", id.name); put("k", kind.name); put("o", origin.name)
        durationMs?.let { put("d", it) }
        put("c", JSONArray(concurrent.map { it.name }))
        put("s", severity.name)
    }

    private fun JSONObject.toEvent() = Event(
        time = getLong("t"),
        id = WarningId.valueOf(getString("id")),
        kind = Kind.valueOf(getString("k")),
        origin = Origin.valueOf(getString("o")),
        durationMs = if (has("d")) getLong("d") else null,
        concurrent = getJSONArray("c").let { a -> (0 until a.length()).map { WarningId.valueOf(a.getString(it)) } },
        severity = if (has("s")) Severity.valueOf(getString("s")) else WarningCatalog[WarningId.valueOf(getString("id"))].severity,
    )

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000

        /** 심각도별 보존 기간. null = 사용자가 지우기 전까지 영구. */
        fun retentionMs(s: Severity): Long? = when (s) {
            Severity.INFO -> 7 * DAY
            Severity.SERVICE_SOON -> 30 * DAY
            Severity.STOP_NOW -> null
        }

        fun retentionLabel(s: Severity): String = when (s) {
            Severity.INFO -> "7일 보관"
            Severity.SERVICE_SOON -> "30일 보관"
            Severity.STOP_NOW -> "영구 보관"
        }
    }
}
