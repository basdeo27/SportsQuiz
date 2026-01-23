package com.elliotmoose.Sports.Quiz.model

data class QuizResponse(
    val quizId: String,
    val questions: List<QuizQuestion>
) {
    companion object {
        fun from(quiz: Quiz): QuizResponse {
            val questions = quiz.questions.map {
                QuizQuestion(
                    id = it.id,
                    league = it.league,
                    logoUrl = it.logoUrl,
                    fullName = it.fullName
                )
            }
            return QuizResponse(quiz.id, questions)
        }
    }
}
