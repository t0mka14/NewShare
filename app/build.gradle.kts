plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // The whole UI is Material3 (M2 dropped 2026-07-16); the legacy-copied Task/Calibration
    // screens use their own screen-scoped ShareLegacyM3Theme (§13 decision 36).
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.kotlinx.coroutines.core)
    // Provides the Swing Main dispatcher — without it Dispatchers.Main throws at runtime
    // on desktop JVM (tests don't catch this; they inject TestCoroutineDispatchers).
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(compose.desktop.currentOs)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // §10.3: headless Compose UI tests run under Skia software rendering. CI must
    // also run these with no visible display; on Linux CI runners that additionally
    // means launching under Xvfb (or another virtual framebuffer) since AWT/Skiko
    // still requires an X server even with software rendering. macOS/Windows CI
    // agents render headlessly without an extra virtual display.
    jvmArgs(
        "-Dskiko.renderApi=SOFTWARE",
        "-Djava.awt.headless=true",
    )
}

compose.desktop {
    application {
        mainClass = "org.example.app.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "ClinicalRecordingApp"
            packageVersion = "1.0.0"
        }
    }
}
