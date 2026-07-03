package org.example.app.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SessionFolderNamingTest {

    @Test
    fun `builds date_patientCode_sessionId`() {
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "HC001", "abc123")
        assertEquals("2026-07-03_HC001_abc123", folderName)
    }

    @Test
    fun `sanitizes unsafe characters in patient code`() {
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "HC 001/../etc", "abc123")
        assertEquals("2026-07-03_HC001etc_abc123", folderName)
    }

    @Test
    fun `extractPatientCode recovers the middle segment given the known sessionId`() {
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "HC001", "abc123")
        assertEquals("HC001", SessionFolderNaming.extractPatientCode(folderName, "abc123"))
    }

    @Test
    fun `extractPatientCode handles a patient code that itself contains underscores`() {
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "HC_001_X", "abc123")
        assertEquals("HC_001_X", SessionFolderNaming.extractPatientCode(folderName, "abc123"))
    }

    @Test
    fun `extractPatientCode handles an empty patient code`() {
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "", "abc123")
        assertEquals("", SessionFolderNaming.extractPatientCode(folderName, "abc123"))
    }
}
