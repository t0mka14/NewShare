package org.example.app.fakes

import org.example.app.domain.upload.FileHashService
import java.nio.file.Path

/** Deterministic [FileHashService] for tests: never touches disk, returns a fixed digest (or a
 * per-path scripted one via [respondWith]) so upload-flow tests don't need a real archive file. */
class FakeFileHashService(private val default: String = "fake-sha256") : FileHashService {
    private val scripted = mutableMapOf<Path, String>()
    val hashedFiles = mutableListOf<Path>()

    fun respondWith(file: Path, sha256: String) {
        scripted[file] = sha256
    }

    override fun sha256(file: Path): String {
        hashedFiles.add(file)
        return scripted[file] ?: default
    }
}
