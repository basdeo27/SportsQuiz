package com.elliotmoose.Sports.Quiz.model

object HintUtils {

    fun resolveLogoHints(
        fullName: String,
        hints: Map<QuizDifficulty, List<String>>,
        legacyHint: String? = null
    ): Map<QuizDifficulty, List<String>> {
        val fallbackHints = buildLogoHints(fullName)
        if (hints.isEmpty()) {
            return if (legacyHint.isNullOrBlank()) fallbackHints else fallbackHints
        }
        return QuizDifficulty.entries.associateWith { difficulty ->
            hints[difficulty]
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackHints.getValue(difficulty)
        }
    }

    fun buildLogoHints(fullName: String): Map<QuizDifficulty, List<String>> {
        val tokens = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return QuizDifficulty.entries.associateWith { listOf("No hint available.") }
        }

        val location = if (tokens.size > 1) tokens.dropLast(1).joinToString(" ") else tokens[0]
        val nickname = tokens.last()
        val nicknameInitial = nickname.firstOrNull()?.uppercaseChar() ?: '?'
        val nicknameLength = nickname.count { it.isLetterOrDigit() }
        val wordCount = tokens.size

        return mapOf(
            QuizDifficulty.EASY to listOf(
                "This team plays as $location, and the nickname starts with $nicknameInitial."
            ),
            QuizDifficulty.MEDIUM to listOf(
                "The nickname starts with $nicknameInitial and is $nicknameLength characters long."
            ),
            QuizDifficulty.HARD to listOf(
                "The full team name is $wordCount words long."
            )
        )
    }

    fun buildFaceHints(team: String): Map<QuizDifficulty, List<String>> {
        val trimmedTeam = team.trim().ifBlank { "Unknown team" }
        return mapOf(
            QuizDifficulty.EASY to listOf("This player is on the $trimmedTeam."),
            QuizDifficulty.MEDIUM to listOf("The player’s current team is $trimmedTeam."),
            QuizDifficulty.HARD to listOf("Think about the $trimmedTeam roster.")
        )
    }
}
