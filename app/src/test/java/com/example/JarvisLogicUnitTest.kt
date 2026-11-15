package com.example

import com.example.util.MathEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisLogicUnitTest {
    @Test
    fun mathEvaluator_handlesComplexExpressions() {
        val result = MathEvaluator.evaluate("(12 + 8) * 3 - sqrt(16) + 2^3")
        assertTrue(result.isSuccess)
        assertEquals("64", MathEvaluator.formatResult(result.getOrThrow()))
    }

    @Test
    fun mathEvaluator_handlesDivisionByZero() {
        val result = MathEvaluator.evaluate("42 / 0")
        assertTrue(result.isFailure)
    }

    @Test
    fun mathEvaluator_handlesTrigAndConstants() {
        val result = MathEvaluator.evaluate("sin(90) + cos(0)")
        assertTrue(result.isSuccess)
        assertEquals("2", MathEvaluator.formatResult(result.getOrThrow()))
    }
}
