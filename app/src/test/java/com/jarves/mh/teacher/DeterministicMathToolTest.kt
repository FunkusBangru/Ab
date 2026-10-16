package com.jarves.mh.teacher

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class DeterministicMathToolTest {

    @Test
    fun `test basic arithmetic and precedence`() {
        assertEquals(4.0, DeterministicMathTool.evaluate("2 + 2"), 0.0001)
        assertEquals(14.0, DeterministicMathTool.evaluate("2 + 3 * 4"), 0.0001)
        assertEquals(20.0, DeterministicMathTool.evaluate("(2 + 3) * 4"), 0.0001)
        assertEquals(2.5, DeterministicMathTool.evaluate("5 / 2"), 0.0001)
        assertEquals(1.0, DeterministicMathTool.evaluate("10 % 3"), 0.0001)
    }

    @Test
    fun `test power and scientific functions`() {
        assertEquals(256.0, DeterministicMathTool.evaluate("2^8"), 0.0001)
        assertEquals(12.0, DeterministicMathTool.evaluate("sqrt(144)"), 0.0001)
        assertEquals(1.0, DeterministicMathTool.evaluate("sin(pi / 2)"), 0.0001)
        assertEquals(1.0, DeterministicMathTool.evaluate("cos(0)"), 0.0001)
        assertEquals(2.0, DeterministicMathTool.evaluate("log(100)"), 0.0001)
        assertEquals(1.0, DeterministicMathTool.evaluate("ln(e)"), 0.0001)
        assertEquals(5.0, DeterministicMathTool.evaluate("abs(-5)"), 0.0001)
    }

    @Test
    fun `test variable substitution`() {
        val vars = mapOf("x" to 10.0, "y" to 3.0)
        val result = DeterministicMathTool.evaluate("x^2 + y * 2", vars)
        assertEquals(106.0, result, 0.0001)
    }

    @Test(expected = ArithmeticException::class)
    fun `test division by zero throws ArithmeticException`() {
        DeterministicMathTool.evaluate("10 / 0")
    }

    @Test(expected = ArithmeticException::class)
    fun `test square root of negative throws ArithmeticException`() {
        DeterministicMathTool.evaluate("sqrt(-4)")
    }
}
