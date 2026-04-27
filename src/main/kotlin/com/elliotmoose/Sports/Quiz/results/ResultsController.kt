package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.results.model.QuizResultsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v0/quiz/results")
class ResultsController(
    private val resultsService: ResultsService
) {
    @GetMapping
    fun getResults(
        @RequestParam userId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) before: Long?
    ): QuizResultsResponse {
        return resultsService.getResults(userId, limit, before)
    }
}
