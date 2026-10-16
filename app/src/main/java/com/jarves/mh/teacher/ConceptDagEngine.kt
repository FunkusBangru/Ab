package com.jarves.mh.teacher

import java.util.ArrayDeque
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

class ConceptDagEngine {
    private val incomingPrerequisites = HashMap<String, HashSet<String>>()
    private val outgoingDependents = HashMap<String, HashSet<String>>()

    fun addPrerequisite(conceptId: String, prerequisiteId: String): Boolean {
        require(conceptId != prerequisiteId) { "A concept cannot be a prerequisite of itself" }
        if (hasPath(prerequisiteId, conceptId)) {
            return false
        }
        incomingPrerequisites.getOrPut(conceptId) { HashSet() }.add(prerequisiteId)
        outgoingDependents.getOrPut(prerequisiteId) { HashSet() }.add(conceptId)
        return true
    }

    fun removePrerequisite(conceptId: String, prerequisiteId: String) {
        incomingPrerequisites[conceptId]?.remove(prerequisiteId)
        outgoingDependents[prerequisiteId]?.remove(conceptId)
    }

    fun getPrerequisites(conceptId: String): Set<String> =
        incomingPrerequisites[conceptId].orEmpty()

    fun getDependents(conceptId: String): Set<String> =
        outgoingDependents[conceptId].orEmpty()

    fun hasPath(sourceId: String, targetId: String, visited: MutableSet<String> = HashSet()): Boolean {
        if (sourceId == targetId) return true
        val queue = ArrayDeque<String>()
        queue.add(sourceId)
        visited.add(sourceId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val dependents = outgoingDependents[current] ?: continue
            for (next in dependents) {
                if (next == targetId) return true
                if (visited.add(next)) {
                    queue.add(next)
                }
            }
        }
        return false
    }

    fun findKnowledgeGaps(conceptId: String, masteredConceptIds: Set<String>): List<String> {
        val prereqs = incomingPrerequisites[conceptId] ?: return emptyList()
        val gaps = ArrayList<String>()
        val queue = ArrayDeque<String>(prereqs)
        val visited = HashSet<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue

            if (current !in masteredConceptIds) {
                gaps.add(current)
                val incoming = incomingPrerequisites[current]
                if (incoming != null) {
                    queue.addAll(incoming)
                }
            }
        }
        return gaps
    }

    fun getReadyToLearn(allConcepts: List<Concept>, masteredConceptIds: Set<String>): List<Concept> {
        if (allConcepts.isEmpty()) return emptyList()
        val result = ArrayList<Concept>()
        for (concept in allConcepts) {
            if (concept.id !in masteredConceptIds) {
                val prereqs = incomingPrerequisites[concept.id]
                val allSatisfied = prereqs == null || prereqs.all { it in masteredConceptIds }
                if (allSatisfied) {
                    result.add(concept)
                }
            }
        }
        return result
    }

    fun topologicalSort(conceptIds: Collection<String>): List<String> {
        if (conceptIds.isEmpty()) return emptyList()
        val subset = if (conceptIds is Set<String>) conceptIds else conceptIds.toSet()
        val inDegree = HashMap<String, Int>(subset.size)

        for (id in subset) {
            val incoming = incomingPrerequisites[id]
            var degree = 0
            if (incoming != null) {
                for (prereq in incoming) {
                    if (prereq in subset) degree++
                }
            }
            inDegree[id] = degree
        }

        val queue = ArrayDeque<String>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }

        val sorted = ArrayList<String>(subset.size)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            sorted.add(current)

            val dependents = outgoingDependents[current] ?: continue
            for (dependent in dependents) {
                if (dependent in subset) {
                    val updatedDegree = (inDegree[dependent] ?: 1) - 1
                    inDegree[dependent] = updatedDegree
                    if (updatedDegree == 0) {
                        queue.add(dependent)
                    }
                }
            }
        }

        if (sorted.size < subset.size) {
            for (id in subset) {
                if (id !in sorted) sorted.add(id)
            }
        }
        return sorted
    }
}
