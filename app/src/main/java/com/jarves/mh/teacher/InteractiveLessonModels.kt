package com.jarves.mh.teacher

import kotlin.math.pow
import kotlin.math.round

data class DataPoint(
    val x: Float,
    val y: Float,
    val label: String? = null,
)

data class SimulationResult(
    val points: List<DataPoint>,
    val summary: Map<String, Any> = emptyMap(),
)

fun interface SimulationDataSource {
    fun compute(parameters: Map<String, Double>): SimulationResult
}

object CompoundInterestSimulation : SimulationDataSource {
    override fun compute(parameters: Map<String, Double>): SimulationResult {
        val rate = parameters["annual_rate"] ?: parameters["r"] ?: 5.0
        val principal = parameters["principal"] ?: parameters["p"] ?: 1000.0
        val years = (parameters["years"] ?: parameters["t"] ?: 10.0).toInt().coerceIn(1, 50)

        val points = ArrayList<DataPoint>(years + 1)
        val rateMultiplier = 1.0 + (rate / 100.0)

        for (t in 0..years) {
            val amount = principal * rateMultiplier.pow(t.toDouble())
            val roundedAmount = (round(amount * 100.0) / 100.0).toFloat()
            points.add(DataPoint(x = t.toFloat(), y = roundedAmount, label = "Year $t: $$roundedAmount"))
        }

        val finalAmount = points.last().y
        val totalInterest = finalAmount - principal.toFloat()
        val growthFactor = if (principal > 0) finalAmount / principal.toFloat() else 1.0f

        return SimulationResult(
            points = points,
            summary = mapOf(
                "principal" to principal,
                "annual_rate" to rate,
                "years" to years,
                "final_amount" to finalAmount,
                "total_interest" to totalInterest,
                "growth_factor" to growthFactor,
            ),
        )
    }
}

sealed class UiBlock {
    abstract val id: String
    abstract val type: String

    data class ExplanationBlock(
        override val id: String,
        val content: String,
        val title: String? = null,
    ) : UiBlock() {
        override val type: String = "explanation"
    }

    data class SliderBlock(
        override val id: String,
        val label: String,
        val min: Float,
        val max: Float,
        val step: Float = 1.0f,
        val value: Float,
        val unit: String = "",
    ) : UiBlock() {
        override val type: String = "slider"
    }

    data class ChartBlock(
        override val id: String,
        val dataSource: String,
        val title: String = "",
        val xAxisLabel: String = "Years",
        val yAxisLabel: String = "Amount ($)",
    ) : UiBlock() {
        override val type: String = "chart"
    }

    data class FormulaBlock(
        override val id: String,
        val label: String,
        val expression: String,
        val unit: String = "",
    ) : UiBlock() {
        override val type: String = "formula"
    }

    data class AssessmentBlock(
        override val id: String,
        val prompt: String,
        val options: List<String>,
        val correctOptionIndex: Int,
        val explanation: String = "",
        val misconception: String? = null,
    ) : UiBlock() {
        override val type: String = "assessment"
    }
}

data class UiComponentPlan(
    val version: Int = 1,
    val mode: String = "interactive_lesson",
    val title: String,
    val conceptId: String? = null,
    val blocks: List<UiBlock> = emptyList(),
)

data class AssessmentSubmissionResult(
    val blockId: String,
    val selectedIndex: Int,
    val isCorrect: Boolean,
    val feedback: String,
    val updatedConcept: Concept?,
    val nextRecommendedConcept: Concept?,
)
