package com.elliotmoose.Sports.Quiz.quiz.model

data class FaceTeamOption(
    val teamId: String,
    val teamName: String,
    val league: League,
    val playerCount: Int
)
