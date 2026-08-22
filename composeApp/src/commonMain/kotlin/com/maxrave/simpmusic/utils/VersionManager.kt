package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.BuildKonfig

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }

    /**
     * True when [latestTag] (e.g. "v1.1.10" or "1.1.10") is strictly newer than [currentVersion]
     * (e.g. "1.1.9"). Uses a real semantic comparison: numeric segments are compared as numbers,
     * so 1.1.10 > 1.1.9. Falls back to a plain string comparison when a segment is not numeric.
     */
    fun isVersionNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = normalizeVersion(latestTag)
        val current = normalizeVersion(currentVersion)
        if (latest.isEmpty() || current.isEmpty()) return false
        val latestParts = latest.split('.')
        val currentParts = current.split('.')
        val maxParts = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxParts) {
            val a = latestParts.getOrNull(i) ?: "0"
            val b = currentParts.getOrNull(i) ?: "0"
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            val cmp =
                if (aNum != null && bNum != null) {
                    aNum.compareTo(bNum)
                } else {
                    a.compareTo(b)
                }
            if (cmp != 0) return cmp > 0
        }
        return false
    }

    private fun normalizeVersion(version: String): String =
        version.trim().removePrefix("v").removePrefix("V").trim()
}