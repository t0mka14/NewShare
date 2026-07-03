package org.example.app.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the remote configuration JSON (§6, Task_Configuration_JSON_Spec.md) into
 * [RemoteConfig].
 *
 * kotlinx.serialization's built-in sealed-class polymorphism matches the `type`
 * discriminator by exact [kotlinx.serialization.SerialName], so the lenient `QUESTIONAIRE`
 * alias (§4, §6.2) cannot be expressed as an annotation on [QuestionnaireTask] without also
 * accepting it as a *re-encoded* value (round-tripping a snapshot would then rewrite
 * `QUESTIONAIRE` back out as `QUESTIONAIRE`, which is wrong once decoded — we always want to
 * normalize forward). Instead this decoder pre-processes the raw JSON tree, rewriting any
 * task's `"type": "QUESTIONAIRE"` to `"type": "QUESTIONNAIRE"` before handing it to the
 * generated deserializer, and returns a warning for every occurrence so the caller can log it
 * (§11: no participant data in logs — task-type strings are not participant data, but the
 * decision of where/how to log is the caller's).
 */
object ConfigDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    data class DecodeResult(val config: RemoteConfig, val warnings: List<String>)

    /** @throws kotlinx.serialization.SerializationException on malformed/unrecognized JSON. */
    fun decode(rawJson: String): DecodeResult {
        val root = json.parseToJsonElement(rawJson).let { it as JsonObject }
        val warnings = mutableListOf<String>()
        val normalizedRoot = normalizeTaskTypeAliases(root, warnings)
        val config = json.decodeFromJsonElement<RemoteConfig>(normalizedRoot)
        return DecodeResult(config, warnings)
    }

    private fun normalizeTaskTypeAliases(root: JsonObject, warnings: MutableList<String>): JsonObject {
        val protocols = root["protocols"] as? JsonArray ?: return root
        val newProtocols = JsonArray(
            protocols.map { protocolElement ->
                val protocol = protocolElement as? JsonObject ?: return@map protocolElement
                val tasks = protocol["tasks"] as? JsonArray ?: return@map protocolElement
                val newTasks = JsonArray(
                    tasks.map { taskElement ->
                        val task = taskElement as? JsonObject ?: return@map taskElement
                        val typeValue = task["type"]?.jsonPrimitive?.content
                        if (typeValue == LEGACY_QUESTIONNAIRE_TYPE) {
                            warnings += "Config uses legacy task type '$LEGACY_QUESTIONNAIRE_TYPE'; normalized to 'QUESTIONNAIRE'."
                            JsonObject(task.toMutableMap().apply { put("type", JsonPrimitive("QUESTIONNAIRE")) })
                        } else {
                            task
                        }
                    },
                )
                JsonObject(protocol.toMutableMap().apply { put("tasks", newTasks) })
            },
        )
        return JsonObject(root.toMutableMap().apply { put("protocols", newProtocols) })
    }

    private const val LEGACY_QUESTIONNAIRE_TYPE = "QUESTIONAIRE"
}
