package ai.pleos.warninglight.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * NaviHelper SDK 래퍼.
 * - getCurrentLocationInfo()로 현재 위치를 받아 가장 가까운 정비소를 고른다.
 * - requestRoute(RouteInfo)로 내비게이션 목적지를 설정한다.
 * 실패해도 앱이 멈추지 않도록 결과를 문장으로 돌려준다.
 */
class Navigator(context: Context) {

    data class Location(val lat: Double, val lon: Double, val address: String?, val fromSdk: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    private var ready = false
    private var lastLocation: Location? = null
    private var pendingLocation: ((Location) -> Unit)? = null
    var onRouteResult: ((String) -> Unit)? = null

    private val events = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            val loc = Location(info.latitude, info.longitude, info.address, fromSdk = true)
            lastLocation = loc
            main.post { pendingLocation?.invoke(loc); pendingLocation = null }
        }

        override fun onRoutingCompleted() {
            main.post { onRouteResult?.invoke("내비게이션 길안내를 시작했습니다.") }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error: $errorCode")
            main.post { onRouteResult?.invoke("길안내 요청 실패 ($errorCode)") }
        }
    }

    fun start() {
        try {
            navi.initialize(
                { ready = true; navi.addListener(events); requestLocationUpdate() },
                { e -> Log.w(TAG, "navi init failed: $e") },
            )
        } catch (e: Exception) {
            Log.w(TAG, "navi unavailable: ${e.message}")
        }
    }

    private fun requestLocationUpdate() {
        runCatching { navi.getCurrentLocationInfo() }.onFailure { Log.w(TAG, "location: ${it.message}") }
    }

    /** 현재 위치. SDK 응답이 1.5초 안에 없으면 데모 기준 위치를 쓴다. */
    fun currentLocation(callback: (Location) -> Unit) {
        lastLocation?.let { callback(it) }
        if (!ready) {
            if (lastLocation == null) callback(fallback())
            return
        }
        if (lastLocation == null) {
            pendingLocation = callback
            main.postDelayed({
                pendingLocation?.let { it(fallback()); pendingLocation = null }
            }, 1500)
        }
        requestLocationUpdate()
    }

    fun lastKnown(): Location = lastLocation ?: fallback()

    private fun fallback() = Location(ServiceCenters.DEFAULT_LAT, ServiceCenters.DEFAULT_LON, null, fromSdk = false)

    /** 목적지 설정 요청. 성공/실패는 onRouteResult로도 알린다. */
    fun routeTo(center: ServiceCenter): String {
        if (!ready) return "내비게이션 연결 안 됨 · ${center.name} (${center.address})"
        return try {
            navi.requestRoute(
                RouteInfo(
                    latitude = center.latitude,
                    longitude = center.longitude,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = center.name,
                    poiId = "",
                    address = center.address,
                    poiSubId = "0",
                )
            )
            "${center.name}(으)로 길안내를 요청했습니다."
        } catch (e: Exception) {
            Log.w(TAG, "requestRoute failed: ${e.message}")
            "길안내 요청 실패 · ${center.name} (${center.address})"
        }
    }

    fun release() {
        runCatching { navi.removeListener(events); navi.release() }
    }

    companion object {
        private const val TAG = "Navigator"
    }
}
