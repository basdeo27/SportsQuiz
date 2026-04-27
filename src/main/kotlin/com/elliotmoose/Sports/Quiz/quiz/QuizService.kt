package com.elliotmoose.Sports.Quiz.quiz

import com.elliotmoose.Sports.Quiz.quiz.difficulty.DifficultyService
import com.elliotmoose.Sports.Quiz.quiz.model.*
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizSettingsProperties
import com.elliotmoose.Sports.Quiz.quiz.repository.QuestionRepository
import com.elliotmoose.Sports.Quiz.results.ResultsService
import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class QuizService(
    private val questionRepository: QuestionRepository,
    private val resultsService: ResultsService,
    private val difficultyService: DifficultyService,
    private val requestValidationService: QuizRequestValidationService,
    private val settings: QuizSettingsProperties
) {

    private val quizzes = ConcurrentHashMap<String, Quiz>()
    private val attemptsByQuiz = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val savedResults = ConcurrentHashMap.newKeySet<String>()
    private val secureRandom = SecureRandom()

    fun createQuiz(quizRequest: QuizRequest): Quiz {
        val availableQuestions = questionRepository.getQuestions(
            quizRequest.leagues,
            quizRequest.type,
            quizRequest.difficulty,
            quizRequest.teamIds
        )
        requestValidationService.validateCreateQuizRequest(quizRequest, availableQuestions)

        val shuffledQuestions = availableQuestions.toMutableList()
        Collections.shuffle(shuffledQuestions, secureRandom)
        val quizQuestions = shuffledQuestions.take(quizRequest.numberOfQuestions)
        val quiz = Quiz(
            type = quizRequest.type,
            difficulty = quizRequest.difficulty,
            questions = quizQuestions,
            accountId = quizRequest.accountId
        )
        quizzes[quiz.id] = quiz
        attemptsByQuiz[quiz.id] = ConcurrentHashMap()
        return quiz
    }

    fun getFaceTeamOptions(leagues: Set<League>): List<FaceTeamOption> {
        requestValidationService.validateFaceTeamOptionsRequest(leagues)
        return questionRepository.getFaceTeamOptions(leagues)
    }

    fun submitAnswer(answerRequest: AnswerRequest): AnswerResponse {
        val resolvedQuiz = requestValidationService.validateQuizExists(answerRequest.quizId, quizzes[answerRequest.quizId])
        val resolvedQuestion = requestValidationService.validateQuestionExists(
            answerRequest.questionId,
            resolvedQuiz.questions.firstOrNull { it.id == answerRequest.questionId }
        )

        val resolvedAttemptsForQuiz = checkNotNull(attemptsByQuiz[resolvedQuiz.id]) {
            "Invariant violated: no attempts tracker for quiz ${resolvedQuiz.id}"
        }
        val attemptsUsed = resolvedAttemptsForQuiz.merge(resolvedQuestion.id, 1) { existing, _ -> existing + 1 } ?: 1

        val normalizedAnswer = normalize(answerRequest.answer)
        val difficultyResult = difficultyService.evaluateAnswer(
            difficulty = resolvedQuiz.difficulty,
            question = resolvedQuestion,
            submittedAnswer = answerRequest.answer,
            attemptsUsed = attemptsUsed
        )
        val matchedAnswer = difficultyResult.matchedAnswer
        val isCorrect = matchedAnswer != null
        val attemptsRemaining = difficultyResult.attemptsRemaining
        val shouldAdvance = difficultyResult.shouldAdvance

        val updatedQuestions = if (shouldAdvance) {
            resolvedQuiz.questions.map { questionItem ->
                if (questionItem.id == resolvedQuestion.id) {
                questionItem.copy(
                    submittedAnswer = answerRequest.answer,
                    isCorrect = isCorrect,
                    isSkipped = false,
                    hinted = answerRequest.hintUsed
                )
                } else {
                    questionItem
                }
            }
        } else {
            resolvedQuiz.questions
        }
        val updatedQuiz = markCompletedIfDone(resolvedQuiz.copy(questions = updatedQuestions))
        quizzes[resolvedQuiz.id] = updatedQuiz

        return AnswerResponse(
            correct = isCorrect,
            normalizedAnswer = normalizedAnswer,
            matchedAnswer = matchedAnswer,
            correctAnswer = resolvedQuestion.fullName,
            attemptsRemaining = attemptsRemaining,
            shouldAdvance = shouldAdvance
        )
    }

    fun getQuiz(quizId: String): QuizReviewResponse {
        val quiz = requestValidationService.validateQuizExists(quizId, quizzes[quizId])
        return QuizReviewResponse.from(quiz)
    }

    fun skipQuestion(skipRequest: SkipRequest): SkipResponse {
        val resolvedQuiz = requestValidationService.validateQuizExists(skipRequest.quizId, quizzes[skipRequest.quizId])
        val resolvedQuestion = requestValidationService.validateQuestionExists(
            skipRequest.questionId,
            resolvedQuiz.questions.firstOrNull { it.id == skipRequest.questionId }
        )

        val updatedQuestions = resolvedQuiz.questions.map { questionItem ->
            if (questionItem.id == resolvedQuestion.id) {
                questionItem.copy(
                    submittedAnswer = null,
                    isCorrect = false,
                    isSkipped = true,
                    hinted = skipRequest.hintUsed
                )
            } else {
                questionItem
            }
        }

        quizzes[resolvedQuiz.id] = markCompletedIfDone(resolvedQuiz.copy(questions = updatedQuestions))
        attemptsByQuiz[resolvedQuiz.id]?.remove(resolvedQuestion.id)

        return SkipResponse(
            skipped = true,
            correctAnswer = resolvedQuestion.fullName
        )
    }

    private fun markCompletedIfDone(quiz: Quiz): Quiz {
        if (quiz.completedAtMillis != null) {
            if (savedResults.add(quiz.id)) {
                resultsService.saveResult(buildResult(quiz))
            }
            return quiz
        }
        val allDone = quiz.questions.all { question ->
            question.isSkipped == true || question.isCorrect != null
        }
        return if (allDone) {
            val completedQuiz = quiz.copy(completedAtMillis = System.currentTimeMillis())
            if (savedResults.add(completedQuiz.id)) {
                resultsService.saveResult(buildResult(completedQuiz))
            }
            completedQuiz
        } else {
            quiz
        }
    }

    private fun buildResult(quiz: Quiz): QuizResult {
        val summary = QuizScoring.summarize(quiz)
        val leagues = quiz.questions.map { it.league }.toSet()
        return QuizResult(
            quizId = quiz.id,
            userId = quiz.accountId ?: SINGLE_USER_ID,
            quizType = quiz.type,
            difficulty = quiz.difficulty,
            leagues = leagues,
            totalQuestions = summary.totalQuestions,
            correctCount = summary.correctCount,
            incorrectCount = summary.incorrectCount,
            skippedCount = summary.skippedCount,
            elapsedSeconds = summary.elapsedSeconds,
            score = summary.score,
            completedAtMillis = quiz.completedAtMillis ?: System.currentTimeMillis()
        )
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun getAvailableLeagues(): Map<QuizType, List<League>> = mapOf(
        QuizType.LOGO to League.entries.filter { it !in settings.disabledLogoLeagues },
        QuizType.FACE to League.entries.filter { it !in settings.disabledFaceLeagues }
    )

    companion object {
        private const val SINGLE_USER_ID = "single-user"
    }
}
