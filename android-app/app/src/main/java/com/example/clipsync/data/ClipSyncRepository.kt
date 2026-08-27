package com.example.clipsync.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class SyncMode {
    CLIPBOARD,
    LIVE_TYPING
}

data class SavedComputer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 8765,
    val lastUsed: Long = System.currentTimeMillis()
)

data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8765,
    val syncDelayMs: Long = 500L,
    val autoClearOnSync: Boolean = false,
    val syncMode: SyncMode = SyncMode.CLIPBOARD,
    val backgroundServiceEnabled: Boolean = false
) {
    val baseUrl: String
        get() = "http://$host:$port"
}

data class ServerStatus(
    val isOnline: Boolean,
    val hostname: String = "",
    val message: String = "",
    val activeHost: String = "",
    val directTypingSupported: Boolean = true
)

class ClipSyncRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("clipsync_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val candidateHosts = listOf("127.0.0.1", "10.42.0.231", "10.51.4.147", "10.0.2.2")

    fun getServerConfig(): ServerConfig {
        val host = prefs.getString("server_host", "127.0.0.1") ?: "127.0.0.1"
        val port = prefs.getInt("server_port", 8765)
        val syncDelayMs = prefs.getLong("sync_delay_ms", 500L)
        val autoClear = prefs.getBoolean("auto_clear", false)
        val modeStr = prefs.getString("sync_mode", SyncMode.CLIPBOARD.name) ?: SyncMode.CLIPBOARD.name
        val syncMode = try { SyncMode.valueOf(modeStr) } catch (_: Exception) { SyncMode.CLIPBOARD }
        val bgService = prefs.getBoolean("bg_service_enabled", false)
        return ServerConfig(host, port, syncDelayMs, autoClear, syncMode, bgService)
    }

    fun saveServerConfig(config: ServerConfig) {
        prefs.edit()
            .putString("server_host", config.host)
            .putInt("server_port", config.port)
            .putLong("sync_delay_ms", config.syncDelayMs)
            .putBoolean("auto_clear", config.autoClearOnSync)
            .putString("sync_mode", config.syncMode.name)
            .putBoolean("bg_service_enabled", config.backgroundServiceEnabled)
            .apply()
    }

    fun getSavedComputers(): List<SavedComputer> {
        val jsonStr = prefs.getString("saved_computers", "[]") ?: "[]"
        val list = mutableListOf<SavedComputer>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedComputer(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "PC"),
                        host = obj.optString("host", "127.0.0.1"),
                        port = obj.optInt("port", 8765),
                        lastUsed = obj.optLong("lastUsed", 0L)
                    )
                )
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            list.add(SavedComputer(name = "PC Local (USB / ADB)", host = "127.0.0.1", port = 8765))
        }
        return list
    }

    fun saveComputer(computer: SavedComputer) {
        val current = getSavedComputers().toMutableList()
        val existingIndex = current.indexOfFirst { it.host == computer.host && it.port == computer.port }
        if (existingIndex >= 0) {
            current[existingIndex] = computer.copy(lastUsed = System.currentTimeMillis())
        } else {
            current.add(0, computer.copy(lastUsed = System.currentTimeMillis()))
        }
        persistComputers(current)
    }

    fun deleteComputer(id: String) {
        val current = getSavedComputers().toMutableList()
        current.removeAll { it.id == id }
        persistComputers(current)
    }

    private fun persistComputers(list: List<SavedComputer>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("host", item.host)
                put("port", item.port)
                put("lastUsed", item.lastUsed)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_computers", arr.toString()).apply()
    }

    fun parseQrCode(qrData: String): ServerConfig? {
        val raw = qrData.trim()
        if (raw.isEmpty()) return null

        // 1. Check if JSON payload
        if (raw.startsWith("{") && raw.endsWith("}")) {
            try {
                val json = JSONObject(raw)
                val host = json.optString("host", "")
                val port = json.optInt("port", 8765)
                if (host.isNotBlank()) {
                    val current = getServerConfig()
                    return current.copy(host = host, port = port)
                }
            } catch (_: Exception) {}
        }

        // 2. Check if HTTP/HTTPS URL
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            try {
                val uri = URI(raw)
                val host = uri.host ?: ""
                val port = if (uri.port > 0) uri.port else 8765
                if (host.isNotBlank()) {
                    val current = getServerConfig()
                    return current.copy(host = host, port = port)
                }
            } catch (_: Exception) {}
        }

        // 3. Check IP or IP:PORT format
        val parts = raw.split(":")
        if (parts.isNotEmpty()) {
            val host = parts[0].trim()
            val port = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 8765 else 8765
            if (host.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")) || host == "localhost") {
                val current = getServerConfig()
                return current.copy(host = host, port = port)
            }
        }

        return null
    }

    suspend fun checkServerStatus(config: ServerConfig): ServerStatus = withContext(Dispatchers.IO) {
        val status = tryPing(config.host, config.port)
        if (status.isOnline) {
            // Auto save to computer list if name found
            if (status.hostname.isNotBlank()) {
                saveComputer(
                    SavedComputer(
                        name = status.hostname,
                        host = config.host,
                        port = config.port
                    )
                )
            }
            return@withContext status
        }

        // Auto-discover among other candidates if user's configured host is down
        for (candidate in candidateHosts) {
            if (candidate != config.host) {
                val candidateStatus = tryPing(candidate, config.port)
                if (candidateStatus.isOnline) {
                    saveServerConfig(config.copy(host = candidate))
                    return@withContext candidateStatus
                }
            }
        }

        status
    }

    private fun tryPing(host: String, port: Int): ServerStatus {
        return try {
            val request = Request.Builder()
                .url("http://$host:$port/api/status")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val hostname = json.optString("hostname", "PC")
                    ServerStatus(
                        isOnline = true,
                        hostname = hostname,
                        message = "Conectado a $hostname ($host)",
                        activeHost = host,
                        directTypingSupported = json.optBoolean("direct_typing", true)
                    )
                } else {
                    ServerStatus(isOnline = false, message = "HTTP ${response.code}", activeHost = host)
                }
            }
        } catch (e: Exception) {
            ServerStatus(isOnline = false, message = e.localizedMessage ?: "Sin conexión", activeHost = host)
        }
    }

    suspend fun sendClip(text: String, config: ServerConfig): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("${config.baseUrl}/api/clip")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(text.length)
                } else {
                    Result.failure(Exception("Error HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDirectType(text: String, config: ServerConfig, enter: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("enter", enter)
                put("timestamp", System.currentTimeMillis())
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("${config.baseUrl}/api/type")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(text.length)
                } else {
                    // Fallback to regular clip
                    sendClip(text, config)
                }
            }
        } catch (e: Exception) {
            // Fallback to regular clip
            sendClip(text, config)
        }
    }
}
