package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import org.springframework.stereotype.Component

@Component
class MediumDifficultyHandler(
    private val answerMatcher: QuizAnswerMatcher
) : QuizDifficultyHandler {

    override fun supports(difficulty: QuizDifficulty): Boolean {
        return difficulty == QuizDifficulty.MEDIUM
    }

    override fun evaluateAnswer(
        question: Question,
        submittedAnswer: String,
        attemptsUsed: Int
    ): DifficultyResult {
        val matchedAnswer = answerMatcher.findExactNormalizedMatch(
            correctAnswers = question.correctAnswers,
            submittedAnswer = submittedAnswer
        )
        return DifficultyResult(
            matchedAnswer = matchedAnswer,
            attemptsRemaining = 0,
            shouldAdvance = true
        )
    }
}
