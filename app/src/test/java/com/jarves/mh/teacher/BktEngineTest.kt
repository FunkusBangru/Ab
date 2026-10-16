package com.jarves.mh.teacher

import org.junit.Assert.*
import org.junit.Test

class BktEngineTest {

    @Test
    fun `test BKT probability increases on correct response`() {
        val initialP = 0.20f
        val updated = BktEngine.update(initialP, isCorrect = true)
        assertTrue("Mastery probability should increase after a correct answer", updated > initialP)
    }

    @Test
    fun `test BKT probability decreases or stabilizes on incorrect response`() {
        val initialP = 0.60f
        val updated = BktEngine.update(initialP, isCorrect = false)
        assertTrue("Mastery probability should be lower than initial after an incorrect answer", updated < initialP)
    }

    @Test
    fun `test multiple correct responses converge toward mastery`() {
        var p = 0.10f
        repeat(5) {
            p = BktEngine.update(p, isCorrect = true)
        }
        assertTrue("5 consecutive correct responses should surpass 0.80 mastery", p >= 0.80f)
    }

    @Test
    fun `test BKT values remain strictly bounded within 0 and 1`() {
        val low = BktEngine.update(0.001f, isCorrect = false)
        val high = BktEngine.update(0.999f, isCorrect = true)
        assertTrue(low >= 0.01f)
        assertTrue(high <= 0.99f)
    }

    @Test
    fun `test predictCorrect increases with higher mastery`() {
        val lowPred = BktEngine.predictCorrect(0.10f)
        val highPred = BktEngine.predictCorrect(0.90f)
        assertTrue(highPred > lowPred)
    }
}
