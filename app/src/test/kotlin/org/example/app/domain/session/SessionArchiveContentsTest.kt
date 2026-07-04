package org.example.app.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionArchiveContentsTest {

    @Test
    fun `primary session JSONs and clips are included`() {
        assertTrue(SessionArchiveContents.isIncluded("participant.json"))
        assertTrue(SessionArchiveContents.isIncluded("examination.json"))
        assertTrue(SessionArchiveContents.isIncluded("task_configuration_snapshot.json"))
        assertTrue(SessionArchiveContents.isIncluded("timeline_original.json"))
        assertTrue(SessionArchiveContents.isIncluded("timeline_edited.json"))
        assertTrue(SessionArchiveContents.isIncluded("clips/install1_HC001_3_PHONATION_1.wav"))
    }

    @Test
    fun `§8_8 exclusion list is excluded`() {
        assertFalse(SessionArchiveContents.isIncluded("archive/HC001_s1.zip"))
        assertFalse(SessionArchiveContents.isIncluded("waveform_cache/master_waveform.cache"))
        assertFalse(SessionArchiveContents.isIncluded("timeline.events.jsonl"))
        assertFalse(SessionArchiveContents.isIncluded("metadata/upload_status.json"))
    }

    @Test
    fun `the raw master directory is excluded — the archive synthesizes its own master entry`() {
        assertFalse(SessionArchiveContents.isIncluded("master/session_master.wav"))
        assertFalse(SessionArchiveContents.isIncluded("master/session_master.part2.wav"))
    }

    @Test
    fun `filter preserves order and drops only excluded paths`() {
        val input = listOf(
            "participant.json",
            "master/session_master.wav",
            "clips/a.wav",
            "timeline.events.jsonl",
            "archive/x.zip",
            "metadata/upload_status.json",
            "waveform_cache/c.cache",
            "examination.json",
        )
        assertEquals(listOf("participant.json", "clips/a.wav", "examination.json"), SessionArchiveContents.filter(input))
    }

    @Test
    fun `windows-style separators are normalized before matching`() {
        assertFalse(SessionArchiveContents.isIncluded("metadata\\upload_status.json"))
        assertTrue(SessionArchiveContents.isIncluded("clips\\a.wav"))
    }
}
