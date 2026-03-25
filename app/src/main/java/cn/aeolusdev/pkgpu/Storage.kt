package cn.aeolusdev.pkgpu

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class Storage(private val context: Context) {
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
        return runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrElse { AppData() }
    }

    fun save(data: AppData) {
        file.writeText(json.encodeToString(data.copy(updatedAt = System.currentTimeMillis())))
    }

    fun export(data: AppData): String = json.encodeToString(data)
    fun import(raw: String): AppData = json.decodeFromString(raw)
}

class WebDavSyncClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    fun sync(local: AppData, config: CloudConfig): AppData {
        if (!config.enabled || config.url.isBlank() || config.username.isBlank() || config.password.isBlank()) {
            return local
        }

        val targetUrl = config.url.trim().trimEnd('/') + "/" + config.remotePath.trim().trimStart('/')
        val auth = Credentials.basic(config.username, config.password)

        val remoteData = getRemote(targetUrl, auth)
        val merged = when {
            remoteData == null -> local
            remoteData.updatedAt > local.updatedAt -> remoteData
            else -> local
        }
        putRemote(targetUrl, auth, merged)
        return merged
    }

    private fun getRemote(url: String, auth: String): AppData? {
        val request = Request.Builder().url(url).header("Authorization", auth).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                json.decodeFromString<AppData>(body)
            }
        }.getOrNull()
    }

    private fun putRemote(url: String, auth: String, data: AppData) {
        val body = json.encodeToString(data).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("Authorization", auth).put(body).build()
        runCatching {
            client.newCall(request).execute().close()
        }
    }
}
