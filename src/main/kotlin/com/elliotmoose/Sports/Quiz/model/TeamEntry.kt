package com.elliotmoose.Sports.Quiz.model

data class TeamEntry(
    val id: String,
    val name: String,
    val logoUrl: String,
    val answers: List<String> = emptyList()
)
