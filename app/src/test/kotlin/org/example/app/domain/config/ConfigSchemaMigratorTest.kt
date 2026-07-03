package org.example.app.domain.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The production migration registry ([ConfigSchemaMigrator.DEFAULT]) is currently empty
 * (schemaVersion 1 is the floor, §6.1 pt 6) — these tests exercise the chaining/failure
 * mechanism itself with synthetic migrations, so it is proven correct ahead of the first real
 * migration being registered.
 */
class ConfigSchemaMigratorTest {

    private fun configOf(schemaVersion: Int, vararg extra: Pair<String, String>): JsonObject =
        JsonObject(
            buildMap {
                put("schemaVersion", JsonPrimitive(schemaVersion))
                extra.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            },
        )

    private fun JsonObject.schemaVersion(): Int = this["schemaVersion"]!!.jsonPrimitive.int

    @Test
    fun `already in range returns unchanged`() {
        val migrator = ConfigSchemaMigrator(emptyMap())
        val root = configOf(1)

        val result = migrator.migrate(root, 1..1)

        assertEquals(root, result)
    }

    @Test
    fun `applies a single registered step to bump into range`() {
        val migrator = ConfigSchemaMigrator(
            mapOf(
                0 to { obj: JsonObject ->
                    JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(1)) })
                },
            ),
        )
        val root = configOf(0)

        val result = migrator.migrate(root, 1..1)

        assertEquals(1, result?.schemaVersion())
    }

    @Test
    fun `chains multiple steps in order`() {
        val migrator = ConfigSchemaMigrator(
            mapOf(
                0 to { obj: JsonObject ->
                    JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(1)) })
                },
                1 to { obj: JsonObject ->
                    JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(2)) })
                },
            ),
        )
        val root = configOf(0)

        val result = migrator.migrate(root, 2..2)

        assertEquals(2, result?.schemaVersion())
    }

    @Test
    fun `missing migration step returns null`() {
        val migrator = ConfigSchemaMigrator(emptyMap())
        val root = configOf(0)

        assertNull(migrator.migrate(root, 1..1))
    }

    @Test
    fun `a step that does not advance the version is treated as unmigratable`() {
        val migrator = ConfigSchemaMigrator(
            mapOf(0 to { obj: JsonObject -> obj }), // buggy: doesn't bump schemaVersion
        )
        val root = configOf(0)

        assertNull(migrator.migrate(root, 1..1))
    }

    @Test
    fun `a step that regresses the version returns null instead of looping forever`() {
        val migrator = ConfigSchemaMigrator(
            mapOf(
                0 to { obj: JsonObject ->
                    JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(-1)) })
                },
            ),
        )
        val root = configOf(0)

        assertNull(migrator.migrate(root, 1..1))
    }

    @Test
    fun `missing schemaVersion field returns null`() {
        val migrator = ConfigSchemaMigrator(emptyMap())
        val root = JsonObject(mapOf("someField" to JsonPrimitive("x")))

        assertNull(migrator.migrate(root, 1..1))
    }

    @Test
    fun `DEFAULT registry has no migrations registered yet`() {
        val root = configOf(1)

        // schemaVersion 1 is already the floor, so this must be a no-op, not a failure.
        assertEquals(root, ConfigSchemaMigrator.DEFAULT.migrate(root, 1..1))
    }
}
