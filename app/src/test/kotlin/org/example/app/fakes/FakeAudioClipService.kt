package org.example.app.fakes

import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.CaptureFormat
import org.example.app.infrastructure.audio.WavHeader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fast [AudioClipService] for `ProcessSessionUseCase` tests (§10.1) — records every
 * cut/convert/concatenate call for assertions, and writes a minimal (header-only, zero
 * audio data) but structurally real WAV file to [output] instead of doing any actual
 * cutting/resampling/concatenation math. Sample-accurate cutting/resampling and atomic-write
 * behavior itself are covered by integration tests against
 * [org.example.app.infrastructure.audio.JvmAudioClipService].
 *
 * The stub file matters when this fake is wired into a real [AppContainer][org.example.app.AppContainer]
 * for a §10.3 UI scenario: `ProcessSessionUseCase`'s archive step always runs the real
 * `ZipSessionArchiveService`/`Sha256FileHashService` (neither is injectable, §5.2), which read
 * every clip/master path named in the archive manifest off disk — a fake that recorded calls
 * without writing anything would make every VOCAL-bearing scenario fail archive building with
 * `NoSuchFileException`, a self-inflicted failure that has nothing to do with the behavior
 * under test.
 *
 * Range validation mirrors the real implementation (reject, never
 * silently truncate) so use-case tests see the same failure shape.
 */
class FakeAudioClipService : AudioClipService {
    val cutClipCalls = mutableListOf<CutClipCall>()
    val convertCalls = mutableListOf<ConvertCall>()
    val concatenateCalls = mutableListOf<ConcatenateCall>()

    /** If set, the next call (of any kind) throws this instead of being recorded — tests error paths. */
    var failNextWith: RuntimeException? = null

    override fun cutClip(sourceWav: Path, startSample: Long, stopSample: Long, targetFormat: CaptureFormat, output: Path) {
        maybeFail()
        require(startSample >= 0) { "startSample must be >= 0, was $startSample" }
        require(startSample < stopSample) { "startSample ($startSample) must be < stopSample ($stopSample)" }
        cutClipCalls += CutClipCall(sourceWav, startSample, stopSample, targetFormat, output)
        writeStub(output, targetFormat)
    }

    override fun convert(sourceWav: Path, targetFormat: CaptureFormat, output: Path) {
        maybeFail()
        convertCalls += ConvertCall(sourceWav, targetFormat, output)
        writeStub(output, targetFormat)
    }

    override fun concatenate(sources: List<Path>, targetFormat: CaptureFormat, output: Path) {
        maybeFail()
        require(sources.isNotEmpty()) { "concatenate requires at least one source" }
        concatenateCalls += ConcatenateCall(sources, targetFormat, output)
        writeStub(output, targetFormat)
    }

    private fun maybeFail() {
        failNextWith?.let {
            failNextWith = null
            throw it
        }
    }

    /** Writes a valid, empty-data 44-byte WAV header to [output] — enough for a real
     * hasher/zip step to read a file that actually exists, without pretending to be
     * sample-accurate audio content. */
    private fun writeStub(output: Path, format: CaptureFormat) {
        output.parent?.let { Files.createDirectories(it) }
        Files.write(output, WavHeader.build(format, dataSize = 0))
    }

    data class CutClipCall(
        val sourceWav: Path,
        val startSample: Long,
        val stopSample: Long,
        val targetFormat: CaptureFormat,
        val output: Path,
    )

    data class ConvertCall(val sourceWav: Path, val targetFormat: CaptureFormat, val output: Path)

    data class ConcatenateCall(val sources: List<Path>, val targetFormat: CaptureFormat, val output: Path)
}
