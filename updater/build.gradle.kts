plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    // Deliberately dependency-light (§9): the updater is a candidate for native-image packaging
    // later, so it uses java.net.http.HttpClient instead of Ktor. kotlinx.serialization.json is
    // the one extra runtime dep, needed to decode VersionCheckResponse/AppVersionFile/UpdateMarker.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.updater.MainKt"
    }
}
