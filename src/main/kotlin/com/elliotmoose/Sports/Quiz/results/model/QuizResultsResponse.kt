package com.elliotmoose.Sports.Quiz.results.model

data class QuizResultsResponse(
    val results: List<QuizResult>,
    val hasMore: Boolean
)
