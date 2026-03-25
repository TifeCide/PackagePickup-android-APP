package cn.aeolusdev.pkgpu

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val stationId: String,
    val alias: String,
    val openTime: String = ""
)

@Serializable
data class PackageEntry(
    val packageNo: Int,
    val trackingNo: String = "",
    val inStation: String,
    val pickupCode: String = "",
    val deliveryCompany: String = "未知承运商",
    val memo: String = "",
    val addTime: Long,
    val picked: Boolean = false,
    val pickedTime: Long? = null
)

@Serializable
data class CloudConfig(
    val provider: String = "custom",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "pkgpu-data.json",
    val enabled: Boolean = false
)

@Serializable
data class AppData(
    val stations: List<Station> = emptyList(),
    val packages: List<PackageEntry> = emptyList(),
    val cloudConfig: CloudConfig = CloudConfig(),
    val updatedAt: Long = System.currentTimeMillis()
)
