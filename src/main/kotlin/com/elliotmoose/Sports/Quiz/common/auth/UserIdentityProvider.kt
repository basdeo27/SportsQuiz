package com.elliotmoose.Sports.Quiz.common.auth

data class UserIdentity(val id: String, val displayName: String)

interface UserIdentityProvider {
    fun resolve(authorization: String?): UserIdentity?
}

class AuthenticationRequiredException : RuntimeException("Please log in again to continue.")
