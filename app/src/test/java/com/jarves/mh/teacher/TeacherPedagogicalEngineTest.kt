package com.jarves.mh.teacher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TeacherPedagogicalEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test pedagogical engine records exercise and updates BKT and FSRS`() {
        val memoryStore = TeacherMemoryStore(createMockContext(tempFolder.root))
        val engine = TeacherPedagogicalEngine(memoryStore)

        val initialConcept = Concept(
            id = "quadratic_formula",
            subject = "Algebra",
            name = "Quadratic Formula",
            mastery = 0.2f,
            pKnown = 0.2f,
        )
        memoryStore.saveConcept(initialConcept)

        val updated = engine.recordExerciseResult(
            conceptId = "quadratic_formula",
            isCorrect = true,
            rating = FsrsRating.GOOD,
        )

        assertTrue(updated.pKnown > 0.2f)
        assertTrue(updated.mastery > 0.2f)
        assertEquals(1, updated.attempts)
        assertEquals(1, updated.correctCount)

        val reviews = memoryStore.loadReviews()
        assertEquals(1, reviews.size)
        assertEquals("quadratic_formula", reviews[0].conceptId)
        assertEquals(CardState.REVIEW, reviews[0].state)
        assertTrue(reviews[0].dueMillis > System.currentTimeMillis())
    }

    @Test
    fun `test pedagogical engine detects and stores misconceptions`() {
        val memoryStore = TeacherMemoryStore(createMockContext(tempFolder.root))
        val engine = TeacherPedagogicalEngine(memoryStore)

        val updated = engine.recordExerciseResult(
            conceptId = "fractions_addition",
            isCorrect = false,
            rating = FsrsRating.AGAIN,
            detectedMisconception = "Added numerators and denominators directly without common denominator",
        )

        assertEquals(1, updated.misconceptions.size)
        assertTrue(updated.misconceptions[0].contains("without common denominator"))
    }

    @Test
    fun `test recommended concept prioritizes due reviews then ready concepts`() {
        val memoryStore = TeacherMemoryStore(createMockContext(tempFolder.root))
        val engine = TeacherPedagogicalEngine(memoryStore)

        val c1 = Concept(id = "c1", subject = "Math", name = "Basic Counting", mastery = 0.9f, attempts = 5)
        val c2 = Concept(id = "c2", subject = "Math", name = "Addition", mastery = 0.0f)
        memoryStore.saveConcepts(listOf(c1, c2))
        engine.dag.addPrerequisite("c2", "c1")

        val recommended = engine.getNextRecommendedConcept()
        assertNotNull(recommended)
        assertEquals("c2", recommended?.id)
    }

    @Test
    fun `test build pedagogical system prompt injects style and autonomy mode`() {
        val memoryStore = TeacherMemoryStore(createMockContext(tempFolder.root))
        val engine = TeacherPedagogicalEngine(memoryStore)

        val profile = LearnerProfile(
            name = "Alice",
            preferredStyle = ExplanationStyle.SOCRATIC,
        )
        val concept = Concept(id = "limits", subject = "Calculus", name = "Limits", mastery = 0.5f, pKnown = 0.6f)

        val prompt = engine.buildPedagogicalSystemPrompt(
            learnerProfile = profile,
            activeConcept = concept,
            autonomyMode = AutonomyMode.MODE_A_EVERY_ACTION,
        )

        assertTrue(prompt.contains("Alice"))
        assertTrue(prompt.contains("Socratic"))
        assertTrue(prompt.contains("Limits (Calculus)"))
        assertTrue(prompt.contains("Ask Before Every Action"))
        assertTrue(prompt.contains("MANDATORY: Before executing any tool"))
    }

    private fun createMockContext(rootDir: File): android.content.Context {
        val mockContext = java.lang.reflect.Proxy.newProxyInstance(
            android.content.Context::class.java.classLoader,
            arrayOf(android.content.Context::class.java),
        ) { _, method, _ ->
            if (method.name == "getFilesDir") {
                rootDir
            } else {
                null
            }
        } as android.content.Context
        return mockContext
    }
}
