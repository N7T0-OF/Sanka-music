import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.multiplatform) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.sentry.gradle) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.build.config) apply false
    alias(libs.plugins.osdetector) apply false
    alias(libs.plugins.conveyor) apply false
    alias(libs.plugins.compose.hotReload) apply false
}

tasks.register<Delete>("Clean") {
    delete(rootProject.layout.buildDirectory)
}

/**
 * Reads the latest git tag and updates version-name / version-code in gradle/libs.versions.toml.
 *
 * Run: ./gradlew autoVersion
 *
 * Scheme:
 *   versionName = stripped tag (e.g., "v1.2.3" → "1.2.3")
 *   versionCode = (major*10000 + minor*100 + patch) * 10
 */
tasks.register("autoVersion") {
    group = "versioning"
    description = "Auto-bumps versionCode and versionName from the latest git tag"
    doLast {
        val toml = rootProject.file("gradle/libs.versions.toml")
        val tag =
            try {
                providers.exec {
                    commandLine("git", "describe", "--tags", "--abbrev=0")
                }.standardOutput.asText.get().trim()
            } catch (e: Exception) {
                ""
            }
        val regex = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")
        val match = regex.find(tag)
        val (name, code) =
            if (match != null) {
                val (major, minor, patch) = match.destructured
                val versionName = "$major.$minor.$patch"
                val versionCode = ((major.toInt() * 10000) + (minor.toInt() * 100) + patch.toInt()) * 10
                versionName to versionCode.toString()
            } else {
                val text = toml.readText()
                val current =
                    Regex("""version-name\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1)
                        ?: "1.0.0"
                current to
                    (Regex("""version-code\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: "1")
            }
        val content = toml.readText()
        val updated =
            content
                .replace(Regex("""version-name\s*=\s*"[^"]*""""), "version-name = \"$name\"")
                .replace(Regex("""version-code\s*=\s*"[^"]*""""), "version-code = \"$code\"")
        toml.writeText(updated)
        println("autoVersion: version-name = \"$name\", version-code = \"$code\"")
    }
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.addAll(
                        listOf(
                            "-P",
                            "plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${layout.buildDirectory.asFile.get().absolutePath}/compose_metrics",
                        ),
                    )
                }
            }
        }
    }

    // PipePipe and Brave both depend on com.github.TeamNewPipe:nanojson with different commit
    // hashes. Gradle's default resolver picks PipePipe's older 1d9e1aea... commit which lacks
    // JsonArray.streamAsJsonObjects(), causing NoSuchMethodError when Brave's fallback runs at
    // runtime. Force the latest upstream commit (newer than both libs ship) across every module
    // so the merged APK/JAR carries a nanojson with the API both extractors expect.
    configurations.all {
        resolutionStrategy {
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
        }
    }
}