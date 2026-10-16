package com.jarves.mh.teacher

import android.content.Context
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy

class CognitiveArchitectureLayersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test Layer 1 ModelAdapter proposes plan and follow-up decisions`() {
        val adapter = LocalPedagogicalModelAdapter()

        // Requesting compound interest
        val result = adapter.planResponse("Can you explain compound interest?")
        assertNotNull(result.proposedComponentPlan)
        assertEquals("Explore compound interest", result.proposedComponentPlan?.title)
        assertFalse(result.requiresToolCall)

        // Requesting code execution with available plugin
        val plugin = AppPluginDefinition(
            packageName = "com.termux",
            displayName = "Termux",
            capabilities = setOf(AppPluginCapability.EXECUTE_CODE),
        )
        val toolResult = adapter.planResponse(
            userRequest = "I want to run some Python code",
            connectedPlugins = listOf(plugin),
        )
        assertTrue(toolResult.requiresToolCall)
        assertEquals("launch_plugin", toolResult.toolName)
        assertEquals("com.termux", toolResult.toolArguments["packageName"])
    }

    @Test
    fun `test Layer 2 UiPlanner enforces versioned declarative schema and rejects unrestricted types`() {
        val planner = UiPlanner()
        val rawJson = """
            {
              "version": 1,
              "mode": "interactive_lesson",
              "title": "Geometry Exploration",
              "blocks": [
                {
                  "type": "explanation",
                  "id": "e1",
                  "content": "Triangles have 180 degrees."
                },
                {
                  "type": "unrestricted_arbitrary_widget",
                  "id": "hack",
                  "malicious_payload": "do_something_bad"
                },
                {
                  "type": "slider",
                  "id": "angle_a",
                  "label": "Angle A",
                  "min": 1,
                  "max": 178,
                  "value": 60
                }
              ]
            }
        """.trimIndent()

        val plan = planner.validateAndPlan(rawJson)
        assertEquals(2, plan.blocks.size) // Unapproved block filtered out!
        assertTrue(plan.blocks.any { it is UiBlock.ExplanationBlock })
        assertTrue(plan.blocks.any { it is UiBlock.SliderBlock })
        assertFalse(plan.blocks.any { it.type == "unrestricted_arbitrary_widget" })
    }

    @Test
    fun `test Layer 3 UiRuntime performs local reactive recomputation without LLM`() {
        val runtime = UiRuntime()
        val plan = InteractiveLessonEngine.createCompoundInterestLesson()
        runtime.loadPlan(plan)

        assertEquals(5.0, runtime.getVariable("annual_rate"), 0.001)

        // Move slider to 7.5%
        val simResult = runtime.onSliderMoved("annual_rate", 7.5f)
        assertNotNull(simResult)

        val finalAmount = simResult!!.summary["final_amount"] as Float
        // P = 1000, r = 7.5, t = 10 -> 1000 * (1.075)^10 = 2061.03
        assertEquals(2061.03f, finalAmount, 0.05f)

        // Dynamic formula evaluation
        val formula = runtime.evaluateFormula("principal * (1 + annual_rate / 100)^years")
        assertEquals(2061.03, formula, 0.05)
    }

    @Test
    fun `test Layer 4 LearningEngine assesses answers and updates records independently of UI`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pedagogicalEngine = TeacherPedagogicalEngine(memoryStore)
        val learningEngine = LearningEngine(pedagogicalEngine)

        val plan = InteractiveLessonEngine.createCompoundInterestLesson()

        val decision = learningEngine.assessAnswer(
            plan = plan,
            assessmentId = "compound_check",
            selectedOptionIndex = 1, // Correct
        )

        assertTrue(decision.isCorrect)
        assertTrue(decision.feedback.contains("Correct!"))
        assertTrue(decision.estimatedMastery > 0.0f)
        assertTrue(decision.bayesianPKnown > 0.1f)
        assertNotNull(decision.nextRecommendedConcept)

        // Verify records in local memory were updated
        val concepts = memoryStore.loadConcepts()
        val recorded = concepts.first { it.id == "compound_interest" }
        assertEquals(1, recorded.correctCount)
        assertEquals(1, recorded.attempts)
    }

    @Test
    fun `test CognitiveTeachingPipeline end-to-end integration`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pedagogicalEngine = TeacherPedagogicalEngine(memoryStore)

        val pipeline = CognitiveTeachingPipeline(
            modelAdapter = LocalPedagogicalModelAdapter(),
            uiPlanner = UiPlanner(),
            uiRuntime = UiRuntime(),
            learningEngine = LearningEngine(pedagogicalEngine),
        )

        // Turn 1: User asks about compound interest
        val modelResult = pipeline.handleUserRequest("How does compound interest grow?")
        assertNotNull(modelResult.proposedComponentPlan)
        assertEquals("Explore compound interest", pipeline.uiRuntime.activePlan?.title)

        // Turn 2: User interacts with slider locally in UI runtime
        val sim = pipeline.uiRuntime.onSliderMoved("annual_rate", 10.0f)
        assertNotNull(sim)
        val finalAmount = sim!!.summary["final_amount"] as Float
        assertEquals(2593.74f, finalAmount, 0.05f)

        // Turn 3: User answers assessment; LearningEngine determines mastery
        val decision = pipeline.submitAssessmentAnswer("compound_check", 1)
        assertNotNull(decision)
        assertTrue(decision!!.isCorrect)
        assertTrue(decision.bayesianPKnown > 0.1f)
    }

    private fun createMockContext(rootDir: File): Context {
        val mockContext = Proxy.newProxyInstance(
            Context::class.java.classLoader,
            arrayOf(Context::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getFilesDir" -> rootDir
                "getPackageName" -> "com.jarves.mh"
                else -> null
            }
        } as Context
        return mockContext
    }
}
