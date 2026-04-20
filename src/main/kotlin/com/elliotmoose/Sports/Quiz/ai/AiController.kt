package com.elliotmoose.Sports.Quiz.ai

import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryRequest
import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v0/ai")
class AiController(
    private val aiService: AiService
) {

    @PostMapping("/quiz-summary")
    fun getQuizSummary(@RequestBody request: QuizSummaryRequest): QuizSummaryResponse {
        return aiService.generateQuizSummary(request)
    }
}
