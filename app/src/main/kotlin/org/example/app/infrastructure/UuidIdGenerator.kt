package org.example.app.infrastructure

import org.example.app.domain.IdGenerator
import java.util.UUID

/** UUIDv4 hex without dashes: opaque, unique, filesystem-safe (§5.3). */
class UuidIdGenerator : IdGenerator {
    override fun newSessionId(): String = UUID.randomUUID().toString().replace("-", "")
}
