plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.hot.reload)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // The whole UI is Material3 (M2 dropped 2026-07-16) under the single app-wide
    // ShareTheme (§13 decision 36).
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

/**
 * ffmpeg binaries for camera capture (§9 `native/` payload).
 *
 * Deliberately NOT an `implementation` dependency. These are ~25 MB of platform natives per
 * OS; putting them on the compile classpath would bake them into `app.jar`, which the updater
 * replaces wholesale, so every routine app update would re-download them. They ship instead as
 * `<install_dir>/native/ffmpeg/<platform>/`, versioned independently.
 *
 * bytedeco is used here only as a checksummed CDN for prebuilt LGPL shared ffmpeg builds — no
 * bytedeco class is ever loaded, and `javacv`/`javacpp`/`opencv` are not involved. Pulling
 * `javacv-platform` instead, as the original app did, resolves to ~620 MB of jars.
 */
val videoNatives: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}

dependencies {
    videoNatives("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:windows-x86_64")
    videoNatives("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:macosx-x86_64")
    videoNatives("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:macosx-arm64")
    videoNatives("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:linux-x86_64")
}

/** Where `unpackVideoNatives` puts the binaries, and where `FfmpegBinaryLocator` looks in dev. */
val videoNativesDir: Provider<Directory> = layout.buildDirectory.dir("native/ffmpeg")

/**
 * Unpacks just the shared libraries and the `ffmpeg` executable out of each platform jar.
 *
 * Dropped on the way through: `ffprobe` (unused), every `jni*.dll`/`libjni*` (JavaCPP JNI shims,
 * ~2.7 MB, dead weight when nothing loads JavaCPP) and the GraalVM `native-image` metadata.
 */
val unpackVideoNatives by tasks.registering(Sync::class) {
    group = "build"
    description = "Unpacks the ffmpeg capture binaries into build/native/ffmpeg/<platform>/."
    into(videoNativesDir)

    from({
        videoNatives.resolve().map { jar ->
            zipTree(jar).matching {
                include("org/bytedeco/ffmpeg/*/**")
                exclude("**/*jni*")
                exclude("**/*ffprobe*")
                exclude("META-INF/**")
            }
        }
    }) {
        // org/bytedeco/ffmpeg/<platform>/<file>  ->  <platform>/<file>
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(3).toTypedArray()) }
        includeEmptyDirs = false
    }

    doLast {
        videoNativesDir.get().asFile.walkTopDown()
            .filter { it.isFile && (it.name == "ffmpeg" || it.name == "ffmpeg.exe") }
            .forEach { it.setExecutable(true) }
    }
}

/** Dev runs and tests find ffmpeg through this property; packaging ships `native/ffmpeg/`. */
fun JavaExec.useVideoNatives() {
    dependsOn(unpackVideoNatives)
    systemProperty("share.ffmpeg.path", videoNativesDir.get().asFile.absolutePath)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(unpackVideoNatives)
    // Camera tests drive the real capture pipeline from a synthetic ffmpeg source; without a
    // binary they skip rather than fail, so this stays green on a machine with no natives.
    systemProperty("share.ffmpeg.path", videoNativesDir.get().asFile.absolutePath)
    // §10.3: headless Compose UI tests run under Skia software rendering. CI must
    // also run these with no visible display; on Linux CI runners that additionally
    // means launching under Xvfb (or another virtual framebuffer) since AWT/Skiko
    // still requires an X server even with software rendering. macOS/Windows CI
    // agents render headlessly without an extra virtual display.
    jvmArgs(
        "-Dskiko.renderApi=SOFTWARE",
        "-Djava.awt.headless=true",
        // JEP 472 (JDK 24+): skiko loads its native lib via System.load from the
        // classpath; without this flag the JVM warns now and will refuse later.
        "--enable-native-access=ALL-UNNAMED",
    )
}

/**
 * Renders `ui/previews` in a real window without starting the app (no AppContainer, recorder,
 * session or config involved). The IDE's `@Preview` pane covers the static look; this covers
 * animation, clicks and resizing.
 */
tasks.register<JavaExec>("previewCalibration") {
    group = "application"
    description = "Opens the CalibrationContent preview harness."
    mainClass = "org.example.app.ui.previews.PreviewHarnessKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useVideoNatives()
}

tasks.register<JavaExec>("previewEditor") {
    group = "application"
    description = "Opens the EditorContent preview harness."
    mainClass = "org.example.app.ui.previews.EditorPreviewHarnessKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useVideoNatives()
}

tasks.register<JavaExec>("previewCamera") {
    group = "application"
    description = "Opens the VideoTaskBody harness over a real camera. -Pautostart opens it at once."
    mainClass = "org.example.app.ui.previews.CameraLiveHarnessKt"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useVideoNatives()
    // Lets the harness be driven from a terminal, reading its once-a-second stats out of the log
    // instead of clicking Start — which is also how it works when hot reload is not attached.
    if (project.hasProperty("autostart")) systemProperty("harness.autostart", "true")
}

/**
 * The Compose Hot Reload tasks (`hotRun`, `hotDev`) are registered by the plugin and so miss
 * `useVideoNatives()`, which every camera harness needs. Matched by name rather than by the
 * plugin's task type so a plugin upgrade cannot silently stop applying this.
 */
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("hot")) useVideoNatives()
}

compose.desktop {
    application {
        mainClass = "org.example.app.MainKt"
        // JEP 472 (JDK 24+): skiko loads its native lib via System.load from the
        // classpath; without this flag the JVM warns now and will refuse later.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "ClinicalRecordingApp"
            packageVersion = "1.0.0"
        }
    }
}
