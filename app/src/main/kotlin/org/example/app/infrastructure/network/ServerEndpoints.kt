package org.example.app.infrastructure.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shape of the generated `org/example/app/server.json` resource (`:app:generateServerJson`). */
@Serializable
internal data class ServerJson(val serverBaseUrl: String)

private const val DEFAULT_SERVER_BASE_URL = "http://192.168.122.183:10001"

/** The build's `serverBaseUrl` (no `/api`, no trailing slash), from the generated resource. */
internal val SERVER_BASE_URL: String = run {
    val raw = ServerJson::class.java.getResourceAsStream("/org/example/app/server.json")
        ?.use { it.readBytes().decodeToString() }
    raw?.let {
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(ServerJson.serializer(), it) }.getOrNull()
    }?.serverBaseUrl?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: DEFAULT_SERVER_BASE_URL
}

/**
 * Web backend (task_protocoller_web_app) that [KtorConfigApi], [KtorUploadApi],
 * [KtorDeviceLogApi] and [KtorDeviceCheckinApi] default to: `<serverBaseUrl>/api`. Its reverse proxy
 * mounts the backend under `/api`, so the config is `GET /api/site-config/{token}` (config alignment
 * row 1) and the upload `POST /api/upload`. Without the prefix the proxy answers with the
 * frontend's HTML and a 200.
 *
 * `serverBaseUrl` is fixed at build time (`-PserverBaseUrl`, default the test container, see
 * `gradle.properties`) and read once from the `server.json` resource; a missing or malformed
 * resource falls back to `http://192.168.122.183:10001`. Not a `const`, so it cannot be used in
 * annotations.
 */
val WEB_SERVER_BASE_URL: String = "$SERVER_BASE_URL/api"
