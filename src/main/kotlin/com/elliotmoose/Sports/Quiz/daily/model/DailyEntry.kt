package com.elliotmoose.Sports.Quiz.daily.model

data class DailyEntry(
    val date: String,
    val answer: String,
    val answers: List<String>,
    val hints: List<DailyHint>
)
