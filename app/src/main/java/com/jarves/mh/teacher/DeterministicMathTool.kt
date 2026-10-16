package com.jarves.mh.teacher

import kotlin.math.*

object DeterministicMathTool {

    fun evaluate(expression: String, variables: Map<String, Double> = emptyMap()): Double {
        val trimmed = expression.trim()
        require(trimmed.isNotEmpty()) { "Mathematical expression cannot be empty" }
        val parser = ExpressionParser(trimmed, variables)
        return parser.parse()
    }

    private class ExpressionParser(
        private val str: String,
        private val vars: Map<String, Double>,
    ) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun skipWhitespace() {
            while (ch == ' '.code || ch == '\t'.code || ch == '\n'.code || ch == '\r'.code) {
                nextChar()
            }
        }

        private fun eat(charToEat: Int): Boolean {
            skipWhitespace()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            skipWhitespace()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character '${ch.toChar()}' at position $pos")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if ((ch in 'a'.code..'z'.code) || (ch in 'A'.code..'Z'.code)) {
                while ((ch in 'a'.code..'z'.code) || (ch in 'A'.code..'Z'.code) || (ch in '0'.code..'9'.code) || ch == '_'.code) {
                    nextChar()
                }
                val rawIdentifier = str.substring(startPos, pos)
                val identifier = rawIdentifier.lowercase()
                if (eat('('.code)) {
                    val arg = parseExpression()
                    if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis ')' after function $identifier")
                    x = when (identifier) {
                        "sqrt" -> {
                            if (arg < 0.0) throw ArithmeticException("Cannot take square root of negative number: $arg")
                            sqrt(arg)
                        }
                        "sin" -> sin(arg)
                        "cos" -> cos(arg)
                        "tan" -> tan(arg)
                        "log", "log10" -> log10(arg)
                        "ln" -> ln(arg)
                        "abs" -> abs(arg)
                        "exp" -> exp(arg)
                        "ceil" -> ceil(arg)
                        "floor" -> floor(arg)
                        "round" -> round(arg)
                        else -> throw IllegalArgumentException("Unknown math function '$identifier'")
                    }
                } else {
                    x = when (identifier) {
                        "pi" -> PI
                        "e" -> E
                        else -> vars[identifier]
                            ?: vars[rawIdentifier]
                            ?: vars.entries.firstOrNull { it.key.equals(identifier, ignoreCase = true) }?.value
                            ?: throw IllegalArgumentException("Unknown variable or constant '$identifier'")
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected character '${ch.toChar()}' at position $pos")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
