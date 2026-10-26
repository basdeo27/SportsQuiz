package com.elliotmoose.Sports.Quiz.account

class AccountNotFoundException(id: String) : RuntimeException("Account not found: $id")
class AccountAlreadyExistsException(identifier: String) : RuntimeException("An account already exists with that email address.")
class InvalidCredentialsException : RuntimeException("Invalid email or password.")

class EmailVerificationRequiredException : RuntimeException("Verify your email address before logging in. You can request a new verification email.")
class InvalidAccountRequestException(message: String) : RuntimeException(message)
class VerificationDeliveryException : RuntimeException("Verification email could not be sent. Please try resending it shortly.")
