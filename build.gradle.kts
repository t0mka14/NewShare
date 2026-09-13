plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.hot.reload) apply false
}

/**
 * The single version source for every module and every shipped artifact (§9). Validated here
 * rather than at packaging time: `AppVersion.parse` (:shared) accepts only plain `x.y.z`, and a
 * value it rejects would leave `app/version.json` unparseable, which `Updater.decideUpdate`
 * reads as "no local version installed" — making every remote version look newer, forever.
 */
val appVersion: String = providers.gradleProperty("appVersion").get()

require(Regex("""^\d+\.\d+\.\d+$""").matches(appVersion)) {
    "appVersion must be plain x.y.z (AppVersion.parse rejects anything else), got '$appVersion'"
}

subprojects {
    group = "org.example"
    version = appVersion

    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/companion")
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            showStandardStreams = false
        }
    }
}