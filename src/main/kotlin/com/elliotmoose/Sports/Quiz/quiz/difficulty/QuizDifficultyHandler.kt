package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.quiz.model.Question
import com.elliotmoose.Sports.Quiz.quiz.model.QuizDifficulty

interface QuizDifficultyHandler {
    fun supports(difficulty: QuizDifficulty): Boolean

    fun evaluateAnswer(
        question: Question,
        submittedAnswer: String,
        attemptsUsed: Int
    ): DifficultyResult
}
