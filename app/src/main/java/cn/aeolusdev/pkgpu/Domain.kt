package cn.aeolusdev.pkgpu

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CarrierRecognizer {
    private val rules = listOf(
        "顺丰速运" to Regex("^SF[0-9]{13}$|^[0-9]{12}$"),
        "京东快递" to Regex("^JD[0-9]{13,15}$|^JDV[0-9]{12}$"),
        "邮政EMS" to Regex("^[A-Z]{2}[0-9]{9}[A-Z]{2}$|^9[89][0-9]{11}$|^1[12][0-9]{11}$"),
        "圆通速递" to Regex("^YT[0-9]{13}$|^8[0-9]{17}$"),
        "极兔速递" to Regex("^JT[0-9]{13}$|^[0-9]{15}$"),
        "中通快递" to Regex("^[7V][0-9]{13}$"),
        "申通快递" to Regex("^(77|88|99)[0-9]{10,13}$"),
        "菜鸟裹裹" to Regex("^9[0-9]{14}$"),
        "韵达快递" to Regex("^(43|46|12)[0-9]{11,13}$")
    )

    fun recognize(code: String): String {
        val target = code.trim().uppercase()
        if (target.isEmpty()) return "未知承运商"
        return rules.firstOrNull { it.second.matches(target) }?.first ?: "未知承运商"
    }
}

object TimeUtils {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun elapsedText(addTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffHours = ((nowMillis - addTimeMillis).coerceAtLeast(0L)) / (1000L * 60L * 60L)
        return if (diffHours >= 24L) {
            val days = diffHours / 24L
            "已入站：${days}天"
        } else {
            "已入站：${diffHours}小时"
        }
    }

    fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}

object StationSorter {
    fun sort(stations: List<Station>, packages: List<PackageEntry>, now: LocalTime = LocalTime.now()): List<Station> {
        val waiting = packages.filter { !it.picked }
        val countMap = waiting.groupingBy { it.inStation }.eachCount()
        val oldestMap = waiting.groupBy { it.inStation }
            .mapValues { (_, items) -> items.minOfOrNull { it.addTime } ?: Long.MAX_VALUE }

        return stations.sortedWith(
            compareByDescending<Station> { isOpen(it.openTime, now) }
                .thenByDescending { countMap[it.stationId] ?: 0 }
                .thenBy { oldestMap[it.stationId] ?: Long.MAX_VALUE }
                .thenBy { it.stationId }
        )
    }

    fun isOpen(openTime: String, now: LocalTime = LocalTime.now()): Boolean {
        val parts = openTime.split("-")
        if (parts.size != 2) return true
        val start = runCatching { LocalTime.parse(parts[0].trim()) }.getOrNull() ?: return true
        val end = runCatching { LocalTime.parse(parts[1].trim()) }.getOrNull() ?: return true
        if (start == end) return true
        return if (end.isAfter(start)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
    }
}

object SyncStatusText {
    fun homeButton(status: SyncStatus): String {
        return when (status) {
            SyncStatus.SYNCING -> "同步中"
            SyncStatus.FAILED -> "同步失败"
            SyncStatus.OFFLINE -> "未连接网络"
            SyncStatus.SUCCESS -> "同步"
            SyncStatus.IDLE -> "同步"
        }
    }
}
