package com.elliotmoose.Sports.Quiz.model

data class FaceEntry(
    val id: String,
    val name: String,
    val team: String,
    val teamId: String,
    val headshotUrl: String,
    val answers: List<String> = emptyList(),
    val isAllStar: Boolean = false
)
