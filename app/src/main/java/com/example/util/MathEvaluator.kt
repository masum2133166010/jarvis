package com.example.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Recursive-descent mathematical expression evaluator supporting +, -, *, /, ^, %,
 * parentheses, constants (pi, e), and functions (sqrt, sin, cos, tan, log, ln, abs).
 */
object MathEvaluator {
    fun evaluate(expression: String): Result<Double> {
        val cleaned = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("Empty expression"))

        return runCatching {
            val parser = Parser(cleaned)
            val result = parser.parse()
            if (result.isNaN() || result.isInfinite()) {
                throw ArithmeticException("Undefined result")
            }
            result
        }
    }

    fun formatResult(value: Double): String {
        return if (value % 1.0 == 0.0 && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        }
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character: '${ch.toChar()}'")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x / divisor
                    }
                    eat('%'.code) -> x % parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val func = str.substring(startPos, pos).lowercase()
                x = when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        val arg = parseFactor()
                        when (func) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log" -> log10(arg)
                            "ln" -> ln(arg)
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function: $func")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected token in expression")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
