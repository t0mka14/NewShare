package org.example.app.domain

import java.nio.file.Path

/**
 * Free-space query for disk preflight (§8.1: "requires free space for a worst-case session
 * ... or refuses to start with a localized error"). A thin port so `StartSessionUseCase` is
 * testable without touching a real filesystem's free-space accounting.
 */
interface DiskSpaceProvider {
    /** Usable bytes on the filesystem/volume containing [path]. [path] must already exist. */
    fun usableBytes(path: Path): Long
}
