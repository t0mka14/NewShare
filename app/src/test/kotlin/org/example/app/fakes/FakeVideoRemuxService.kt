package org.example.app.fakes

import org.example.app.domain.video.VideoRemuxException
import org.example.app.domain.video.VideoRemuxService
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fast [VideoRemuxService] for `ProcessSessionUseCase` tests — records every call and writes a
 * stub file to `output`, for the same reason [FakeAudioClipService] does: the real archive step
 * reads every file it zips. The real stream copy is covered by `FfmpegVideoRemuxServiceTest`.
 */
class FakeVideoRemuxService : VideoRemuxService {
    data class Call(val source: Path, val fps: Int, val output: Path)

    val calls = mutableListOf<Call>()

    /** If set, every call throws this detail instead of writing — tests the failure path. */
    var failWith: String? = null

    override fun remuxToMp4(source: Path, fps: Int, output: Path) {
        failWith?.let { throw VideoRemuxException(it) }
        calls += Call(source, fps, output)
        Files.createDirectories(output.toAbsolutePath().parent)
        Files.write(output, byteArrayOf(0, 0, 0, 8, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))
    }
}
