package ai.pleos.warninglight.navi

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ServiceCenter(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
)

/**
 * 데모용 고정 정비소 데이터 (판교·분당 일대).
 * TODO(사용자 확인): 실제 블루핸즈 지점명·좌표·주소로 교체. 실서비스에서는 정비망 API로 대체한다.
 */
object ServiceCenters {
    val demo = listOf(
        ServiceCenter("블루핸즈 판교점 (데모)", 37.4020, 127.1086, "경기 성남시 분당구 판교역로 일대"),
        ServiceCenter("블루핸즈 정자점 (데모)", 37.3663, 127.1084, "경기 성남시 분당구 정자동 일대"),
        ServiceCenter("블루핸즈 서현점 (데모)", 37.3847, 127.1233, "경기 성남시 분당구 서현동 일대"),
        ServiceCenter("블루핸즈 수지점 (데모)", 37.3222, 127.0950, "경기 용인시 수지구 일대"),
    )

    /** 위치를 못 받았을 때 쓰는 데모 기준 위치 (판교테크노밸리). */
    const val DEFAULT_LAT = 37.4005
    const val DEFAULT_LON = 127.1055

    /** 데모용 고정 주유소 데이터. 실서비스에서는 주유소 검색 API로 대체한다. */
    val gasStations = listOf(
        ServiceCenter("판교 주유소 (데모)", 37.3985, 127.1120, "경기 성남시 분당구 판교 일대"),
        ServiceCenter("백현 주유소 (데모)", 37.3890, 127.1010, "경기 성남시 분당구 백현동 일대"),
        ServiceCenter("시흥동 주유소 (데모)", 37.4170, 127.0985, "경기 성남시 수정구 시흥동 일대"),
    )

    fun nearest(lat: Double, lon: Double, places: List<ServiceCenter> = demo): Pair<ServiceCenter, Double> =
        places.map { it to distanceKm(lat, lon, it.latitude, it.longitude) }.minBy { it.second }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** 긴급 연락처. */
object Emergency {
    const val PHONE = "080-600-6000"
    const val LABEL = "현대자동차 긴급출동"

    /** 고속도로 고장·사고 시 가까운 안전지대까지 무료 긴급견인 (24시간). */
    const val HIGHWAY_PHONE = "1588-2504"
    const val HIGHWAY_LABEL = "한국도로공사 긴급견인"
    const val HIGHWAY_SAFETY = "비상등을 켜고 가드레일 밖 안전한 곳으로 먼저 대피한 뒤 전화하세요."

    /** 주소로 고속도로 여부를 추정한다 (NaviHelper에 도로 종류 정보가 없음). */
    fun isHighway(address: String?): Boolean =
        address != null && (address.contains("고속도로") || address.contains("고속국도"))
}
