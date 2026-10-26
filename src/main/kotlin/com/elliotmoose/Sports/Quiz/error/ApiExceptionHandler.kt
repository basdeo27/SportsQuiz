package com.elliotmoose.Sports.Quiz.error

import com.elliotmoose.Sports.Quiz.account.AccountAlreadyExistsException
import com.elliotmoose.Sports.Quiz.account.EmailVerificationRequiredException
import com.elliotmoose.Sports.Quiz.account.InvalidAccountRequestException
import com.elliotmoose.Sports.Quiz.account.VerificationDeliveryException
import com.elliotmoose.Sports.Quiz.account.AccountNotFoundException
import com.elliotmoose.Sports.Quiz.account.InvalidCredentialsException
import com.elliotmoose.Sports.Quiz.daily.DailyNotFoundException
import com.elliotmoose.Sports.Quiz.daily.DailySessionNotFoundException
import com.elliotmoose.Sports.Quiz.daily.DailySessionCompletedException
import com.elliotmoose.Sports.Quiz.daily.NoMoreHintsException
import com.elliotmoose.Sports.Quiz.daily.InvalidDailyRequestException
import com.elliotmoose.Sports.Quiz.daily.DailyScoreConflictException
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
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

    @ExceptionHandler(InvalidDailyRequestException::class, com.elliotmoose.Sports.Quiz.results.InvalidResultsRequestException::class)
    fun handleInvalidDailyRequest(ex: RuntimeException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Invalid daily request."), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun handleMalformedRequest(ex: Exception): ResponseEntity<ApiError> =
        ResponseEntity(ApiError("Invalid request format."), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(AuthenticationRequiredException::class)
    fun handleAuthenticationRequired(ex: AuthenticationRequiredException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Please log in."), HttpStatus.UNAUTHORIZED)

    @ExceptionHandler(DailyScoreConflictException::class)
    fun handleDailyScoreConflict(ex: DailyScoreConflictException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Score already submitted."), HttpStatus.CONFLICT)

    @ExceptionHandler(EmailVerificationRequiredException::class)
    fun handleUnverifiedEmail(ex: EmailVerificationRequiredException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Verify your email before logging in."), HttpStatus.FORBIDDEN)

    @ExceptionHandler(InvalidAccountRequestException::class)
    fun handleInvalidAccountRequest(ex: InvalidAccountRequestException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Invalid account request."), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(VerificationDeliveryException::class)
    fun handleVerificationDelivery(ex: VerificationDeliveryException): ResponseEntity<ApiError> =
        ResponseEntity(ApiError(ex.message ?: "Email delivery unavailable."), HttpStatus.SERVICE_UNAVAILABLE)

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

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Account not found."), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(AccountAlreadyExistsException::class)
    fun handleAccountAlreadyExists(ex: AccountAlreadyExistsException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Username already taken."), HttpStatus.CONFLICT)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Invalid credentials."), HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(DailyNotFoundException::class, DailySessionNotFoundException::class)
    fun handleDailyNotFound(ex: RuntimeException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Not found."), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(DailySessionCompletedException::class)
    fun handleDailySessionCompleted(ex: DailySessionCompletedException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Session already ended."), HttpStatus.CONFLICT)
    }

    @ExceptionHandler(NoMoreHintsException::class)
    fun handleNoMoreHints(ex: NoMoreHintsException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "No more hints available."), HttpStatus.UNPROCESSABLE_ENTITY)
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
