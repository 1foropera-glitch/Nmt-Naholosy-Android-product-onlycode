package com.example.util

object StressUtils {
    fun accessibleStress(plain: String, stressed: String): String {
        var vowel = 0
        val positions = plain.mapIndexedNotNull { index, char ->
            if (isVowel(char)) vowel++
            if (isVowel(char) && stressed.getOrNull(index)?.isUpperCase() == true) vowel else null
        }
        return "$plain. Наголос на голосній номер ${positions.joinToString(" або ")}."
    }
    const val UKRAINIAN_VOWELS = "аеєиіїоуюя"

    fun isVowel(char: Char): Boolean {
        return UKRAINIAN_VOWELS.contains(char.lowercaseChar())
    }

    fun renderWithStress(plain: String, positions: List<Int>): String {
        val chars = plain.lowercase().toCharArray()
        for (pos in positions) {
            if (pos in chars.indices) {
                chars[pos] = chars[pos].uppercaseChar()
            }
        }
        return String(chars)
    }

    /**
     * Generates multiple choices for a word, stressing different vowels.
     * Guaranteed to include the correct stressedWord as one of the options.
     */
    fun getAnswerVariants(plain: String, stressedWord: String): List<String> {
        val list = mutableListOf<String>()
        val lowerPlain = plain.lowercase()
        for (i in lowerPlain.indices) {
            if (isVowel(lowerPlain[i])) {
                list.add(renderWithStress(plain, listOf(i)))
            }
        }
        // Ensure the correct answer is always present in the generated list
        if (!list.contains(stressedWord)) {
            list.add(stressedWord)
        }
        return list.distinct()
    }

    /**
     * Normalizes a text string to match Ukrainian standards
     */
    fun normalizeWord(text: String): String {
        return text.trim()
            .lowercase()
            .replace("’", "'")
            .replace("ʼ", "'")
            .replace("`", "'")
    }
}
