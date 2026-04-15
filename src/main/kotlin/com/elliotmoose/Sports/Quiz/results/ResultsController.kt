package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.results.model.QuizResultsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v0/quiz/results")
class ResultsController(
    private val resultsService: ResultsService
) {
    @GetMapping
    fun getResults(): QuizResultsResponse {
        return QuizResultsResponse(resultsService.getResults())
    }
}
