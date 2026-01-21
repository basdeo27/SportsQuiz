package com.elliotmoose.Sports.Quiz.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        return ResponseEntity(ApiError(ex.message ?: "Invalid request."), HttpStatus.BAD_REQUEST)
    }
}

data class ApiError(
    val message: String
)
