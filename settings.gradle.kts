plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "ShareAppNewDemo"

include(":app")
include(":updater")
include(":shared")
// Assembles the §9 install layout and the published release artifacts from :app and :updater.
include(":packaging")
