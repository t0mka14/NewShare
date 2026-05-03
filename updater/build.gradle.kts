plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.ktor.client.cio)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.updater.MainKt"
    }
}
