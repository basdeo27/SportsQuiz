package com.elliotmoose.Sports.Quiz.quiz.model

data class QuizReviewResponse(
    val quizId: String,
    val difficulty: QuizDifficulty,
    val questions: List<QuizReviewQuestion>,
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val elapsedSeconds: Long,
    val score: Int
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
                    skipped = question.isSkipped,
                    hinted = question.hinted
                )
            }
            val summary = QuizScoring.summarize(quiz)
            return QuizReviewResponse(
                quizId = quiz.id,
                difficulty = quiz.difficulty,
                questions = questions,
                totalQuestions = summary.totalQuestions,
                correctCount = summary.correctCount,
                incorrectCount = summary.incorrectCount,
                skippedCount = summary.skippedCount,
                elapsedSeconds = summary.elapsedSeconds,
                score = summary.score
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
    val skipped: Boolean?,
    val hinted: Boolean
)
