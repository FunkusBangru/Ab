package com.jarves.mh.teacher

import kotlin.math.max
import kotlin.math.min

object BktEngine {
    const val DEFAULT_P_TRANSITION = 0.20f
    const val DEFAULT_P_GUESS = 0.20f
    const val DEFAULT_P_SLIP = 0.10f
    const val MASTERY_THRESHOLD = 0.85f

    fun update(
        currentPknown: Float,
        isCorrect: Boolean,
        pTransition: Float = DEFAULT_P_TRANSITION,
        pGuess: Float = DEFAULT_P_GUESS,
        pSlip: Float = DEFAULT_P_SLIP,
    ): Float {
        val prior = currentPknown.coerceIn(0.01f, 0.99f)
        val pG = pGuess.coerceIn(0.01f, 0.50f)
        val pS = pSlip.coerceIn(0.01f, 0.50f)
        val pT = pTransition.coerceIn(0.01f, 0.90f)

        val posteriorKnown = if (isCorrect) {
            val numerator = prior * (1.0f - pS)
            val denominator = numerator + ((1.0f - prior) * pG)
            if (denominator <= 0f) prior else numerator / denominator
        } else {
            val numerator = prior * pS
            val denominator = numerator + ((1.0f - prior) * (1.0f - pG))
            if (denominator <= 0f) prior else numerator / denominator
        }

        val nextPknown = posteriorKnown + ((1.0f - posteriorKnown) * pT)
        return nextPknown.coerceIn(0.01f, 0.99f)
    }

    fun predictCorrect(
        pKnown: Float,
        pGuess: Float = DEFAULT_P_GUESS,
        pSlip: Float = DEFAULT_P_SLIP,
    ): Float {
        val l = pKnown.coerceIn(0.0f, 1.0f)
        return (l * (1.0f - pSlip)) + ((1.0f - l) * pGuess)
    }
}
