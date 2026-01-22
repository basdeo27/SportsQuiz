package com.elliotmoose.Sports.Quiz.quiz

data class QuizReviewResponse(
    val quizId: String,
    val difficulty: QuizDifficulty,
    val questions: List<QuizReviewQuestion>
) {
    companion object {
        fun from(quiz: Quiz): QuizReviewResponse {
            val questions = quiz.questions.map { question ->
                QuizReviewQuestion(
                    id = question.id,
                    league = question.league,
                    logoUrl = question.logoUrl,
                    fullName = question.fullName,
                    submittedAnswer = question.submittedAnswer,
                    correct = question.isCorrect,
                    skipped = question.isSkipped
                )
            }
            return QuizReviewResponse(
                quizId = quiz.id,
                difficulty = quiz.difficulty,
                questions = questions
            )
        }
    }
}

data class QuizReviewQuestion(
    val id: String,
    val league: League,
    val logoUrl: String,
    val fullName: String,
    val submittedAnswer: String?,
    val correct: Boolean?,
    val skipped: Boolean?
)
