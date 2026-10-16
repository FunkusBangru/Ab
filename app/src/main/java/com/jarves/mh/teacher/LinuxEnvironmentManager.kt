package com.jarves.mh.teacher

import android.os.StatFs
import java.io.File

object LinuxEnvironmentManager {

    val AVAILABLE_DISTRIBUTIONS = listOf(
        LinuxDistribution(
            id = "alpine-lite",
            name = "Alpine Linux (Lite)",
            description = "Ultra-lightweight rootfs (~45 MB download). Starts in milliseconds with minimal RAM and battery usage.",
            downloadSizeMb = 45,
            installedSizeMb = 120,
            isLite = true,
            packageManager = "apk",
            rootfsUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.0-aarch64.tar.gz",
        ),
        LinuxDistribution(
            id = "debian-minimal",
            name = "Debian 12 (Minimal)",
            description = "Rock-solid, general-purpose minimal Debian environment with full apt compatibility.",
            downloadSizeMb = 75,
            installedSizeMb = 260,
            isLite = false,
            packageManager = "apt",
            rootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/debian-rootfs-arm64.tar.xz",
        ),
        LinuxDistribution(
            id = "kali-minimal",
            name = "Kali Linux (Minimal)",
            description = "Cybersecurity, networking, and penetration testing environment for security education.",
            downloadSizeMb = 420,
            installedSizeMb = 1400,
            isLite = false,
            packageManager = "apt",
            rootfsUrl = "https://github.com/termux/proot-distro/releases/download/v4.18.0/kali-rootfs-arm64.tar.xz",
        ),
        LinuxDistribution(
            id = "ubuntu-focal",
            name = "Ubuntu 20.04 (PocketDev Core)",
            description = "The standard Mobile Harness core runtime with pre-bundled build tools, Git, and Node.js.",
            downloadSizeMb = 89,
            installedSizeMb = 310,
            isLite = false,
            packageManager = "apt",
            rootfsUrl = "https://github.com/techjarves/Mobile-Harness/releases/download/runtime-2026.09.4/pocketdev-core-arm64-2026.09.5.tar.zst",
        ),
    )

    fun getDistribution(id: String): LinuxDistribution =
        AVAILABLE_DISTRIBUTIONS.firstOrNull { it.id == id } ?: AVAILABLE_DISTRIBUTIONS.last()

    fun hasAdequateSpace(targetDir: File, distro: LinuxDistribution): Boolean {
        return runCatching {
            val stat = StatFs(targetDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val requiredBytes = (distro.downloadSizeMb + distro.installedSizeMb + 100L) * 1024L * 1024L
            availableBytes >= requiredBytes
        }.getOrDefault(true)
    }
}
