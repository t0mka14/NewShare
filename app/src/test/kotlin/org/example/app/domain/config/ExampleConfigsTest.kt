package org.example.app.domain.config

import org.example.app.domain.timeline.TaskInstanceExpander
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * The configs under `docs/configs/` are what integrators copy from, so a broken one is a
 * broken hand-off even though nothing in the app loads them. They had no coverage at all;
 * this decodes each through the production [ConfigDecoder] and checks every key a task
 * references actually exists in every declared language.
 */
class ExampleConfigsTest {

    private val configs: List<File> =
        File("../docs/configs").listFiles { f -> f.extension == "json" }?.sorted().orEmpty()

    @Test
    fun `the example configs are found`() {
        assertTrue(configs.isNotEmpty(), "no configs under docs/configs; looked in ${File("../docs/configs").absolutePath}")
    }

    @TestFactory
    fun `each example config decodes`(): List<DynamicTest> = configs.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val result = ConfigDecoder.decode(file.readText())
            assertTrue(result.config.protocols.isNotEmpty(), "${file.name} declares no protocol")
        }
    }

    /**
     * A missing key renders as the key itself (§7 fallback), so this never crashes at runtime —
     * it just puts `mimic_detail_title` on screen in front of a patient.
     */
    @TestFactory
    fun `every referenced string key resolves in every language`(): List<DynamicTest> = configs.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val config = ConfigDecoder.decode(file.readText()).config
            val missing = mutableListOf<String>()

            for (language in config.languages) {
                val strings = config.strings[language].orEmpty()
                for (protocol in config.protocols) {
                    for (task in protocol.tasks) {
                        val keys = buildList {
                            add(task.titleKey)
                            when (task) {
                                is VocalTask -> addAll(task.instructionKeys)
                                is InfoTask -> addAll(task.instructionKeys)
                                is VideoTask -> addAll(task.instructionKeys)
                                is CalibrationTask -> addAll(task.instructionKeys)
                                is QuestionnaireTask -> task.questions.forEach {
                                    add(it.questionTextKey)
                                    addAll(it.questionOptions.orEmpty())
                                }
                            }
                        }
                        keys.filterNot { it.isEmpty() || strings.containsKey(it) }
                            .forEach { missing += "$language:$it" }
                    }
                }
            }

            assertEquals(emptyList<String>(), missing.distinct(), "${file.name} references undefined string keys")
        }
    }

    /** VIDEO tasks are navigable, so a config containing them must expand into real screens. */
    @Test
    fun `the full example expands its VIDEO tasks into navigable instances`() {
        val file = configs.single { it.name == "full_example_config.json" }
        val tasks = ConfigDecoder.decode(file.readText()).config.protocols.first().tasks
        val videoTasks = tasks.filterIsInstance<VideoTask>()

        assertTrue(videoTasks.size >= 2, "the full example should show both a plain and a PTZ camera task")
        assertTrue(videoTasks.any { it.havePTZ }, "one VIDEO task should demonstrate havePTZ")

        val instances = TaskInstanceExpander.expand(tasks).instances
        assertEquals(
            videoTasks.sumOf { it.nrepetition },
            instances.count { it.task is VideoTask },
            "each VIDEO repetition must occupy its own task instance",
        )
    }
}
