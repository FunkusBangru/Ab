package com.jarves.mh.teacher

import android.content.Context
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import kotlin.math.abs
import kotlin.math.pow

class InteractiveLessonTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test structural validity parses valid component plan schema`() {
        val adapter = StreamingUiAdapter()
        val json = """
            {
              "version": 1,
              "mode": "interactive_lesson",
              "title": "Explore compound interest",
              "conceptId": "compound_interest",
              "blocks": [
                {
                  "type": "explanation",
                  "id": "concept",
                  "content": "Interest can itself earn interest."
                },
                {
                  "type": "slider",
                  "id": "annual_rate",
                  "label": "Annual interest rate",
                  "min": 0,
                  "max": 15,
                  "step": 0.5,
                  "value": 5,
                  "unit": "%"
                },
                {
                  "type": "chart",
                  "id": "growth",
                  "data_source": "compound_interest_simulation"
                }
              ]
            }
        """.trimIndent()

        val plan = adapter.parsePlan(json)
        assertEquals(1, plan.version)
        assertEquals("interactive_lesson", plan.mode)
        assertEquals("Explore compound interest", plan.title)
        assertEquals("compound_interest", plan.conceptId)
        assertEquals(3, plan.blocks.size)

        val b0 = plan.blocks[0] as UiBlock.ExplanationBlock
        assertEquals("concept", b0.id)
        assertEquals("Interest can itself earn interest.", b0.content)

        val b1 = plan.blocks[1] as UiBlock.SliderBlock
        assertEquals("annual_rate", b1.id)
        assertEquals("Annual interest rate", b1.label)
        assertEquals(0f, b1.min)
        assertEquals(15f, b1.max)
        assertEquals(0.5f, b1.step)
        assertEquals(5f, b1.value)
        assertEquals("%", b1.unit)

        val b2 = plan.blocks[2] as UiBlock.ChartBlock
        assertEquals("growth", b2.id)
        assertEquals("compound_interest_simulation", b2.dataSource)
    }

    @Test
    fun `test streaming implementation delivers validated renderable units incrementally`() {
        val adapter = StreamingUiAdapter()

        // Chunk 1: Incomplete fragment
        val chunk1 = """{"version": 1, "blocks": [{"type": "explanation", "id": "exp1", "content": "Start of """
        val units1 = adapter.feedChunk(chunk1)
        assertTrue("Incomplete JSON fragment must not emit an invalid unit", units1.isEmpty())

        // Chunk 2: Completes the first block
        val chunk2 = """explanation."}"""
        val units2 = adapter.feedChunk(chunk2)
        assertEquals(1, units2.size)
        assertTrue(units2[0] is UiBlock.ExplanationBlock)
        assertEquals("exp1", units2[0].id)
        assertEquals("Start of explanation.", (units2[0] as UiBlock.ExplanationBlock).content)

        // Chunk 3: An invalid block (missing label and max < min) followed by a valid slider block
        val chunk3 = """, {"type": "slider", "id": "bad", "min": 20, "max": 10}, {"type": "slider", "id": "good_rate", "label": "Rate", "min": 0, "max": 10, "value": 3}"""
        val units3 = adapter.feedChunk(chunk3)
        assertEquals(1, units3.size) // Only the valid block is emitted
        assertEquals("good_rate", units3[0].id)
    }

    @Test
    fun `test content correctness compound interest formula calculation`() {
        // Formula: A = P * (1 + r/100)^t
        // P = 1000, r = 5%, t = 10 years -> A = 1000 * (1.05)^10 = 1628.8946
        val simulation = CompoundInterestSimulation
        val result5 = simulation.compute(mapOf("principal" to 1000.0, "annual_rate" to 5.0, "years" to 10.0))

        assertEquals(11, result5.points.size) // t = 0 to 10
        val a5 = result5.summary["final_amount"] as Float
        assertEquals(1628.89f, a5, 0.05f)

        val interest5 = result5.summary["total_interest"] as Float
        assertEquals(628.89f, interest5, 0.05f)

        // P = 1000, r = 10%, t = 10 years -> A = 1000 * (1.10)^10 = 2593.7424
        val result10 = simulation.compute(mapOf("principal" to 1000.0, "annual_rate" to 10.0, "years" to 10.0))
        val a10 = result10.summary["final_amount"] as Float
        assertEquals(2593.74f, a10, 0.05f)

        val interest10 = result10.summary["total_interest"] as Float
        assertEquals(1593.74f, interest10, 0.05f)

        // Exponential property: interest at 10% is more than double interest at 5%
        assertTrue("Compounding interest must be non-linear / exponential", interest10 > (interest5 * 2f))
    }

    @Test
    fun `test interaction correctness local reactive recomputation on slider movement`() {
        val engine = InteractiveLessonEngine()
        val plan = InteractiveLessonEngine.createCompoundInterestLesson()
        engine.initializeFromPlan(plan)

        assertEquals(5.0, engine.getVariable("annual_rate"), 0.001)

        // Simulate slider moving from 5.0 to 8.0
        engine.setVariable("annual_rate", 8.0)

        // Recompute simulation locally
        val sim = engine.computeSimulation("compound_interest_simulation")
        val finalAmount = sim.summary["final_amount"] as Float

        // P = 1000, r = 8%, t = 10 -> 1000 * (1.08)^10 = 2158.92
        assertEquals(2158.92f, finalAmount, 0.05f)

        // Dynamic formula recomputation
        val formulaResult = engine.evaluateFormula("principal * (1 + annual_rate / 100)^years")
        assertEquals(2158.92, formulaResult, 0.05)
    }

    @Test
    fun `test performance local reactive recomputation benchmark`() {
        val engine = InteractiveLessonEngine()
        val plan = InteractiveLessonEngine.createCompoundInterestLesson()
        engine.initializeFromPlan(plan)

        // Measure 500 consecutive slider update & simulation recomputations
        val startTime = System.currentTimeMillis()
        for (i in 1..500) {
            val r = (i % 150) / 10.0
            engine.setVariable("annual_rate", r)
            val res = engine.computeSimulation("compound_interest_simulation")
            assertNotNull(res)
        }
        val elapsed = System.currentTimeMillis() - startTime

        // In 2GB RAM device, local computation must be sub-millisecond per frame (< 500ms total for 500 frames)
        assertTrue("500 simulation recomputations took $elapsed ms, must be under 500ms", elapsed < 500)
    }

    @Test
    fun `test adaptivity assessment updates learning records and selects next concept`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pedagogicalEngine = TeacherPedagogicalEngine(memoryStore)
        val lessonEngine = InteractiveLessonEngine()

        val plan = InteractiveLessonEngine.createCompoundInterestLesson()

        // Verify initial state
        val initialConcepts = memoryStore.loadConcepts()
        val initialMastery = initialConcepts.firstOrNull { it.id == "compound_interest" }?.mastery ?: 0f

        // Correct answer on exponential compounding
        val result = lessonEngine.assessAnswer(
            plan = plan,
            assessmentId = "compound_check",
            selectedOptionIndex = 1, // Correct: More than doubles
            pedagogicalEngine = pedagogicalEngine,
        )

        assertTrue(result.isCorrect)
        assertTrue(result.feedback.contains("Correct!"))
        assertNotNull(result.updatedConcept)
        assertTrue(result.updatedConcept!!.mastery > initialMastery)
        assertTrue(result.updatedConcept!!.pKnown > 0.1f)
        assertEquals(1, result.updatedConcept!!.correctCount)

        // Verify next pedagogical step is selected
        assertNotNull(result.nextRecommendedConcept)
    }

    @Test
    fun `test adaptivity incorrect answer diagnoses misconception and retains record`() {
        val context = createMockContext(tempFolder.root)
        val memoryStore = TeacherMemoryStore(context)
        val pedagogicalEngine = TeacherPedagogicalEngine(memoryStore)
        val lessonEngine = InteractiveLessonEngine()

        val plan = InteractiveLessonEngine.createCompoundInterestLesson()

        // Incorrect answer selecting linear option
        val result = lessonEngine.assessAnswer(
            plan = plan,
            assessmentId = "compound_check",
            selectedOptionIndex = 0, // Incorrect: Doubles linearly
            pedagogicalEngine = pedagogicalEngine,
        )

        assertFalse(result.isCorrect)
        assertTrue(result.feedback.contains("Incorrect"))
        assertNotNull(result.updatedConcept)
        assertEquals(1, result.updatedConcept!!.attempts)
        assertEquals(0, result.updatedConcept!!.correctCount)
        assertTrue(result.updatedConcept!!.misconceptions.isNotEmpty())
        assertTrue(result.updatedConcept!!.misconceptions.any { it.contains("linear", ignoreCase = true) })
    }

    @Test
    fun `test safety local simulation runs within restricted deterministic constraints`() {
        val gatekeeper = AutonomyGatekeeper(AutonomyMode.MODE_D_RESTRICTED_AUTO)

        // Mathematical simulation evaluation is deterministic and safe
        val approval = gatekeeper.evaluateAction(
            toolName = "InteractiveLessonEngine",
            operation = "compute_simulation",
            arguments = mapOf("dataSource" to "compound_interest_simulation"),
            isDeterministic = true,
            isReadOnly = true,
            leavesDevice = false,
        )

        assertNull("Deterministic local interactive simulations must run automatically without prompting", approval)
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
