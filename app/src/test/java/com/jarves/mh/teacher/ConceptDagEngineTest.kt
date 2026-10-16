package com.jarves.mh.teacher

import org.junit.Assert.*
import org.junit.Test

class ConceptDagEngineTest {

    @Test
    fun `test add prerequisite and retrieve dependencies`() {
        val dag = ConceptDagEngine()
        val added = dag.addPrerequisite("calculus", "algebra")
        assertTrue(added)

        val prereqs = dag.getPrerequisites("calculus")
        assertEquals(setOf("algebra"), prereqs)

        val dependents = dag.getDependents("algebra")
        assertEquals(setOf("calculus"), dependents)
    }

    @Test
    fun `test cycle detection prevents circular prerequisites`() {
        val dag = ConceptDagEngine()
        assertTrue(dag.addPrerequisite("B", "A"))
        assertTrue(dag.addPrerequisite("C", "B"))

        val cycleEdgeAdded = dag.addPrerequisite("A", "C")
        assertFalse("Adding cycle should be rejected", cycleEdgeAdded)
    }

    @Test
    fun `test knowledge gap discovery finds unmastered prerequisites`() {
        val dag = ConceptDagEngine()
        dag.addPrerequisite("machine_learning", "linear_algebra")
        dag.addPrerequisite("linear_algebra", "basic_matrices")
        dag.addPrerequisite("machine_learning", "python_basics")

        val mastered = setOf("python_basics")

        val gaps = dag.findKnowledgeGaps("machine_learning", mastered)
        assertTrue(gaps.contains("linear_algebra"))
        assertTrue(gaps.contains("basic_matrices"))
        assertFalse(gaps.contains("python_basics"))
    }

    @Test
    fun `test ready to learn identifies concepts whose prerequisites are satisfied`() {
        val dag = ConceptDagEngine()
        val c1 = Concept(id = "variables", subject = "CS", name = "Variables")
        val c2 = Concept(id = "functions", subject = "CS", name = "Functions")
        val c3 = Concept(id = "recursion", subject = "CS", name = "Recursion")

        dag.addPrerequisite("functions", "variables")
        dag.addPrerequisite("recursion", "functions")

        val ready1 = dag.getReadyToLearn(listOf(c1, c2, c3), setOf("variables"))
        assertEquals(listOf(c2), ready1)

        val ready2 = dag.getReadyToLearn(listOf(c1, c2, c3), setOf("variables", "functions"))
        assertEquals(listOf(c3), ready2)
    }

    @Test
    fun `test topological sort orders dependencies before dependents`() {
        val dag = ConceptDagEngine()
        dag.addPrerequisite("integration", "derivatives")
        dag.addPrerequisite("derivatives", "limits")
        dag.addPrerequisite("limits", "algebra")

        val sorted = dag.topologicalSort(listOf("integration", "algebra", "limits", "derivatives"))
        assertEquals(listOf("algebra", "limits", "derivatives", "integration"), sorted)
    }
}
