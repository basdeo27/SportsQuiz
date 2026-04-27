package com.elliotmoose.Sports.Quiz.account

class AccountNotFoundException(id: String) : RuntimeException("Account not found: $id")
class AccountAlreadyExistsException(username: String) : RuntimeException("Username already taken: $username")
class InvalidCredentialsException : RuntimeException("Invalid username or password.")
