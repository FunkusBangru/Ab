package com.jarves.mh.teacher

import java.util.HashMap
import java.util.HashSet

class TeacherPedagogicalEngine(
    private val memoryStore: TeacherMemoryStore,
) {
    val dag = ConceptDagEngine()

    init {
        val prereqs = memoryStore.loadPrerequisites()
        for (p in prereqs) {
            dag.addPrerequisite(p.conceptId, p.prerequisiteId)
        }
    }

    fun recordExerciseResult(
        conceptId: String,
        isCorrect: Boolean,
        rating: FsrsRating,
        detectedMisconception: String? = null,
    ): Concept {
        val concepts = memoryStore.loadConcepts()
        val current = concepts.firstOrNull { it.id == conceptId } ?: Concept(
            id = conceptId,
            subject = "General",
            name = conceptId,
        )

        val updatedPknown = BktEngine.update(current.pKnown, isCorrect)
        val updatedMastery = ((current.mastery * 0.7f) + (if (isCorrect) 0.3f else 0.0f)).coerceIn(0.0f, 1.0f)

        val updatedMisconceptions = if (!detectedMisconception.isNullOrBlank() && !isCorrect) {
            if (current.misconceptions.contains(detectedMisconception)) {
                current.misconceptions
            } else {
                current.misconceptions + detectedMisconception
            }
        } else {
            current.misconceptions
        }

        val updatedConcept = current.copy(
            mastery = updatedMastery,
            pKnown = updatedPknown,
            attempts = current.attempts + 1,
            correctCount = current.correctCount + (if (isCorrect) 1 else 0),
            misconceptions = updatedMisconceptions,
            lastPracticedMillis = System.currentTimeMillis(),
        )

        val reviews = memoryStore.loadReviews().toMutableList()
        val cardIndex = reviews.indexOfFirst { it.conceptId == conceptId }
        val existingCard = if (cardIndex >= 0) reviews[cardIndex] else FsrsCard(conceptId = conceptId)
        val updatedCard = FsrsEngine.review(existingCard, rating)
        if (cardIndex >= 0) {
            reviews[cardIndex] = updatedCard
        } else {
            reviews.add(updatedCard)
        }

        memoryStore.saveConcept(updatedConcept)
        memoryStore.saveReviews(reviews)

        val profile = memoryStore.loadProfile()
        val allConcepts = memoryStore.loadConcepts()
        val masteredCount = allConcepts.count { it.isMastered }
        memoryStore.saveProfile(
            profile.copy(
                totalExercises = profile.totalExercises + 1,
                totalMasteredConcepts = masteredCount,
            ),
        )

        return updatedConcept
    }

    fun getNextRecommendedConcept(subject: String? = null): Concept? {
        val now = System.currentTimeMillis()
        val allConcepts = memoryStore.loadConcepts()
        val concepts = if (subject.isNullOrBlank()) {
            allConcepts
        } else {
            allConcepts.filter { it.subject.equals(subject, ignoreCase = true) }
        }
        if (concepts.isEmpty()) return null

        val reviews = memoryStore.loadReviews()
        val conceptMap = HashMap<String, Concept>(concepts.size)
        for (c in concepts) {
            conceptMap[c.id] = c
        }

        // Find earliest due review with O(1) membership check
        var earliestDueCard: FsrsCard? = null
        var earliestDueMillis = Long.MAX_VALUE
        for (card in reviews) {
            if (card.dueMillis <= now && card.dueMillis < earliestDueMillis) {
                if (conceptMap.containsKey(card.conceptId)) {
                    earliestDueCard = card
                    earliestDueMillis = card.dueMillis
                }
            }
        }

        if (earliestDueCard != null) {
            return conceptMap[earliestDueCard.conceptId]
        }

        val masteredIds = HashSet<String>(concepts.size)
        for (c in concepts) {
            if (c.isMastered) masteredIds.add(c.id)
        }

        val ready = dag.getReadyToLearn(concepts, masteredIds)
        if (ready.isNotEmpty()) {
            return ready.first()
        }

        return concepts.firstOrNull { !it.isMastered } ?: concepts.firstOrNull()
    }

    fun buildPedagogicalSystemPrompt(
        learnerProfile: LearnerProfile,
        activeConcept: Concept?,
        autonomyMode: AutonomyMode,
        scratchpadEntries: List<ScratchpadEntry> = emptyList(),
        connectedPlugins: List<AppPluginDefinition> = emptyList(),
    ): String = buildString {
        appendLine("You are an expert personalized AI Teacher and learning guide.")
        appendLine("Your student is ${learnerProfile.name}.")
        appendLine()
        appendLine("### Teaching Methodology")
        appendLine(learnerProfile.preferredStyle.promptInstruction)
        appendLine("Core rule: Do not immediately give away the final solution when the student struggles. Decompose the problem, diagnose misunderstandings, and scaffold their learning with guiding hints.")
        appendLine()

        if (activeConcept != null) {
            val pKnownFormatted = ((activeConcept.pKnown * 100).toInt() / 100.0)
            appendLine("### Active Learning Objective")
            appendLine("Concept: ${activeConcept.name} (${activeConcept.subject})")
            appendLine("Current Estimated Mastery: ${(activeConcept.mastery * 100).toInt()}% (BKT P(L)=$pKnownFormatted)")
            if (activeConcept.misconceptions.isNotEmpty()) {
                appendLine("Known student misconceptions to watch out for:")
                for (m in activeConcept.misconceptions) {
                    appendLine("- $m")
                }
            }
            appendLine()
        }

        appendLine("### Autonomy & Safety Policy")
        appendLine("Active Autonomy Mode: ${autonomyMode.title}")
        appendLine(autonomyMode.description)
        if (autonomyMode == AutonomyMode.MODE_A_EVERY_ACTION) {
            appendLine("MANDATORY: Before executing any tool, file modification, or shell command, you MUST explicitly propose the action and ask the student for approval.")
        }
        appendLine()

        if (scratchpadEntries.isNotEmpty()) {
            appendLine("### Current Task Scratchpad")
            val limit = minOf(5, scratchpadEntries.size)
            for (i in 0 until limit) {
                val entry = scratchpadEntries[i]
                appendLine("- [${entry.type}] ${entry.title}: ${entry.content}")
            }
            appendLine()
        }

        val plugins = if (connectedPlugins.isNotEmpty()) {
            connectedPlugins.filter { it.isEnabled }
        } else {
            memoryStore.loadPlugins().filter { it.isEnabled }
        }
        if (plugins.isNotEmpty()) {
            appendLine("### Connected Application Plugins on Student's Device")
            appendLine("The student has connected these local device apps for interactive learning and practice:")
            for (p in plugins) {
                val caps = p.capabilities.joinToString(", ") { it.label }
                appendLine("- ${p.displayName} (${p.targetSubject}): ${p.teachingRole} [Capabilities: $caps]")
            }
            appendLine("Instruction: Encourage the student to use these connected tools for hands-on exercises and verification.")
            appendLine()
        }
    }
}
