package org.example.app.domain

/**
 * Session IDs: opaque, unique, filesystem-safe (§5.3).
 */
interface IdGenerator {
    fun newSessionId(): String
}
