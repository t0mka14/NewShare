package org.example.app.domain.config

import kotlinx.serialization.Serializable

/** §4.2.1. */
@Serializable
enum class QuestionType { OPEN, SINGLE_CHOICE, MULTIPLE_CHOICE }

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
)
