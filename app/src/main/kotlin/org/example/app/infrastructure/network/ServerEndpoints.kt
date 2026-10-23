package org.example.app.infrastructure.network

/**
 * Web backend (task_protocoller_web_app) that [KtorConfigApi], [KtorUploadApi] and
 * [KtorDeviceCheckinApi] default to: its reverse proxy mounts the backend under `/api`, so the
 * config is `GET /api/site-config/{token}` (config alignment row 1) and the upload
 * `POST /api/upload`. Without the prefix the proxy answers with the frontend's HTML and a 200.
 *
 * HTTPS, which §6.1 pt 7 requires in production, is still open; when it lands, this constant
 * (or the values `AppContainer` passes to the API classes) is the only thing that changes.
 */
const val WEB_SERVER_BASE_URL: String = "http://192.168.122.183:10001/api"
