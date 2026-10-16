package com.jarves.mh.teacher

import java.util.UUID

// =========================================================================
// Layer 1 — Model adapter
// Replaceable LLM provider
// Takes user's request and conversation context. Produces an answer plan,
// proposes components, and decides when follow-up or tool calls are warranted.
// =========================================================================

data class ConversationTurn(
    val fromUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class ModelPlanResult(
    val explanationText: String,
    val proposedComponentPlan: UiComponentPlan? = null,
    val requiresToolCall: Boolean = false,
    val toolName: String? = null,
    val toolArguments: Map<String, String> = emptyMap(),
    val followUpQuestion: String? = null,
    val providerId: String = "default_model_adapter",
)

interface TeacherModelAdapter {
    val providerName: String

    fun planResponse(
        userRequest: String,
        history: List<ConversationTurn> = emptyList(),
        activeConcept: Concept? = null,
        learnerProfile: LearnerProfile? = null,
        connectedPlugins: List<AppPluginDefinition> = emptyList(),
    ): ModelPlanResult
}

class LocalPedagogicalModelAdapter : TeacherModelAdapter {
    override val providerName: String = "LocalPedagogicalModelAdapter"

    override fun planResponse(
        userRequest: String,
        history: List<ConversationTurn>,
        activeConcept: Concept?,
        learnerProfile: LearnerProfile?,
        connectedPlugins: List<AppPluginDefinition>,
    ): ModelPlanResult {
        val reqLower = userRequest.lowercase()

        return when {
            reqLower.contains("compound interest") || reqLower.contains("interest rate") || reqLower.contains("investment") -> {
                ModelPlanResult(
                    explanationText = "Compound interest occurs when the interest earned on an investment also begins earning interest in subsequent periods, resulting in exponential growth.",
                    proposedComponentPlan = InteractiveLessonEngine.createCompoundInterestLesson(),
                    providerId = providerName,
                )
            }
            reqLower.contains("plugin") || reqLower.contains("tool") || reqLower.contains("code") -> {
                val plugin = connectedPlugins.firstOrNull { it.isEnabled && it.capabilities.contains(AppPluginCapability.EXECUTE_CODE) }
                if (plugin != null) {
                    ModelPlanResult(
                        explanationText = "You can test this concept hands-on inside your connected ${plugin.displayName} application.",
                        requiresToolCall = true,
                        toolName = "launch_plugin",
                        toolArguments = mapOf("packageName" to plugin.packageName),
                        providerId = providerName,
                    )
                } else {
                    ModelPlanResult(
                        explanationText = "I can guide you through this step by step. Would you like to see a worked example or explore a derivation?",
                        followUpQuestion = "Would you like a worked example or first-principles derivation?",
                        providerId = providerName,
                    )
                }
            }
            else -> {
                ModelPlanResult(
                    explanationText = "Let's explore ${activeConcept?.name ?: "this concept"} from first principles. Consider how decomposing the problem into smaller axioms simplifies understanding.",
                    followUpQuestion = "What is your intuition about the first step?",
                    providerId = providerName,
                )
            }
        }
    }
}

// =========================================================================
// Layer 2 — UI planner + schema
// A versioned declarative format
// Describes layout, content blocks, data, actions and constraints using a
// validated schema. The model cannot invent unrestricted UI component types.
// =========================================================================

class UiPlanner(
    private val schemaValidator: StreamingUiAdapter = StreamingUiAdapter(),
) {
    val schemaVersion: Int = 1
    val allowedBlockTypes: Set<String> = setOf("explanation", "slider", "chart", "formula", "assessment")

    fun validateAndPlan(rawJson: String): UiComponentPlan {
        val plan = schemaValidator.parsePlan(rawJson)
        val constrainedBlocks = plan.blocks.filter { allowedBlockTypes.contains(it.type) }
        return plan.copy(blocks = constrainedBlocks)
    }

    fun parseStreamChunk(chunk: String): List<UiBlock> =
        schemaValidator.feedChunk(chunk)

    fun resetStream() = schemaValidator.reset()
}

// =========================================================================
// Layer 3 — UI runtime
// Reusable components + incremental rendering
// Renders the approved component tree; handles updates, styling, local
// state, errors, and interaction events.
// Recomputes dynamic values (e.g. A = P(1+r/100)^t) locally without calling LLM!
// =========================================================================

sealed class UiInteractionEvent {
    data class SliderChanged(val blockId: String, val newValue: Float) : UiInteractionEvent()
    data class AssessmentOptionSelected(val blockId: String, val selectedIndex: Int) : UiInteractionEvent()
    data class ToolRequested(val toolName: String, val args: Map<String, String>) : UiInteractionEvent()
}

class UiRuntime(
    val lessonEngine: InteractiveLessonEngine = InteractiveLessonEngine(),
) {
    var activePlan: UiComponentPlan? = null
        private set

    fun loadPlan(plan: UiComponentPlan) {
        activePlan = plan
        lessonEngine.initializeFromPlan(plan)
    }

    fun onSliderMoved(blockId: String, value: Float): SimulationResult? {
        lessonEngine.setVariable(blockId, value.toDouble())
        val chartBlock = activePlan?.blocks?.filterIsInstance<UiBlock.ChartBlock>()?.firstOrNull()
        return if (chartBlock != null) {
            lessonEngine.computeSimulation(chartBlock.dataSource)
        } else null
    }

    fun getVariable(id: String): Double = lessonEngine.getVariable(id)

    fun evaluateFormula(expression: String): Double = lessonEngine.evaluateFormula(expression)
}

// =========================================================================
// Layer 4 — Learning engine
// Independent teacher logic + local memory
// Assesses answers, updates learning records and selects what to teach next.
// The UI displays the teaching decision but does not determine mastery by itself.
// =========================================================================

data class TeachingDecision(
    val isCorrect: Boolean,
    val feedback: String,
    val estimatedMastery: Float,
    val bayesianPKnown: Float,
    val nextRecommendedConcept: Concept?,
    val reviewDueMillis: Long,
)

class LearningEngine(
    val pedagogicalEngine: TeacherPedagogicalEngine,
) {
    fun assessAnswer(
        plan: UiComponentPlan,
        assessmentId: String,
        selectedOptionIndex: Int,
    ): TeachingDecision {
        val assessBlock = plan.blocks.filterIsInstance<UiBlock.AssessmentBlock>()
            .firstOrNull { it.id == assessmentId }

        val isCorrect = assessBlock != null && selectedOptionIndex == assessBlock.correctOptionIndex
        val conceptId = plan.conceptId ?: plan.title.lowercase().replace(" ", "_")

        val updatedConcept = pedagogicalEngine.recordExerciseResult(
            conceptId = conceptId,
            isCorrect = isCorrect,
            rating = if (isCorrect) FsrsRating.GOOD else FsrsRating.AGAIN,
            detectedMisconception = if (!isCorrect) assessBlock?.misconception else null,
        )

        val nextConcept = pedagogicalEngine.getNextRecommendedConcept()

        val feedback = if (isCorrect) {
            assessBlock?.explanation?.ifBlank { "Correct! Great understanding." } ?: "Correct!"
        } else {
            assessBlock?.explanation?.ifBlank { "Incorrect. Review the principles and observe how the variables interact." } ?: "Incorrect."
        }

        return TeachingDecision(
            isCorrect = isCorrect,
            feedback = feedback,
            estimatedMastery = updatedConcept.mastery,
            bayesianPKnown = updatedConcept.pKnown,
            nextRecommendedConcept = nextConcept,
            reviewDueMillis = System.currentTimeMillis() + (86400000L * 2),
        )
    }
}

// =========================================================================
// Unified Cognitive Pipeline (Orchestrates Layers 1 -> 2 -> 3 -> 4)
// =========================================================================

class CognitiveTeachingPipeline(
    val modelAdapter: TeacherModelAdapter,
    val uiPlanner: UiPlanner,
    val uiRuntime: UiRuntime,
    val learningEngine: LearningEngine,
) {
    fun handleUserRequest(
        userRequest: String,
        history: List<ConversationTurn> = emptyList(),
        activeConcept: Concept? = null,
        profile: LearnerProfile? = null,
        plugins: List<AppPluginDefinition> = emptyList(),
    ): ModelPlanResult {
        // Layer 1: Model adapter proposes plan and components
        val planResult = modelAdapter.planResponse(
            userRequest = userRequest,
            history = history,
            activeConcept = activeConcept,
            learnerProfile = profile,
            connectedPlugins = plugins,
        )

        // Layer 2: UI planner validates the declarative component schema
        val validatedPlan = planResult.proposedComponentPlan?.let {
            val validatedBlocks = it.blocks.filter { b -> uiPlanner.allowedBlockTypes.contains(b.type) }
            it.copy(blocks = validatedBlocks)
        }

        // Layer 3: UI runtime loads the validated component tree
        if (validatedPlan != null) {
            uiRuntime.loadPlan(validatedPlan)
        }

        return planResult.copy(proposedComponentPlan = validatedPlan)
    }

    fun submitAssessmentAnswer(
        assessmentId: String,
        selectedOption: Int,
    ): TeachingDecision? {
        val plan = uiRuntime.activePlan ?: return null
        // Layer 4: Independent Learning Engine evaluates answer and updates records
        return learningEngine.assessAnswer(plan, assessmentId, selectedOption)
    }
}
