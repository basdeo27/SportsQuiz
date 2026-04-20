package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.quiz.model.Question
import com.elliotmoose.Sports.Quiz.quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import org.springframework.stereotype.Component

@Component
class HardDifficultyHandler(
    private val answerMatcher: QuizAnswerMatcher
) : QuizDifficultyHandler {

    override fun supports(difficulty: QuizDifficulty): Boolean {
        return difficulty == QuizDifficulty.HARD
    }

    override fun evaluateAnswer(
        question: Question,
        submittedAnswer: String,
        attemptsUsed: Int
    ): DifficultyResult {
        val matchedAnswer = if (answerMatcher.isExactFullNameMatch(question.fullName, submittedAnswer)) {
            question.fullName
        } else {
            null
        }
        return DifficultyResult(
            matchedAnswer = matchedAnswer,
            attemptsRemaining = 0,
            shouldAdvance = true
        )
    }
}
