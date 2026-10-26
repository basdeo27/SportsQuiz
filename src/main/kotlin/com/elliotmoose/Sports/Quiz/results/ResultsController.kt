package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.results.model.QuizResultsResponse
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentityProvider
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v0/quiz/results")
class ResultsController(
    private val resultsService: ResultsService,
    private val identities: UserIdentityProvider
) {
    @GetMapping
    fun getResults(
        @RequestParam(required = false) userId: String?,
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) before: Long?
    ): QuizResultsResponse {
        val user = identities.resolve(authorization) ?: throw AuthenticationRequiredException()
        if (userId != null && userId != user.id) throw AuthenticationRequiredException()
        if (limit !in 1..100) throw com.elliotmoose.Sports.Quiz.results.InvalidResultsRequestException("Page size must be between 1 and 100.")
        return resultsService.getResults(user.id, limit, before)
    }
}
