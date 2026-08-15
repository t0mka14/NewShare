package org.example.app.infrastructure.network

/**
 * Base URL both [KtorConfigApi] and [KtorUploadApi] default to.
 *
 * Points at the demo mock server in `tools/mock-server` (see its README): plain HTTP, the
 * container's port 80 published on the host as 10001. It serves `GET /api/config/{installationId}`
 * and accepts `POST /api/upload`, i.e. the placeholder contract from §6.1 / §8.9.
 *
 * The real server contract is still open (§13 open question 1) — including HTTPS, which §6.1 pt 7
 * requires in production. When it lands, this constant (or the values `AppContainer` passes to the
 * two API classes) is the only thing that changes.
 */
const val DEMO_SERVER_BASE_URL: String = "http://192.168.122.183:10001/api"
