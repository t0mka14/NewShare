package org.example.app.domain.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** §4.2.1. */
@Serializable
enum class QuestionType { OPEN, SINGLE_CHOICE, MULTIPLE_CHOICE }

/** §4.2.1 `showIf`: [questionKey] names an earlier choice question; [optionKeys] is a subset of its options. */
@Serializable(with = QuestionConditionSerializer::class)
data class QuestionCondition(
    val questionKey: String,
    val optionKeys: List<String>,
) {
    companion object {
        /** What an unreadable `showIf` decodes to; never a valid rule (empty `optionKeys`). */
        val MALFORMED = QuestionCondition("", emptyList())
    }
}

/**
 * One question within a [QuestionnaireTask] (§4.2.1). `questionRegex` validates `OPEN`
 * answers only; `questionOptions` are localization keys, required for choice types and
 * absent for `OPEN`.
 */
@Serializable
data class Question(
    val questionType: QuestionType,
    val questionKey: String,
    val questionTextKey: String,
    val questionRegex: String? = null,
    val questionOptions: List<String>? = null,
    /** §4.2.1: show this question only while the source question's selection contains one of the keys. */
    val showIf: QuestionCondition? = null,
) {
    /**
     * Whether [value] is a valid `OPEN` answer: no regex means free text, otherwise it must match.
     * An untouched answer is `""`, so `.*` makes the question optional and `.+` required (the web
     * emits exactly these for optional/required open questions).
     */
    fun acceptsOpenAnswer(value: String): Boolean =
        questionRegex.isNullOrEmpty() || Regex(questionRegex).matches(value)
}

/**
 * Brief §3: a bad rule must never cost the whole config. Anything that is not exactly
 * `{questionKey: string, optionKeys: [strings]}` decodes to [QuestionCondition.MALFORMED], which
 * the shared validity check (`effectiveRules`) rejects, so the warning and the always-shown
 * behaviour need no separate path. A JSON `null` never reaches here (the field is nullable).
 */
internal object QuestionConditionSerializer : KSerializer<QuestionCondition> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): QuestionCondition {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val obj = element as? JsonObject ?: return QuestionCondition.MALFORMED
        val key = obj["questionKey"].asStringOrNull() ?: return QuestionCondition.MALFORMED
        val keys = (obj["optionKeys"] as? JsonArray)?.map { it.asStringOrNull() ?: return QuestionCondition.MALFORMED }
            ?: return QuestionCondition.MALFORMED
        return QuestionCondition(key, keys)
    }

    override fun serialize(encoder: Encoder, value: QuestionCondition) {
        (encoder as JsonEncoder).encodeJsonElement(
            JsonObject(
                mapOf(
                    "questionKey" to JsonPrimitive(value.questionKey),
                    "optionKeys" to JsonArray(value.optionKeys.map { JsonPrimitive(it) }),
                ),
            ),
        )
    }

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
}
