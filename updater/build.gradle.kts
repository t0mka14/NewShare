import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.graalvm.native)
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

/**
 * GraalVM Community, downloaded on demand by the foojay toolchain resolver in
 * settings.gradle.kts. Nothing is installed system-wide and GRAALVM_HOME is not consulted, so a
 * fresh clone can build the native binary with no manual setup.
 *
 * Only `nativeCompile` uses this launcher — compilation still runs on the Gradle JVM, so :shared
 * and :app are entirely unaffected.
 */
val graalLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.graalvm.jdk.get().toInt()))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

/**
 * Repairs the GraalVM launchers that Gradle's toolchain provisioning breaks.
 *
 * In the GraalVM tarball `bin/native-image` (and its two siblings) are symlinks into
 * `lib/svm/bin/`. Gradle's JDK auto-provisioning does not preserve symlinks, so they extract as
 * **zero-byte files** — `nativeCompile` then fails with a bare "A problem occurred starting
 * process", naming a binary that very much appears to exist. The real 25 MB launcher is present
 * the whole time, one directory away.
 *
 * Idempotent, and a no-op on a correctly-installed GraalVM (a manually installed one, or if
 * Gradle ever learns to keep symlinks).
 */
val repairGraalvmLaunchers = tasks.register("repairGraalvmLaunchers") {
    group = "native"
    description = "Restores bin/native-image symlinks that Gradle's JDK provisioning flattens."
    val home = graalLauncher.map { it.metadata.installationPath.asFile }
    // The toolchain is an external, mutable directory: no declared outputs, so never up-to-date.
    outputs.upToDateWhen { false }
    doLast {
        val jdkHome = home.get().toPath()
        listOf("native-image", "native-image-configure", "native-image-utils").forEach { name ->
            val launcher = jdkHome.resolve("bin").resolve(name)
            val real = jdkHome.resolve("lib/svm/bin").resolve(name)
            val broken = Files.exists(launcher) &&
                !Files.isSymbolicLink(launcher) &&
                Files.size(launcher) == 0L
            if (broken && Files.isRegularFile(real) && Files.size(real) > 0L) {
                Files.delete(launcher)
                Files.createSymbolicLink(launcher, launcher.parent.relativize(real))
                logger.lifecycle("repaired empty GraalVM launcher: $launcher -> $real")
            }
        }
    }
}

tasks.named("nativeCompile") { dependsOn(repairGraalvmLaunchers) }

graalvmNative {
    // The reachability-metadata repository exists for third-party libraries that need reflection
    // config (Netty, Hibernate, ...). :updater has exactly one non-JDK dependency,
    // kotlinx-serialization-json, and every call site uses an explicit `X.serializer()` — there is
    // nothing to look up, so the download is skipped.
    metadataRepository { enabled.set(false) }

    binaries.named("main") {
        imageName.set("updater")
        // :updater does not apply the `application` plugin, so this cannot be inferred.
        mainClass.set("org.example.updater.MainKt")
        javaLauncher.set(graalLauncher)
        buildArgs.addAll(
            // Emit a real native binary or fail the build — never a fallback image that silently
            // still needs a JVM, which would defeat the whole point of shipping this standalone.
            "--no-fallback",
            "-O2",
            // Ctrl-C / SIGTERM terminate the process the way a normal executable does.
            "--install-exit-handlers",
            "-H:+ReportExceptionStackTraces",
        )
    }
}

/** The native binary `:packaging` places at `<install_dir>/updater` (§9). */
configurations.consumable("installUpdaterBinary")

artifacts {
    // nativeCompile writes <buildDir>/native/nativeCompile/<imageName>; imageName is pinned to
    // "updater" above, so this path is deterministic.
    add("installUpdaterBinary", layout.buildDirectory.file("native/nativeCompile/updater")) {
        builtBy(tasks.named("nativeCompile"))
    }
}
