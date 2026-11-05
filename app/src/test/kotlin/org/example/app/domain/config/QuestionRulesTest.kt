package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestionRulesTest {

    private fun single(key: String, vararg options: String, showIf: QuestionCondition? = null) =
        Question(QuestionType.SINGLE_CHOICE, key, "$key.t", questionOptions = options.toList(), showIf = showIf)

    private fun multi(key: String, vararg options: String, showIf: QuestionCondition? = null) =
        Question(QuestionType.MULTIPLE_CHOICE, key, "$key.t", questionOptions = options.toList(), showIf = showIf)

    private fun open(key: String, showIf: QuestionCondition? = null) =
        Question(QuestionType.OPEN, key, "$key.t", questionRegex = ".*", showIf = showIf)

    private fun rule(source: String, vararg options: String) = QuestionCondition(source, options.toList())

    private fun keys(vararg k: String) = k.toList()

    private fun visible(questions: List<Question>, selections: Map<String, List<String>>) =
        visibleQuestionKeys(questions, selections).toList()

    @Test
    fun `no rules means everything is visible`() {
        val qs = listOf(single("a", "x"), open("b"))
        assertEquals(keys("a", "b"), visible(qs, emptyMap()))
    }

    @Test
    fun `a question is visible when the source selection contains any of the option keys`() {
        val qs = listOf(multi("a", "x", "y", "z"), open("b", rule("a", "x", "y")))
        assertEquals(keys("a"), visible(qs, emptyMap()))
        assertEquals(keys("a"), visible(qs, mapOf("a" to listOf("z"))))
        assertEquals(keys("a", "b"), visible(qs, mapOf("a" to listOf("z", "y"))))
    }

    @Test
    fun `a chain is hidden when its first source is hidden even if the middle selection still matches`() {
        val qs = listOf(
            single("a", "a1", "a2"),
            single("b", "b1", showIf = rule("a", "a1")),
            open("c", rule("b", "b1")),
        )
        assertEquals(keys("a", "b", "c"), visible(qs, mapOf("a" to listOf("a1"), "b" to listOf("b1"))))
        assertEquals(keys("a"), visible(qs, mapOf("a" to listOf("a2"), "b" to listOf("b1"))))
    }

    @Test
    fun `invalid rules count as no rule - missing source, forward, self, OPEN source, empty keys, non-subset`() {
        val cases = mapOf(
            "missing source" to listOf(single("a", "x"), open("b", rule("nope", "x"))),
            "forward reference" to listOf(open("b", rule("a", "x")), single("a", "x")),
            "self reference" to listOf(single("a", "x", showIf = rule("a", "x"))),
            "OPEN source" to listOf(open("a"), open("b", rule("a", "x"))),
            "empty optionKeys" to listOf(single("a", "x"), open("b", QuestionCondition("a", emptyList()))),
            "not a subset" to listOf(single("a", "x"), open("b", rule("a", "x", "y"))),
        )
        for ((name, qs) in cases) {
            assertEquals(qs.map { it.questionKey }, visible(qs, emptyMap()), name)
            assertEquals(1, questionsWithInvalidRule(qs).size, name)
            assertEquals(qs.map { it.questionKey }, displayedQuestions(qs).map { it.question.questionKey }, name)
            assertTrue(displayedQuestions(qs).all { it.depth == 0 }, name)
        }
    }

    @Test
    fun `a valid rule is not reported as invalid`() {
        val qs = listOf(single("a", "x"), open("b", rule("a", "x")))
        assertEquals(emptyList<Question>(), questionsWithInvalidRule(qs))
    }

    @Test
    fun `an invalid middle rule makes that question a root but a rule on it still works`() {
        val qs = listOf(
            single("a", "a1"),
            single("b", "b1", showIf = rule("zzz", "q")), // invalid: always shown
            open("c", rule("b", "b1")),
        )
        assertEquals(keys("a", "b"), visible(qs, emptyMap()))
        assertEquals(keys("a", "b", "c"), visible(qs, mapOf("b" to listOf("b1"))))
    }

    private fun shape(qs: List<Question>) = displayedQuestions(qs).map { it.question.questionKey to it.depth }

    @Test
    fun `display order puts children under their source in config order`() {
        val qs = listOf(
            single("q1", "x"),
            single("q2", "x", "y"),
            open("q3", rule("q1", "x")),
            open("q4", rule("q2", "y")),
            open("q5", rule("q1", "x")),
        )
        assertEquals(
            listOf("q1" to 0, "q3" to 1, "q5" to 1, "q2" to 0, "q4" to 1),
            shape(qs),
        )
    }

    @Test
    fun `the web sample shape - q7 under q3 and q8 under q4`() {
        val qs = listOf(
            single("q1", "o"), single("q2", "o"), single("q3", "o1", "o2", "o3"), multi("q4", "o1", "o2"),
            open("q5"), open("q6"),
            open("q7", rule("q3", "o2", "o3")),
            open("q8", rule("q4", "o1")),
        )
        assertEquals(
            listOf("q1" to 0, "q2" to 0, "q3" to 0, "q7" to 1, "q4" to 0, "q8" to 1, "q5" to 0, "q6" to 0),
            shape(qs),
        )
    }

    @Test
    fun `a chain nests by depth and a child's subtree comes before the source's next child`() {
        val qs = listOf(
            single("a", "a1"),
            single("b", "b1", showIf = rule("a", "a1")),
            open("c", rule("a", "a1")),
            open("d", rule("b", "b1")),
        )
        assertEquals(listOf("a" to 0, "b" to 1, "d" to 2, "c" to 1), shape(qs))
        assertEquals(qs.size, displayedQuestions(qs).size)
    }

    private fun assertAllShownAsRoots(qs: List<Question>, expectedInvalid: Int) {
        assertEquals(qs.map { it.questionKey }, displayedQuestions(qs).map { it.question.questionKey })
        assertTrue(displayedQuestions(qs).all { it.depth == 0 })
        assertEquals(qs.map { it.questionKey }.toSet(), visibleQuestionKeys(qs, emptyMap()))
        assertEquals(expectedInvalid, questionsWithInvalidRule(qs).size)
    }

    @Test
    fun `duplicate keys - mutual rules between two copies of A are all invalid`() {
        val qs = listOf(single("A", "x"), single("B", "y", showIf = rule("A", "x")), single("A", "x", showIf = rule("B", "y")))
        assertAllShownAsRoots(qs, expectedInvalid = 2)
        assertEquals(3, displayedQuestions(qs).size)
    }

    @Test
    fun `duplicate keys - an unconditional copy is not hidden by its twin's rule`() {
        val qs = listOf(
            single("s", "a", "b"),
            open("k", rule("s", "a")),
            open("k"),
            open("z"),
        )
        assertEquals(listOf("s", "k", "k", "z"), displayedQuestions(qs).map { it.question.questionKey })
        assertEquals(listOf("s", "k", "z"), visibleQuestionKeys(qs, mapOf("s" to listOf("b"))).toList())
        assertEquals(1, questionsWithInvalidRule(qs).size)
        assertTrue(displayedQuestions(qs).all { it.depth == 0 })
    }

    @Test
    fun `duplicate keys - an invalid copy is reported and always shown`() {
        val qs = listOf(single("s", "a"), open("k", rule("s", "a")), open("k", rule("nope", "a")))
        assertAllShownAsRoots(qs, expectedInvalid = 2)
    }

    @Test
    fun `duplicate source keys invalidate the rule that points at them`() {
        val qs = listOf(single("s", "a"), single("s", "a"), open("k", rule("s", "a")))
        assertAllShownAsRoots(qs, expectedInvalid = 1)
    }

    @Test
    fun `duplicates without rules keep showing both copies`() {
        val qs = listOf(open("k"), open("k"), single("s", "a"))
        assertAllShownAsRoots(qs, expectedInvalid = 0)
    }
}
