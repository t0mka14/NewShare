package org.example.app.domain.config

/** A question paired with its indentation depth in the displayed list (0 = root). */
data class DisplayedQuestion(val question: Question, val depth: Int)

/**
 * The effective `showIf` rule of every question that has a valid one, keyed by `questionKey`.
 * The single validity check shared by the visibility pass, the display order and the config
 * validator (§4.2.1; brief §3): the source is an EARLIER question of the same list, of a choice
 * type, and `optionKeys` is a non-empty subset of its options. Anything else is "no rule".
 */
fun effectiveRules(questions: List<Question>): Map<String, QuestionCondition> {
    val rules = LinkedHashMap<String, QuestionCondition>()
    val earlier = HashMap<String, Question>()
    // Rules are keyed by questionKey, so a duplicated key (the question's own or its source's)
    // would make one copy's rule apply to all copies: such a rule is invalid instead.
    val duplicated = questions.groupingBy { it.questionKey }.eachCount().filterValues { it > 1 }.keys
    for (q in questions) {
        val rule = q.showIf
        if (rule != null && q.questionKey !in duplicated && rule.questionKey !in duplicated &&
            isValidRule(rule, earlier[rule.questionKey])
        ) {
            rules[q.questionKey] = rule
        }
        earlier.putIfAbsent(q.questionKey, q)
    }
    return rules
}

/** The questions whose `showIf` is present but invalid (they are treated as unconditional). */
fun questionsWithInvalidRule(questions: List<Question>): List<Question> {
    val valid = effectiveRules(questions)
    return questions.filter { it.showIf != null && valid[it.questionKey] !== it.showIf }
}

private fun isValidRule(rule: QuestionCondition, source: Question?): Boolean {
    if (source == null) return false
    if (source.questionType != QuestionType.SINGLE_CHOICE && source.questionType != QuestionType.MULTIPLE_CHOICE) return false
    val options = source.questionOptions.orEmpty()
    return rule.optionKeys.isNotEmpty() && rule.optionKeys.all { it in options }
}

/**
 * Keys of the questions currently visible, given each question's selected option keys. One pass
 * in config order is enough because a valid source is always earlier.
 */
fun visibleQuestionKeys(questions: List<Question>, selections: Map<String, List<String>>): Set<String> {
    val rules = effectiveRules(questions)
    val visible = LinkedHashSet<String>()
    for (q in questions) {
        val rule = rules[q.questionKey]
        val shown = rule == null ||
            (rule.questionKey in visible && selections[rule.questionKey].orEmpty().any { it in rule.optionKeys })
        if (shown) visible += q.questionKey
    }
    return visible
}

/**
 * Pre-order display list: each question with its depth, conditional questions directly below
 * their source (in config order, after earlier siblings' subtrees). Invalid-rule questions are
 * depth-0 roots at their config position. Every question appears exactly once.
 */
fun displayedQuestions(questions: List<Question>): List<DisplayedQuestion> {
    val rules = effectiveRules(questions)
    val children = HashMap<String, MutableList<Question>>()
    for (q in questions) rules[q.questionKey]?.let { children.getOrPut(it.questionKey) { mutableListOf() } += q }
    val result = ArrayList<DisplayedQuestion>(questions.size)
    fun walk(q: Question, depth: Int) {
        result += DisplayedQuestion(q, depth)
        children[q.questionKey]?.forEach { walk(it, depth + 1) }
    }
    questions.filter { it.questionKey !in rules }.forEach { walk(it, 0) }
    return result
}
