package com.jarves.mh.teacher

import android.content.Context
import java.lang.reflect.Method

enum class ShizukuStatus {
    NOT_INSTALLED,
    SERVICE_STOPPED,
    PERMISSION_NEEDED,
    AUTHORIZED_AND_READY,
}

enum class ShizukuPrivilege(val id: String, val title: String, val benefitDescription: String) {
    DISABLE_PHANTOM_KILLER(
        "phantom_process",
        "Bypass Phantom Process Killer",
        "Prevents Android 12+ from killing background PRoot and compiler processes when CPU spikes.",
    ),
    SILENT_APK_INSTALL(
        "silent_install",
        "Silent Local APK Installation",
        "Installs locally compiled Android debug builds without requiring manual package-installer confirmation.",
    ),
    SYSTEM_LOGCAT(
        "system_logcat",
        "System-Wide Diagnostics",
        "Streams deep logcat entries across app boundaries to diagnose crashes.",
    ),
}

object ShizukuSupport {
    @Volatile
    private var isReflectionInitialized = false
    private var pingMethod: Method? = null
    private var checkPermissionMethod: Method? = null

    private fun initReflection() {
        if (isReflectionInitialized) return
        synchronized(this) {
            if (isReflectionInitialized) return
            runCatching {
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                pingMethod = shizukuClass.getMethod("pingBinder")
                checkPermissionMethod = shizukuClass.getMethod("checkSelfPermission")
            }
            isReflectionInitialized = true
        }
    }

    fun checkStatus(context: Context): ShizukuStatus {
        initReflection()
        val ping = pingMethod ?: return ShizukuStatus.NOT_INSTALLED

        val isRunning = runCatching {
            ping.invoke(null) as? Boolean ?: false
        }.getOrDefault(false)

        if (!isRunning) return ShizukuStatus.SERVICE_STOPPED

        val checkPermission = checkPermissionMethod ?: return ShizukuStatus.SERVICE_STOPPED

        val permissionResult = runCatching {
            checkPermission.invoke(null) as? Int ?: -1
        }.getOrDefault(-1)

        return if (permissionResult == 0) {
            ShizukuStatus.AUTHORIZED_AND_READY
        } else {
            ShizukuStatus.PERMISSION_NEEDED
        }
    }

    fun supportedPrivileges(): List<ShizukuPrivilege> = ShizukuPrivilege.entries

    fun disablePhantomProcessKillerCommand(): String =
        "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
}
