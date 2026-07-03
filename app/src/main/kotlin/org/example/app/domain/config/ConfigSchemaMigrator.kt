package org.example.app.domain.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Versioned migration-function registry for stale *cached* configs (§6.1 pt 6). Migration
 * never applies to a freshly fetched server response — a response outside the supported range
 * is rejected outright (the server is expected to speak a version this release understands);
 * only a config previously written to disk by an older app release is a migration candidate.
 *
 * Each entry is keyed by the schemaVersion a migration function accepts as *input* and must
 * return a [JsonObject] whose `schemaVersion` has advanced by exactly one step. [migrate]
 * chains steps until the version falls inside the target range.
 *
 * The production registry ([DEFAULT]) is currently empty: schemaVersion 1 is the floor this
 * release supports, so there is nothing below it to migrate yet. The chaining/failure
 * mechanism itself is unit-tested with synthetic migrations (`ConfigSchemaMigratorTest`) so it
 * is ready the day a schemaVersion 2 ships and a cache below it needs upgrading — adding a
 * migration is then a one-line addition to [DEFAULT], no changes to this class.
 */
class ConfigSchemaMigrator(
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap(),
) {
    /**
     * Applies registered migration steps to [root] until its `schemaVersion` falls inside
     * [targetRange]. Returns `null` — meaning "cannot migrate, treat as no cache" (§6.1 pt 6)
     * — if: `root` has no readable integer `schemaVersion`, a required step is missing from
     * the registry, or a migration step fails to advance the version (guards against an
     * infinite loop from a misbehaving migration function).
     */
    fun migrate(root: JsonObject, targetRange: IntRange): JsonObject? {
        var current = root
        var version = current.schemaVersionOrNull() ?: return null

        while (version < targetRange.first) {
            val step = migrations[version] ?: return null
            val migrated = step(current)
            val newVersion = migrated.schemaVersionOrNull() ?: return null
            if (newVersion <= version) return null
            current = migrated
            version = newVersion
        }
        return current
    }

    companion object {
        /** No migrations registered yet — schemaVersion 1 is the current floor (§6.1 pt 6). */
        val DEFAULT = ConfigSchemaMigrator()

        private fun JsonObject.schemaVersionOrNull(): Int? = this["schemaVersion"]?.jsonPrimitive?.intOrNull
    }
}
