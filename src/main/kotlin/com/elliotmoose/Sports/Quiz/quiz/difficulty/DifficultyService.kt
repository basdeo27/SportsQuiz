package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.quiz.model.Question
import com.elliotmoose.Sports.Quiz.quiz.model.QuizDifficulty
import org.springframework.stereotype.Service

@Service
class DifficultyService(
    handlers: List<QuizDifficultyHandler>
) {
    private val handlersByDifficulty = QuizDifficulty.entries.associateWith { difficulty ->
        handlers.singleOrNull { it.supports(difficulty) }
            ?: throw IllegalStateException("No difficulty handler registered for $difficulty")
    }

    fun evaluateAnswer(
        difficulty: QuizDifficulty,
        question: Question,
        submittedAnswer: String,
        attemptsUsed: Int
    ): DifficultyResult {
        return requireNotNull(handlersByDifficulty[difficulty]).evaluateAnswer(
            question = question,
            submittedAnswer = submittedAnswer,
            attemptsUsed = attemptsUsed
        )
    }
}
