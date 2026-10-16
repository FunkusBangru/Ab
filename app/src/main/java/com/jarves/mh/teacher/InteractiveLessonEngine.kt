package com.jarves.mh.teacher

import java.util.concurrent.ConcurrentHashMap

class InteractiveLessonEngine {

    private val simulationRegistry = ConcurrentHashMap<String, SimulationDataSource>()
    private val variableBindings = ConcurrentHashMap<String, Double>()

    init {
        registerSimulation("compound_interest_simulation", CompoundInterestSimulation)
    }

    fun registerSimulation(id: String, dataSource: SimulationDataSource) {
        simulationRegistry[id] = dataSource
    }

    fun setVariable(id: String, value: Double) {
        variableBindings[id] = value
    }

    fun setVariables(vars: Map<String, Double>) {
        variableBindings.putAll(vars)
    }

    fun getVariable(id: String, default: Double = 0.0): Double =
        variableBindings[id] ?: default

    fun getAllVariables(): Map<String, Double> =
        HashMap(variableBindings)

    fun initializeFromPlan(plan: UiComponentPlan) {
        for (block in plan.blocks) {
            if (block is UiBlock.SliderBlock) {
                if (!variableBindings.containsKey(block.id)) {
                    variableBindings[block.id] = block.value.toDouble()
                }
            }
        }
    }

    fun computeSimulation(dataSourceId: String): SimulationResult {
        val dataSource = simulationRegistry[dataSourceId]
            ?: return SimulationResult(points = emptyList(), summary = mapOf("error" to "Unknown data source: $dataSourceId"))
        return dataSource.compute(variableBindings)
    }

    fun evaluateFormula(expression: String): Double {
        return runCatching {
            DeterministicMathTool.evaluate(expression, variableBindings)
        }.getOrDefault(0.0)
    }

    fun assessAnswer(
        plan: UiComponentPlan,
        assessmentId: String,
        selectedOptionIndex: Int,
        pedagogicalEngine: TeacherPedagogicalEngine,
    ): AssessmentSubmissionResult {
        val assessmentBlock = plan.blocks.filterIsInstance<UiBlock.AssessmentBlock>()
            .firstOrNull { it.id == assessmentId }

        if (assessmentBlock == null) {
            return AssessmentSubmissionResult(
                blockId = assessmentId,
                selectedIndex = selectedOptionIndex,
                isCorrect = false,
                feedback = "Assessment block not found.",
                updatedConcept = null,
                nextRecommendedConcept = null,
            )
        }

        val isCorrect = selectedOptionIndex == assessmentBlock.correctOptionIndex
        val feedback = if (isCorrect) {
            if (assessmentBlock.explanation.isNotBlank()) {
                "Correct! ${assessmentBlock.explanation}"
            } else {
                "Correct! Excellent reasoning."
            }
        } else {
            val base = "Incorrect."
            if (assessmentBlock.explanation.isNotBlank()) {
                "$base ${assessmentBlock.explanation}"
            } else {
                "$base Review the compounding formula and try adjusting the slider to observe the curve."
            }
        }

        val conceptId = plan.conceptId?.ifBlank { null }
            ?: plan.title.lowercase().replace("\\s+".toRegex(), "_")

        val detectedMisconception = if (!isCorrect) assessmentBlock.misconception else null

        val updatedConcept = pedagogicalEngine.recordExerciseResult(
            conceptId = conceptId,
            isCorrect = isCorrect,
            rating = if (isCorrect) FsrsRating.GOOD else FsrsRating.AGAIN,
            detectedMisconception = detectedMisconception,
        )

        val nextConcept = pedagogicalEngine.getNextRecommendedConcept()

        return AssessmentSubmissionResult(
            blockId = assessmentId,
            selectedIndex = selectedOptionIndex,
            isCorrect = isCorrect,
            feedback = feedback,
            updatedConcept = updatedConcept,
            nextRecommendedConcept = nextConcept,
        )
    }

    companion object {
        fun createCompoundInterestLesson(): UiComponentPlan {
            return UiComponentPlan(
                version = 1,
                mode = "interactive_lesson",
                title = "Explore compound interest",
                conceptId = "compound_interest",
                blocks = listOf(
                    UiBlock.ExplanationBlock(
                        id = "concept_intro",
                        title = "How Compounding Works",
                        content = "Interest can itself earn interest. With compound interest, each year's earned interest is added to the principal, accelerating growth over time according to A = P(1 + r/100)^t.",
                    ),
                    UiBlock.SliderBlock(
                        id = "annual_rate",
                        label = "Annual interest rate",
                        min = 0f,
                        max = 15f,
                        step = 0.5f,
                        value = 5f,
                        unit = "%",
                    ),
                    UiBlock.SliderBlock(
                        id = "principal",
                        label = "Initial principal (P)",
                        min = 100f,
                        max = 5000f,
                        step = 100f,
                        value = 1000f,
                        unit = "$",
                    ),
                    UiBlock.SliderBlock(
                        id = "years",
                        label = "Time duration (t)",
                        min = 1f,
                        max = 30f,
                        step = 1f,
                        value = 10f,
                        unit = "years",
                    ),
                    UiBlock.FormulaBlock(
                        id = "balance_metric",
                        label = "Accumulated Balance A = P(1 + r/100)^t",
                        expression = "principal * (1 + annual_rate / 100)^years",
                        unit = "$",
                    ),
                    UiBlock.ChartBlock(
                        id = "growth",
                        dataSource = "compound_interest_simulation",
                        title = "Growth Curve Over Time",
                        xAxisLabel = "Years",
                        yAxisLabel = "Total Balance ($)",
                    ),
                    UiBlock.AssessmentBlock(
                        id = "compound_check",
                        prompt = "If the annual interest rate doubles from 5% to 10% over 10 years, does the total interest earned simply double, or more than double?",
                        options = listOf(
                            "Doubles linearly",
                            "More than doubles due to compounding",
                            "Less than doubles",
                            "Remains unchanged",
                        ),
                        correctOptionIndex = 1,
                        explanation = "At 5%, $1000 earns $628.89 interest. At 10%, it earns $1593.74 interest (more than 2.5x the interest), proving compounding is exponential rather than linear!",
                        misconception = "Assumed linear interest growth rather than exponential compounding",
                    ),
                ),
            )
        }
    }
}
