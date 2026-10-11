package org.example.app.infrastructure.network

/**
 * Web backend (task_protocoller_web_app) that [KtorConfigApi] defaults to: its reverse proxy
 * mounts the backend under `/api`, so the config is `GET /api/site-config/{token}` (config
 * alignment row 1). Without the prefix the proxy answers with the frontend's HTML and a 200.
 */
const val WEB_SERVER_BASE_URL: String = "http://192.168.122.183:10001/api"

/**
 * Base URL [KtorUploadApi] defaults to.
 *
 * Points at the demo mock server in `tools/mock-server` (see its README): plain HTTP, the
 * container's port 80 published on the host as 10001. It accepts `POST /api/upload`, the
 * placeholder contract from §8.9, until the web backend gets an upload endpoint (phase B).
 *
 * HTTPS, which §6.1 pt 7 requires in production, is still open for both URLs; when it lands,
 * these constants (or the values `AppContainer` passes to the two API classes) are the only
 * things that change.
 */
const val DEMO_SERVER_BASE_URL: String = "http://192.168.122.183:10001/api"
