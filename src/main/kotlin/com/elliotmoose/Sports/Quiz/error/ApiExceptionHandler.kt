package com.elliotmoose.Sports.Quiz.error

import com.elliotmoose.Sports.Quiz.quiz.InvalidQuizRequestException
import com.elliotmoose.Sports.Quiz.quiz.QuestionNotFoundException
import com.elliotmoose.Sports.Quiz.quiz.QuizNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(QuizNotFoundException::class, QuestionNotFoundException::class)
    fun handleNotFound(ex: RuntimeException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Not found."), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(InvalidQuizRequestException::class)
    fun handleBadRequest(ex: InvalidQuizRequestException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Invalid request."), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "Invalid request."
        return ResponseEntity(ApiError(firstError), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception: ${ex.message}", ex)
        return ResponseEntity(ApiError("An unexpected error occurred."), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}

data class ApiError(
    val message: String
)
