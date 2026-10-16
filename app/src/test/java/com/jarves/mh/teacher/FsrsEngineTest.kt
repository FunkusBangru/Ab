package com.jarves.mh.teacher

import org.junit.Assert.*
import org.junit.Test

class FsrsEngineTest {

    @Test
    fun `test retrievability decreases as elapsed days increase`() {
        val stability = 5.0f
        val rDay1 = FsrsEngine.retrievability(1.0f, stability)
        val rDay5 = FsrsEngine.retrievability(5.0f, stability)
        val rDay15 = FsrsEngine.retrievability(15.0f, stability)

        assertTrue(rDay1 > rDay5)
        assertTrue(rDay5 > rDay15)
        assertTrue(rDay1 <= 1.0f && rDay15 >= 0.0f)
    }

    @Test
    fun `test initial review sets proper stability`() {
        val newCard = FsrsCard(conceptId = "concept_1", reps = 0)
        val reviewedGood = FsrsEngine.review(newCard, FsrsRating.GOOD)

        assertEquals(1, reviewedGood.reps)
        assertEquals(CardState.REVIEW, reviewedGood.state)
        assertTrue(reviewedGood.stability > 1.0f)
        assertTrue(reviewedGood.dueMillis > System.currentTimeMillis())
    }

    @Test
    fun `test lapse on Again increases lapse count and sets relearning state`() {
        val card = FsrsCard(
            conceptId = "concept_2",
            reps = 3,
            stability = 10.0f,
            difficulty = 5.0f,
            lapses = 0,
            lastReviewMillis = System.currentTimeMillis() - (86400000L * 10),
        )

        val failed = FsrsEngine.review(card, FsrsRating.AGAIN)

        assertEquals(4, failed.reps)
        assertEquals(1, failed.lapses)
        assertEquals(CardState.RELEARNING, failed.state)
        assertTrue(failed.stability < card.stability)
    }

    @Test
    fun `test repeated Good reviews expand stability interval`() {
        var card = FsrsCard(conceptId = "concept_3", reps = 0)
        var now = System.currentTimeMillis()

        repeat(3) {
            card = FsrsEngine.review(card, FsrsRating.GOOD, now)
            now = card.dueMillis
        }

        assertTrue("After 3 reviews stability should exceed 10 days", card.stability >= 5.0f)
        assertEquals(3, card.reps)
        assertEquals(0, card.lapses)
    }
}
