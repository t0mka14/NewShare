package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    override fun toString(): String = "$major.$minor.$patch"
}

@Serializable
data class VersionCheckResponse(
    val version: String,
    val downloadUrl: String,
    val checksum: String
)
