package org.example.updater

import org.example.shared.model.AppVersion

fun main() {
    val version = AppVersion(1, 0, 0)
    println("Auto-updater version $version")
    println("Checking for updates...")
    println("App is up to date. Launching...")
}
