package com.elliotmoose.Sports.Quiz.quiz

import com.elliotmoose.Sports.Quiz.model.*
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizSettingsProperties
import org.springframework.stereotype.Service

@Service
class QuizRequestValidationService(
    private val settings: QuizSettingsProperties
) {

    fun validateCreateQuizRequest(
        quizRequest: QuizRequest,
        availableQuestions: List<Question>
    ) {
        validateSelection(quizRequest)
        validateDisabledLeagues(quizRequest.leagues)
        validateQuestionCount(quizRequest.numberOfQuestions)
        if (availableQuestions.size < quizRequest.numberOfQuestions) {
            throw InvalidQuizRequestException("Not enough questions for the selected leagues.")
        }
    }

    fun validateFaceTeamOptionsRequest(leagues: Set<League>) {
        validateDisabledLeagues(leagues)
    }

    fun validateQuizExists(quizId: String, quiz: Quiz?): Quiz =
        quiz ?: throw QuizNotFoundException(quizId)

    fun validateQuestionExists(questionId: String, question: Question?): Question =
        question ?: throw QuestionNotFoundException(questionId)

    private fun validateSelection(quizRequest: QuizRequest) {
        val isValid = when (quizRequest.type) {
            QuizType.LOGO -> quizRequest.leagues.isNotEmpty()
            QuizType.FACE -> quizRequest.leagues.isNotEmpty() || quizRequest.teamIds.isNotEmpty()
        }
        if (!isValid) {
            val message = when (quizRequest.type) {
                QuizType.FACE -> "At least one league or team must be selected."
                QuizType.LOGO -> "At least one league must be selected."
            }
            throw InvalidQuizRequestException(message)
        }
    }

    private fun validateDisabledLeagues(leagues: Set<League>) {
        if (leagues.intersect(settings.disabledLeagues).isNotEmpty()) {
            throw InvalidQuizRequestException("One or more selected leagues are currently disabled.")
        }
    }

    private fun validateQuestionCount(numberOfQuestions: Int) {
        if (numberOfQuestions !in settings.minQuestions..settings.maxQuestions) {
            throw InvalidQuizRequestException("numberOfQuestions must be between ${settings.minQuestions} and ${settings.maxQuestions}.")
        }
    }
}
