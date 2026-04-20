package com.elliotmoose.Sports.Quiz.ai

interface AiSupplier {
    fun complete(prompt: String): String
}
