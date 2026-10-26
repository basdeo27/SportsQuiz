package com.elliotmoose.Sports.Quiz.account.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.auth")
data class AuthenticationProperties(
    val requireEmailVerification: Boolean = true,
    val allowDevelopmentLogin: Boolean = false,
    val verificationMinutes: Long = 30,
    val frontendUrl: String = "https://funsportsquiz.com",
    val sessionsTableName: String = "sports-quiz-auth-sessions",
    val sessionHours: Long = 24,
    val cookieName: String = "sports_quiz_session",
    val secureCookie: Boolean = true,
    val sameSite: String = "None"
) {
    init {
        require(sessionHours in 1..720)
        require(sameSite in setOf("Lax", "Strict", "None"))
        require(secureCookie || sameSite != "None") { "SameSite=None requires a secure cookie." }
    }
}
