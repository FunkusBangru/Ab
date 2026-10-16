package com.jarves.mh.teacher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import java.util.ArrayList

class AppPluginManager(
    private val memoryStore: TeacherMemoryStore,
) {

    companion object {
        private val KNOWN_EDUCATIONAL_PACKAGES = mapOf(
            "com.termux" to SmartAppPreset(
                suggestedRole = "Terminal & Linux Environment",
                suggestedSubject = "Computer Science",
                suggestedCapabilities = setOf(AppPluginCapability.EXECUTE_CODE, AppPluginCapability.GENERAL_INTENT),
            ),
            "com.iiec.pydroid3" to SmartAppPreset(
                suggestedRole = "Python 3 IDE & Execution",
                suggestedSubject = "Computer Science",
                suggestedCapabilities = setOf(AppPluginCapability.EXECUTE_CODE, AppPluginCapability.TEXT_EDITOR),
            ),
            "com.faizmalkani.desmos" to SmartAppPreset(
                suggestedRole = "Graphing Calculator & Function Plotter",
                suggestedSubject = "Mathematics",
                suggestedCapabilities = setOf(AppPluginCapability.RENDER_GRAPH),
            ),
            "org.geogebra.android" to SmartAppPreset(
                suggestedRole = "Dynamic Geometry & Algebra Visualizer",
                suggestedSubject = "Mathematics",
                suggestedCapabilities = setOf(AppPluginCapability.RENDER_GRAPH, AppPluginCapability.SIMULATION),
            ),
            "com.ichi2.anki" to SmartAppPreset(
                suggestedRole = "Spaced Repetition Flashcards",
                suggestedSubject = "General",
                suggestedCapabilities = setOf(AppPluginCapability.SPACED_REPETITION),
            ),
            "com.foxdebug.acode" to SmartAppPreset(
                suggestedRole = "Code & Syntax Editor",
                suggestedSubject = "Computer Science",
                suggestedCapabilities = setOf(AppPluginCapability.TEXT_EDITOR),
            ),
            "org.wikipedia" to SmartAppPreset(
                suggestedRole = "Conceptual Encyclopedia & References",
                suggestedSubject = "General",
                suggestedCapabilities = setOf(AppPluginCapability.LOOKUP_DOCS),
            ),
            "md.obsidian" to SmartAppPreset(
                suggestedRole = "Markdown Notes & Knowledge Graph",
                suggestedSubject = "General",
                suggestedCapabilities = setOf(AppPluginCapability.TEXT_EDITOR, AppPluginCapability.LOOKUP_DOCS),
            ),
            "edu.colorado.colorado.phet" to SmartAppPreset(
                suggestedRole = "Interactive Science & Physics Sims",
                suggestedSubject = "Physics",
                suggestedCapabilities = setOf(AppPluginCapability.SIMULATION),
            ),
            "com.duolingo" to SmartAppPreset(
                suggestedRole = "Language Learning Drills",
                suggestedSubject = "Language",
                suggestedCapabilities = setOf(AppPluginCapability.SPACED_REPETITION),
            ),
        )

        private data class SmartAppPreset(
            val suggestedRole: String,
            val suggestedSubject: String,
            val suggestedCapabilities: Set<AppPluginCapability>,
        )
    }

    fun listConnectedPlugins(): List<AppPluginDefinition> =
        memoryStore.loadPlugins()

    fun scanInstalledApps(
        context: Context,
        searchQuery: String? = null,
        includeSystemApps: Boolean = false,
    ): List<InstalledAppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName

        val installedPackages = runCatching {
            pm.getInstalledPackages(0)
        }.getOrElse {
            runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { appInfo ->
                    pm.getPackageInfo(appInfo.packageName, 0)
                }
            }.getOrDefault(emptyList())
        }

        val result = ArrayList<InstalledAppInfo>(installedPackages.size)
        val queryLower = searchQuery?.trim()?.lowercase()

        for (pkg in installedPackages) {
            val pkgName = pkg.packageName
            if (pkgName == selfPackage) continue

            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val preset = KNOWN_EDUCATIONAL_PACKAGES[pkgName]

            if (isSystem && !includeSystemApps && preset == null) {
                continue
            }

            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkgName)

            if (!queryLower.isNullOrBlank()) {
                val matchesName = appName.lowercase().contains(queryLower)
                val matchesPkg = pkgName.lowercase().contains(queryLower)
                val matchesSubject = preset?.suggestedSubject?.lowercase()?.contains(queryLower) ?: false
                if (!matchesName && !matchesPkg && !matchesSubject) {
                    continue
                }
            }

            result.add(
                InstalledAppInfo(
                    packageName = pkgName,
                    appName = appName,
                    versionName = pkg.versionName,
                    isSystemApp = isSystem,
                    suggestedRole = preset?.suggestedRole ?: "General Practice App",
                    suggestedSubject = preset?.suggestedSubject ?: "General",
                    suggestedCapabilities = preset?.suggestedCapabilities ?: setOf(AppPluginCapability.GENERAL_INTENT),
                ),
            )
        }

        result.sortWith(
            compareByDescending<InstalledAppInfo> { KNOWN_EDUCATIONAL_PACKAGES.containsKey(it.packageName) }
                .thenBy { it.appName.lowercase() },
        )

        return result
    }

    fun connectApp(
        context: Context,
        packageName: String,
        teachingRole: String? = null,
        targetSubject: String? = null,
        capabilities: Set<AppPluginCapability>? = null,
        launchAction: String = Intent.ACTION_MAIN,
        notes: String = "",
    ): AppPluginDefinition {
        val pm = context.packageManager
        val preset = KNOWN_EDUCATIONAL_PACKAGES[packageName]

        val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val appName = pkgInfo?.applicationInfo?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?: packageName

        val finalRole = teachingRole?.takeIf { it.isNotBlank() }
            ?: preset?.suggestedRole
            ?: "General Practice Tool"

        val finalSubject = targetSubject?.takeIf { it.isNotBlank() }
            ?: preset?.suggestedSubject
            ?: "General"

        val finalCaps = capabilities?.takeIf { it.isNotEmpty() }
            ?: preset?.suggestedCapabilities
            ?: setOf(AppPluginCapability.GENERAL_INTENT)

        val existing = memoryStore.loadPlugins().firstOrNull { it.packageName == packageName }
        val plugin = if (existing != null) {
            existing.copy(
                displayName = appName,
                teachingRole = finalRole,
                targetSubject = finalSubject,
                capabilities = finalCaps,
                launchAction = launchAction,
                notes = notes,
                installedVersion = pkgInfo?.versionName,
                isInstalled = pkgInfo != null,
            )
        } else {
            AppPluginDefinition(
                packageName = packageName,
                displayName = appName,
                teachingRole = finalRole,
                targetSubject = finalSubject,
                capabilities = finalCaps,
                launchAction = launchAction,
                notes = notes,
                installedVersion = pkgInfo?.versionName,
                isInstalled = pkgInfo != null,
            )
        }

        memoryStore.savePlugin(plugin)
        return plugin
    }

    fun disconnectApp(id: String): Boolean =
        memoryStore.deletePlugin(id)

    fun togglePlugin(id: String, enabled: Boolean): Boolean {
        val plugin = memoryStore.loadPlugins().firstOrNull { it.id == id } ?: return false
        memoryStore.savePlugin(plugin.copy(isEnabled = enabled))
        return true
    }

    fun launchPlugin(
        context: Context,
        plugin: AppPluginDefinition,
        payloadText: String? = null,
    ): AppLaunchResult {
        return runCatching {
            val pm = context.packageManager

            if (!plugin.deepLinkTemplate.isNullOrBlank()) {
                val targetUri = if (!payloadText.isNullOrBlank()) {
                    val encoded = Uri.encode(payloadText)
                    Uri.parse(plugin.deepLinkTemplate.replace("{payload}", encoded))
                } else {
                    Uri.parse(plugin.deepLinkTemplate)
                }

                val viewIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                    setPackage(plugin.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (viewIntent.resolveActivity(pm) != null) {
                    context.startActivity(viewIntent)
                    return AppLaunchResult(
                        success = true,
                        message = "Opened ${plugin.displayName} via deep link",
                        packageName = plugin.packageName,
                        intentAction = Intent.ACTION_VIEW,
                    )
                }
            }

            if (!payloadText.isNullOrBlank() && plugin.launchAction == Intent.ACTION_SEND) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payloadText)
                    setPackage(plugin.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (sendIntent.resolveActivity(pm) != null) {
                    context.startActivity(sendIntent)
                    return AppLaunchResult(
                        success = true,
                        message = "Dispatched data to ${plugin.displayName}",
                        packageName = plugin.packageName,
                        intentAction = Intent.ACTION_SEND,
                    )
                }
            }

            val launchIntent = pm.getLaunchIntentForPackage(plugin.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!payloadText.isNullOrBlank()) {
                    launchIntent.putExtra(Intent.EXTRA_TEXT, payloadText)
                    launchIntent.putExtra("teaching_payload", payloadText)
                }
                context.startActivity(launchIntent)
                return AppLaunchResult(
                    success = true,
                    message = "Launched ${plugin.displayName}",
                    packageName = plugin.packageName,
                    intentAction = launchIntent.action,
                )
            }

            AppLaunchResult(
                success = false,
                message = "Could not find a valid launch activity for ${plugin.packageName}. Ensure the app is installed.",
                packageName = plugin.packageName,
            )
        }.getOrElse { err ->
            AppLaunchResult(
                success = false,
                message = "Launch failed: ${err.message}",
                packageName = plugin.packageName,
            )
        }
    }

    fun sendTeachingPayload(
        context: Context,
        plugin: AppPluginDefinition,
        payload: String,
        subject: String? = null,
    ): AppLaunchResult {
        return launchPlugin(context, plugin, payloadText = payload)
    }

    fun suggestPluginsForConcept(concept: Concept): List<AppPluginDefinition> {
        val plugins = memoryStore.loadPlugins().filter { it.isEnabled && it.isInstalled }
        if (plugins.isEmpty()) return emptyList()

        return plugins.filter { p ->
            p.targetSubject.equals(concept.subject, ignoreCase = true) ||
                p.targetSubject.equals("General", ignoreCase = true) ||
                concept.name.contains(p.displayName, ignoreCase = true)
        }
    }
}
