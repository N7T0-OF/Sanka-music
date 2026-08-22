#!/usr/bin/env -S kotlin -script

/**
 * Auto-bumps versionCode and versionName in gradle/libs.versions.toml
 * from the latest git tag.
 *
 * Usage: kotlin scripts/autoVersion.gradle.kts
 *
 * Version scheme:
 *   - Reads the latest git tag (e.g., "v1.2.3")
 *   - versionName = tag without the "v" prefix
 *   - versionCode = (major*10000 + minor*100 + patch) * 10  (leaves room for 10 builds per patch)
 *
 * If there is no tag yet, falls back to the existing values in the TOML file.
 */

import java.io.File
import java.util.regex.Pattern

val tomlFile = File("gradle/libs.versions.toml")
if (!tomlFile.exists()) {
    println("ERROR: gradle/libs.versions.toml not found. Run from the project root.")
    kotlin.system.exitProcess(1)
}

// ── 1. Get the latest git tag ──
val tagProcess =
    ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
        .redirectErrorStream(true)
        .start()
val tagOutput = tagProcess.inputStream.bufferedReader().readText().trim()
tagProcess.waitFor()

val versionPattern = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
val matcher =
    if (tagProcess.exitValue() == 0 && tagOutput.isNotEmpty()) {
        versionPattern.matcher(tagOutput)
    } else {
        null
    }

val (newVersionName, newVersionCode) =
    if (matcher != null && matcher.matches()) {
        val major = matcher.group(1).toInt()
        val minor = matcher.group(2).toInt()
        val patch = matcher.group(3).toInt()
        val versionName = "$major.$minor.$patch"
        // e.g. 1.2.3 → (10000 + 200 + 3) * 10 = 102030
        val versionCode = ((major * 10000) + (minor * 100) + patch) * 10
        println("Git tag: $tagOutput → versionName=$versionName  versionCode=$versionCode")
        versionName to versionCode.toString()
    } else {
        // No tag found — read current values from TOML and keep them
        val content = tomlFile.readText()
        val currentVersionName =
            Regex("""version-name\s*=\s*"([^"]+)"""").find(content)?.groupValues?.getOrNull(1) ?: "1.0.0"
        val currentVersionCode =
            Regex("""version-code\s*=\s*"([^"]+)"""").find(content)?.groupValues?.getOrNull(1) ?: "1"
        println("No git tag found. Keeping existing: versionName=$currentVersionName  versionCode=$currentVersionCode")
        currentVersionName to currentVersionCode
    }

// ── 2. Update the TOML file ──
val content = tomlFile.readText()
val updated =
    content
        .replace(
            Regex("""version-name\s*=\s*"[^"]*""""),
            """version-name = "$newVersionName"""",
        ).replace(
            Regex("""version-code\s*=\s*"[^"]*""""),
            """version-code = "$newVersionCode"""",
        )

if (updated == content) {
    println("No changes needed — TOML already matches.")
} else {
    tomlFile.writeText(updated)
    println("Updated gradle/libs.versions.toml:")
    println("  version-name = \"$newVersionName\"")
    println("  version-code = \"$newVersionCode\"")
}