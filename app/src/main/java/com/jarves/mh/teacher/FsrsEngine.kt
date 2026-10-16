package com.jarves.mh.teacher

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object FsrsEngine {
    private const val W0 = 0.40255f
    private const val W1 = 1.18385f
    private const val W2 = 3.173f
    private const val W3 = 15.69105f
    private const val W4 = 7.1949f
    private const val W5 = 0.5345f
    private const val W6 = 1.4604f
    private const val W7 = 0.0046f
    private const val W8 = 1.54575f
    private const val W9 = 0.1192f
    private const val W10 = 1.01925f
    private const val W11 = 1.9395f
    private const val W12 = 0.11f
    private const val W13 = 0.29605f
    private const val W14 = 0.22695f

    private const val W5_TIMES_W4 = W5 * W4 // 3.845674f
    private const val ONE_MINUS_W5 = 1.0f - W5 // 0.4655f
    private const val EXP_W8 = 4.691475f // exp(1.54575f)

    private const val FACTOR = (19.0f / 81.0f)
    private const val MILLIS_PER_DAY = 86400000L
    private const val MILLIS_PER_DAY_FLOAT = 86400000.0f

    fun retrievability(elapsedDays: Float, stability: Float): Float {
        if (stability <= 0f) return 0f
        if (elapsedDays <= 0f) return 1.0f
        val inner = 1.0f + (FACTOR * (elapsedDays / stability))
        return (1.0f / sqrt(inner)).coerceIn(0.0f, 1.0f)
    }

    fun review(card: FsrsCard, rating: FsrsRating, nowMillis: Long = System.currentTimeMillis()): FsrsCard {
        val elapsedDays = if (card.lastReviewMillis > 0L) {
            max(0.0f, (nowMillis - card.lastReviewMillis).toFloat() / MILLIS_PER_DAY_FLOAT)
        } else {
            0.0f
        }

        val r = if (card.reps > 0) retrievability(elapsedDays, card.stability) else 1.0f

        val nextDifficulty = calculateNextDifficulty(card.difficulty, rating)
        val nextStability = if (card.reps == 0) {
            initStability(rating)
        } else if (rating == FsrsRating.AGAIN) {
            calculateLapseStability(nextDifficulty, card.stability, r)
        } else {
            calculateRecallStability(nextDifficulty, card.stability, r, rating)
        }

        val nextIntervalDays = max(1, nextStability.roundToInt())
        val nextDueMillis = nowMillis + (nextIntervalDays.toLong() * MILLIS_PER_DAY)

        val nextState = when (rating) {
            FsrsRating.AGAIN -> CardState.RELEARNING
            else -> CardState.REVIEW
        }

        val lapses = if (rating == FsrsRating.AGAIN) card.lapses + 1 else card.lapses

        return card.copy(
            stability = nextStability,
            difficulty = nextDifficulty,
            dueMillis = nextDueMillis,
            lastReviewMillis = nowMillis,
            reps = card.reps + 1,
            lapses = lapses,
            state = nextState,
        )
    }

    private fun initStability(rating: FsrsRating): Float = when (rating) {
        FsrsRating.AGAIN -> max(0.1f, W0)
        FsrsRating.HARD -> max(0.1f, W1)
        FsrsRating.GOOD -> max(0.1f, W2)
        FsrsRating.EASY -> max(0.1f, W3)
    }

    private fun calculateNextDifficulty(currentD: Float, rating: FsrsRating): Float {
        val gradeOffset = rating.value - 3
        val delta = -W6 * gradeOffset
        val nextD = currentD + delta
        val meanReverted = W5_TIMES_W4 + (ONE_MINUS_W5 * nextD)
        return meanReverted.coerceIn(1.0f, 10.0f)
    }

    private fun calculateRecallStability(d: Float, s: Float, r: Float, rating: FsrsRating): Float {
        val hardPenalty = if (rating == FsrsRating.HARD) W14 else 1.0f
        val easyBonus = if (rating == FsrsRating.EASY) W10 else 1.0f
        val increaseFactor = EXP_W8 * (11.0f - d) * s.pow(-W9) * (exp((1.0f - r) * W10) - 1.0f) * hardPenalty * easyBonus
        return max(0.1f, s * (1.0f + increaseFactor))
    }

    private fun calculateLapseStability(d: Float, s: Float, r: Float): Float {
        val nextS = W11 * d.pow(-W12) * ((s + 1.0f).pow(W13) - 1.0f) * exp((1.0f - r) * W14)
        return min(max(0.1f, nextS), s)
    }
}
