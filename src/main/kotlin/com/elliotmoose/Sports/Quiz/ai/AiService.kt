package com.elliotmoose.Sports.Quiz.ai

import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryRequest
import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizService
import com.elliotmoose.Sports.Quiz.quiz.model.QuizReviewQuestion
import com.elliotmoose.Sports.Quiz.quiz.model.QuizReviewResponse
import org.springframework.stereotype.Service

@Service
class AiService(
    private val quizService: QuizService,
    private val aiSupplier: AiSupplier
) {

    fun generateQuizSummary(request: QuizSummaryRequest): QuizSummaryResponse {
        val review = quizService.getQuiz(request.quizId)
        val prompt = buildPrompt(review)
        val summary = aiSupplier.complete(prompt)
        return QuizSummaryResponse(summary = summary)
    }

    private fun buildPrompt(review: QuizReviewResponse): String {
        val pct = if (review.totalQuestions > 0)
            (review.correctCount * 100) / review.totalQuestions else 0

        val wrongAnswers = review.questions
            .filter { it.correct == false && it.submittedAnswer != null }
            .take(3)

        val skippedNames = review.questions
            .filter { it.skipped == true }
            .map { it.fullName }
            .take(3)

        val hardCorrect = review.questions
            .filter { it.correct == true && !it.hinted }
            .map { it.fullName }
            .take(2)

        return buildString {
            appendLine("You are a witty sports commentator roasting (but affectionately) a quiz contestant.")
            appendLine("Keep it to 2-3 sentences. Be funny, playful, and a little cheeky.")
            appendLine()
            appendLine("Quiz results:")
            appendLine("- Score: ${review.score} points ($pct% correct — ${review.correctCount}/${review.totalQuestions})")
            appendLine("- Difficulty: ${review.difficulty}")
            appendLine("- Time taken: ${review.elapsedSeconds} seconds")

            if (review.skippedCount > 0) {
                appendLine("- Skipped: ${review.skippedCount} question(s)")
            }
            if (review.incorrectCount > 0) {
                appendLine("- Wrong answers: ${review.incorrectCount}")
            }

            if (wrongAnswers.isNotEmpty()) {
                appendLine()
                appendLine("Some particularly embarrassing wrong answers:")
                wrongAnswers.forEach { q ->
                    appendLine("  - They answered \"${q.submittedAnswer}\" but the correct answer was \"${q.fullName}\"")
                }
            }

            if (skippedNames.isNotEmpty()) {
                appendLine()
                appendLine("Questions they couldn't even attempt:")
                skippedNames.forEach { name -> appendLine("  - $name") }
            }

            if (hardCorrect.isNotEmpty()) {
                appendLine()
                appendLine("Questions they actually got right (give credit where it's due):")
                hardCorrect.forEach { name -> appendLine("  - $name") }
            }

            appendLine()
            appendLine("Write a short, funny 2-3 sentence roast/summary of their performance. " +
                    "If they did well (80%+), be impressively enthusiastic. " +
                    "If they scored under 50%, be playfully brutal. " +
                    "Reference specific answers if available. Do not use hashtags or emojis.")
        }
    }
}
