package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import org.springframework.stereotype.Component
import kotlin.math.max

@Component
class EasyDifficultyHandler(
    private val answerMatcher: QuizAnswerMatcher
) : QuizDifficultyHandler {

    override fun supports(difficulty: QuizDifficulty): Boolean {
        return difficulty == QuizDifficulty.EASY
    }

    override fun evaluateAnswer(
        question: Question,
        submittedAnswer: String,
        attemptsUsed: Int
    ): DifficultyResult {
        val matchedAnswer = answerMatcher.findEasyMatch(
            correctAnswers = question.correctAnswers,
            submittedAnswer = submittedAnswer
        )
        val isCorrect = matchedAnswer != null
        return DifficultyResult(
            matchedAnswer = matchedAnswer,
            attemptsRemaining = max(0, 2 - attemptsUsed),
            shouldAdvance = isCorrect || attemptsUsed >= 2
        )
    }
}
