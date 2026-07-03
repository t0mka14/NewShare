package org.example.app.infrastructure.persistence

import org.example.app.domain.DiskSpaceProvider
import java.nio.file.Files
import java.nio.file.Path

/** Production [DiskSpaceProvider] over `java.nio.file.FileStore` (§8.1 preflight). */
class JvmDiskSpaceProvider : DiskSpaceProvider {
    override fun usableBytes(path: Path): Long = Files.getFileStore(path).usableSpace
}
