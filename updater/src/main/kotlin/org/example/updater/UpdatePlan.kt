package org.example.updater

import org.example.shared.model.ComponentDescriptor
import java.nio.file.Path

/** One component the current run intends to replace. */
data class PlannedComponent(
    val descriptor: ComponentDescriptor,
    /** Absolute, already validated by [ComponentTarget]. */
    val target: Path,
    val backup: Path,
    /** What the ledger recorded before this run, so a failed launch can revert the ledger too.
     * Null when this component was not installed before. */
    val previousChecksum: String?,
) {
    val id: String get() = descriptor.id
    val payloadName: String get() = "${descriptor.id}.zip"
}

/** A release, reduced to only the components that actually differ from what is installed. */
data class UpdatePlan(val release: String, val components: List<PlannedComponent>)
