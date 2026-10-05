/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors / Foldboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import java.util.Locale
import java.util.regex.Pattern

object LocalAiEngine {

    private val TECH_ACRONYMS = mapOf(
        "avf" to "AVF",
        "apk" to "APK",
        "ci" to "CI",
        "ai" to "AI",
        "llm" to "LLM",
        "gboard" to "Gboard",
        "git" to "Git",
        "ssh" to "SSH",
        "ip" to "IP",
        "api" to "API",
        "url" to "URL",
        "json" to "JSON",
        "ram" to "RAM",
        "vm" to "VM",
        "linux" to "Linux",
        "android" to "Android",
        "debian" to "Debian",
        "pixel" to "Pixel",
        "google" to "Google",
        "github" to "GitHub",
        "bash" to "Bash",
        "posix" to "POSIX",
        "arm64" to "ARM64",
        "ime" to "IME"
    )

    fun formatSpeech(input: String): String {
        if (input.isBlank()) return ""

        var text = input.trim()

        // 1. Developer case transformations
        val lower = text.lowercase(Locale.ROOT)
        when {
            lower.startsWith("snake case ") -> return toSnakeCase(text.substring(11))
            lower.startsWith("kebab case ") -> return toKebabCase(text.substring(11))
            lower.startsWith("camel case ") -> return toCamelCase(text.substring(11))
            lower.startsWith("screaming snake ") -> return toSnakeCase(text.substring(16)).uppercase(Locale.ROOT)
            lower.startsWith("pascal case ") -> return toPascalCase(text.substring(12))
        }

        // 2. Replace spoken punctuation words
        text = replacePunctuation(text)

        // 3. Fix spaces around punctuation
        text = fixPunctuationSpacing(text)

        // 4. Capitalize sentences
        text = capitalizeSentences(text)

        // 5. Acronym corrections
        text = applyTechAcronyms(text)

        return text
    }

    private fun replacePunctuation(input: String): String {
        var s = input
        val replacements = listOf(
            Regex("(?i)\\s*\\b(new line|newline)\\b\\s*") to "\n",
            Regex("(?i)\\b(full stop|period)\\b") to ".",
            Regex("(?i)\\bcomma\\b") to ",",
            Regex("(?i)\\bquestion mark\\b") to "?",
            Regex("(?i)\\b(exclamation mark|exclamation point)\\b") to "!",
            Regex("(?i)\\bcolon\\b") to ":",
            Regex("(?i)\\bsemicolon\\b") to ";",
            Regex("(?i)\\b(dot dot dot|ellipsis)\\b") to "...",
            Regex("(?i)\\b(dash|hyphen)\\b") to "-",
            Regex("(?i)\\bunderscore\\b") to "_",
            Regex("(?i)\\btab\\b") to "\t",
            Regex("(?i)\\bopen quote\\b") to "\"",
            Regex("(?i)\\bclose quote\\b") to "\"",
            Regex("(?i)\\b(open parenthesis|open paren)\\b") to "(",
            Regex("(?i)\\b(close parenthesis|close paren)\\b") to ")",
            Regex("(?i)\\b(open bracket|open square bracket)\\b") to "[",
            Regex("(?i)\\b(close bracket|close square bracket)\\b") to "]",
            Regex("(?i)\\b(open brace|open curly bracket)\\b") to "{",
            Regex("(?i)\\b(close brace|close curly bracket)\\b") to "}",
            Regex("(?i)\\b(pipe symbol|vertical bar)\\b") to "|",
            Regex("(?i)\\bforward slash\\b") to "/",
            Regex("(?i)\\bbackslash\\b") to "\\",
            Regex("(?i)\\bat sign\\b") to "@",
            Regex("(?i)\\bhash tag\\b|\\bhashtag\\b") to "#"
        )

        for ((pattern, replacement) in replacements) {
            s = pattern.replace(s, replacement)
        }
        return s
    }

    private fun fixPunctuationSpacing(input: String): String {
        var s = input
        // Remove space before punctuation: "hello , world" -> "hello, world"
        s = s.replace(Regex("\\s+([.,!?:;])"), "$1")
        // Ensure space after punctuation if followed by a letter/digit: "hello,world" -> "hello, world"
        s = s.replace(Regex("([.,!?:;])([a-zA-Z0-9])"), "$1 $2")
        // Remove space inside quotes/parentheses
        s = s.replace(Regex("\\(\\s+"), "(")
        s = s.replace(Regex("\\s+\\)"), ")")
        s = s.replace(Regex("\\[\\s+"), "[")
        s = s.replace(Regex("\\s+\\]"), "]")
        s = s.replace(Regex("\\{\\s+"), "{")
        s = s.replace(Regex("\\s+\\}"), "}")
        return s
    }

    private fun capitalizeSentences(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder()
        var capitalizeNext = true
        for (i in input.indices) {
            val c = input[i]
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
                if (c == '.' || c == '?' || c == '!' || c == '\n') {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun applyTechAcronyms(input: String): String {
        var s = input
        for ((lower, proper) in TECH_ACRONYMS) {
            s = s.replace(Regex("(?i)\\b" + Pattern.quote(lower) + "\\b"), proper)
        }
        return s
    }

    private fun words(input: String): List<String> {
        return input.trim().split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
    }

    fun toSnakeCase(input: String): String {
        return words(input).joinToString("_") { it.lowercase(Locale.ROOT) }
    }

    fun toKebabCase(input: String): String {
        return words(input).joinToString("-") { it.lowercase(Locale.ROOT) }
    }

    fun toCamelCase(input: String): String {
        val w = words(input)
        if (w.isEmpty()) return ""
        return w[0].lowercase(Locale.ROOT) + w.drop(1).joinToString("") {
            it.lowercase(Locale.ROOT).replaceFirstChar { ch -> ch.uppercaseChar() }
        }
    }

    fun toPascalCase(input: String): String {
        return words(input).joinToString("") {
            it.lowercase(Locale.ROOT).replaceFirstChar { ch -> ch.uppercaseChar() }
        }
    }
}
