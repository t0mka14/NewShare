package org.example.app.fakes

import org.example.app.domain.DiskSpaceProvider
import java.nio.file.Path

/** Fixed usable-space answer for every path, defaulting to a generous 100 GiB so tests that
 * don't care about preflight pass it by default; override [usableBytesValue] to drive
 * insufficient-space scenarios. */
class FakeDiskSpaceProvider(
    var usableBytesValue: Long = 100L * 1024 * 1024 * 1024,
) : DiskSpaceProvider {
    override fun usableBytes(path: Path): Long = usableBytesValue
}
