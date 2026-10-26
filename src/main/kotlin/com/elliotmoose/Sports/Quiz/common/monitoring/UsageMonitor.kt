package com.elliotmoose.Sports.Quiz.common.monitoring

enum class CompletionType { LOGO, FACE, DAILY_RANKED, DAILY_PRACTICE }
interface UsageMonitor {
    fun quizCompleted(id: String, type: CompletionType, completedAtMillis: Long)
    fun endpointError(method: String, route: String, status: Int)
}
