package com.elliotmoose.Sports.Quiz.quiz

import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import org.springframework.stereotype.Service

@Service
class QuizService(private val quizRepository: QuizRepository) {

    private val quizzes = ConcurrentHashMap<String, Quiz>()
    private val attemptsByQuiz = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val secureRandom = SecureRandom()

    fun createQuiz(quizRequest: QuizRequest): Quiz {
        require(quizRequest.numberOfQuestions in 10..25) {
            "numberOfQuestions must be between 10 and 25."
        }
        require(quizRequest.leagues.isNotEmpty()) {
            "At least one league must be selected."
        }

        val availableQuestions = quizRepository.getQuestions(quizRequest.leagues)
        require(availableQuestions.size >= quizRequest.numberOfQuestions) {
            "Not enough questions for the selected leagues."
        }

        val shuffledQuestions = availableQuestions.toMutableList()
        Collections.shuffle(shuffledQuestions, secureRandom)
        val quizQuestions = shuffledQuestions.take(quizRequest.numberOfQuestions)
        val quiz = Quiz(
            id = UUID.randomUUID().toString(),
            difficulty = quizRequest.difficulty,
            questions = quizQuestions
        )
        quizzes[quiz.id] = quiz
        attemptsByQuiz[quiz.id] = ConcurrentHashMap()
        return quiz
    }

    fun submitAnswer(answerRequest: AnswerRequest): AnswerResponse {
        val quiz = quizzes[answerRequest.quizId]
            ?: throw IllegalArgumentException("Quiz not found.")
        val question = quiz.questions.firstOrNull { it.id == answerRequest.questionId }
            ?: throw IllegalArgumentException("Question not found.")

        val attemptsForQuiz = attemptsByQuiz[quiz.id]
            ?: throw IllegalArgumentException("Quiz not found.")
        val attemptsUsed = attemptsForQuiz.merge(question.id, 1) { existing, _ -> existing + 1 } ?: 1

        val normalizedAnswer = normalize(answerRequest.answer)
        val matchedAnswer = when (quiz.difficulty) {
            QuizDifficulty.EASY -> question.correctAnswers.firstOrNull { candidate ->
                isAnswerMatch(normalizedAnswer, candidate)
            }
            QuizDifficulty.MEDIUM -> question.correctAnswers.firstOrNull { candidate ->
                normalize(candidate) == normalizedAnswer
            }
            QuizDifficulty.HARD -> {
                val normalizedFullName = normalize(question.fullName)
                if (normalizedFullName == normalizedAnswer) {
                    question.fullName
                } else {
                    null
                }
            }
        }

        val isCorrect = matchedAnswer != null
        val attemptsRemaining = when (quiz.difficulty) {
            QuizDifficulty.EASY -> max(0, 2 - attemptsUsed)
            else -> 0
        }
        val shouldAdvance = when (quiz.difficulty) {
            QuizDifficulty.EASY -> isCorrect || attemptsUsed >= 2
            else -> true
        }

        val updatedQuestions = quiz.questions.map { questionItem ->
            if (questionItem.id == question.id) {
                questionItem.copy(
                    submittedAnswer = answerRequest.answer,
                    isCorrect = isCorrect,
                    isSkipped = false
                )
            } else {
                questionItem
            }
        }
        quizzes[quiz.id] = quiz.copy(questions = updatedQuestions)

        return AnswerResponse(
            correct = isCorrect,
            normalizedAnswer = normalizedAnswer,
            matchedAnswer = matchedAnswer,
            correctAnswer = question.fullName,
            attemptsRemaining = attemptsRemaining,
            shouldAdvance = shouldAdvance
        )
    }

    fun getQuiz(quizId: String): QuizReviewResponse {
        val quiz = quizzes[quizId]
            ?: throw IllegalArgumentException("Quiz not found.")
        return QuizReviewResponse.from(quiz)
    }

    fun skipQuestion(skipRequest: SkipRequest): SkipResponse {
        val quiz = quizzes[skipRequest.quizId]
            ?: throw IllegalArgumentException("Quiz not found.")
        val question = quiz.questions.firstOrNull { it.id == skipRequest.questionId }
            ?: throw IllegalArgumentException("Question not found.")

        val updatedQuestions = quiz.questions.map { questionItem ->
            if (questionItem.id == question.id) {
                questionItem.copy(
                    submittedAnswer = null,
                    isCorrect = false,
                    isSkipped = true
                )
            } else {
                questionItem
            }
        }

        quizzes[quiz.id] = quiz.copy(questions = updatedQuestions)
        attemptsByQuiz[quiz.id]?.remove(question.id)

        return SkipResponse(
            skipped = true,
            correctAnswer = question.fullName
        )
    }

    private fun isAnswerMatch(normalizedAnswer: String, candidate: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate == normalizedAnswer) {
            return true
        }

        val similarity = similarityScore(normalizedAnswer, normalizedCandidate)
        if (similarity >= 0.8) {
            return true
        }

        val answerTokens = normalizedAnswer.split(" ").filter { it.isNotBlank() }
        val candidateTokens = normalizedCandidate.split(" ").filter { it.isNotBlank() }
        if (candidateTokens.isNotEmpty()) {
            val matchingTokens = candidateTokens.count { token -> answerTokens.contains(token) }
            val tokenMatchRatio = matchingTokens.toDouble() / candidateTokens.size
            if (tokenMatchRatio >= 0.75) {
                return true
            }
        }

        return false
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun similarityScore(a: String, b: String): Double {
        val maxLength = max(a.length, b.length)
        if (maxLength == 0) {
            return 1.0
        }
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLength
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) {
            dp[i][0] = i
        }
        for (j in 0..b.length) {
            dp[0][j] = j
        }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
