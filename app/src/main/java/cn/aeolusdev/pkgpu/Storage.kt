package cn.aeolusdev.pkgpu

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class Storage(private val context: Context) {
    companion object {
        private const val TAG = "PkgPuStorage"
    }
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val file = File(context.filesDir, "pkgpu_data.txt")

    fun load(): AppData {
        if (!file.exists()) {
            val defaultData = AppData(
                stations = listOf(Station("01", "北门驿站", "00:00-00:00"))
            )
            save(defaultData)
            return defaultData
        }
        return runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrElse {
            Log.e(TAG, "本地数据解析失败，已回退默认空数据", it)
            AppData()
        }
    }

    fun save(data: AppData) {
        file.writeText(json.encodeToString(data.copy(updatedAt = System.currentTimeMillis())))
    }

    fun export(data: AppData): String = json.encodeToString(data)
    fun import(raw: String): AppData = json.decodeFromString(raw)
}

class WebDavSyncClient {
    companion object {
        private const val TAG = "PkgPuWebDav"
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    fun sync(local: AppData, config: CloudConfig): SyncResult {
        if (!config.enabled || config.url.isBlank() || config.username.isBlank() || config.password.isBlank()) {
            return SyncResult(local, SyncStatus.IDLE)
        }

        val targetUrl = config.url.trim().trimEnd('/') + "/" + config.remotePath.trim().trimStart('/')
        val auth = Credentials.basic(config.username, config.password)

        val remoteResult = getRemote(targetUrl, auth)
        if (remoteResult.status != null) {
            return SyncResult(local, remoteResult.status)
        }
        val remoteData = remoteResult.data
        val merged = when {
            remoteData == null -> local
            remoteData.updatedAt > local.updatedAt -> remoteData
            else -> local
        }
        val uploadStatus = putRemote(targetUrl, auth, merged)
        return SyncResult(merged, uploadStatus ?: SyncStatus.SUCCESS)
    }

    private fun getRemote(url: String, auth: String): RemoteSyncResult {
        val request = Request.Builder().url(url).header("Authorization", auth).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "拉取云端数据失败: HTTP ${response.code}")
                    return if (response.code == 404) {
                        RemoteSyncResult(data = null, status = null)
                    } else {
                        RemoteSyncResult(data = null, status = SyncStatus.FAILED)
                    }
                }
                val body = response.body?.string() ?: return RemoteSyncResult(data = null, status = SyncStatus.FAILED)
                RemoteSyncResult(data = json.decodeFromString<AppData>(body), status = null)
            }
        }.onFailure {
            Log.e(TAG, "拉取云端数据异常", it)
        }.getOrElse {
            RemoteSyncResult(data = null, status = mapFailureStatus(it))
        }
    }

    private fun putRemote(url: String, auth: String, data: AppData): SyncStatus? {
        val body = json.encodeToString(data).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("Authorization", auth).put(body).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "上传云端数据失败: HTTP ${response.code}")
                    return SyncStatus.FAILED
                }
            }
            null
        }.onFailure {
            Log.e(TAG, "上传云端数据异常", it)
        }.getOrElse { mapFailureStatus(it) }
    }

    private fun mapFailureStatus(throwable: Throwable): SyncStatus {
        return if (throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException
        ) {
            SyncStatus.OFFLINE
        } else {
            SyncStatus.FAILED
        }
    }
}

data class SyncResult(
    val data: AppData,
    val status: SyncStatus
)

data class RemoteSyncResult(
    val data: AppData?,
    val status: SyncStatus?
)

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED,
    OFFLINE
}
