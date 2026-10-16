package com.jarves.mh.teacher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class AppPluginManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test connect app creates and persists plugin with smart defaults`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pluginManager = AppPluginManager(memoryStore)

        val plugin = pluginManager.connectApp(
            context = context,
            packageName = "com.termux",
        )

        assertEquals("com.termux", plugin.packageName)
        assertEquals("Terminal & Linux Environment", plugin.teachingRole)
        assertEquals("Computer Science", plugin.targetSubject)
        assertTrue(plugin.capabilities.contains(AppPluginCapability.EXECUTE_CODE))

        val loaded = pluginManager.listConnectedPlugins()
        assertEquals(1, loaded.size)
        assertEquals("com.termux", loaded[0].packageName)
    }

    @Test
    fun `test toggle plugin and disconnect plugin`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pluginManager = AppPluginManager(memoryStore)

        val plugin = pluginManager.connectApp(
            context = context,
            packageName = "com.faizmalkani.desmos",
        )

        assertTrue(plugin.isEnabled)

        val toggled = pluginManager.togglePlugin(plugin.id, false)
        assertTrue(toggled)

        val updated = pluginManager.listConnectedPlugins().first { it.id == plugin.id }
        assertFalse(updated.isEnabled)

        val disconnected = pluginManager.disconnectApp(plugin.id)
        assertTrue(disconnected)
        assertTrue(pluginManager.listConnectedPlugins().isEmpty())
    }

    @Test
    fun `test suggest plugins for concept matching subject`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pluginManager = AppPluginManager(memoryStore)

        pluginManager.connectApp(context, "com.iiec.pydroid3") // CS
        pluginManager.connectApp(context, "org.geogebra.android") // Math

        val csConcept = Concept(id = "c1", subject = "Computer Science", name = "Binary Search Trees")
        val mathConcept = Concept(id = "c2", subject = "Mathematics", name = "Trigonometric Graphs")

        val csPlugins = pluginManager.suggestPluginsForConcept(csConcept)
        val mathPlugins = pluginManager.suggestPluginsForConcept(mathConcept)

        assertTrue(csPlugins.any { it.packageName == "com.iiec.pydroid3" })
        assertTrue(mathPlugins.any { it.packageName == "org.geogebra.android" })
    }

    @Test
    fun `test pedagogical prompt incorporates connected app plugins`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pluginManager = AppPluginManager(memoryStore)
        val engine = TeacherPedagogicalEngine(memoryStore)

        pluginManager.connectApp(context, "com.termux")

        val profile = LearnerProfile(name = "DevStudent")
        val prompt = engine.buildPedagogicalSystemPrompt(
            learnerProfile = profile,
            activeConcept = Concept(name = "Bash Scripting", subject = "Computer Science"),
            autonomyMode = AutonomyMode.MODE_A_EVERY_ACTION,
        )

        assertTrue(prompt.contains("Connected Application Plugins"))
        assertTrue(prompt.contains("com.termux"))
        assertTrue(prompt.contains("Terminal & Linux Environment"))
    }

    private fun createMockContext(rootDir: File): Context {
        val mockPm = Proxy.newProxyInstance(
            PackageManager::class.java.classLoader,
            arrayOf(PackageManager::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getPackageInfo" -> {
                    val pkgName = args[0] as String
                    PackageInfo().apply {
                        packageName = pkgName
                        versionName = "1.0.0"
                        applicationInfo = ApplicationInfo().apply {
                            packageName = pkgName
                            flags = 0
                        }
                    }
                }
                "getApplicationLabel" -> {
                    val appInfo = args[0] as ApplicationInfo
                    when (appInfo.packageName) {
                        "com.termux" -> "Termux"
                        "com.iiec.pydroid3" -> "Pydroid 3"
                        "com.faizmalkani.desmos" -> "Desmos"
                        "org.geogebra.android" -> "GeoGebra"
                        else -> appInfo.packageName
                    }
                }
                else -> null
            }
        } as PackageManager

        val mockContext = Proxy.newProxyInstance(
            Context::class.java.classLoader,
            arrayOf(Context::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getFilesDir" -> rootDir
                "getPackageName" -> "com.jarves.mh"
                "getPackageManager" -> mockPm
                else -> null
            }
        } as Context

        return mockContext
    }
}
