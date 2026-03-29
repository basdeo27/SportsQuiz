package com.elliotmoose.Sports.Quiz.api

import com.elliotmoose.Sports.Quiz.model.*
import com.elliotmoose.Sports.Quiz.service.QuizService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/quiz")
class SportsQuizController(val quizService: QuizService) {

    @PostMapping
    fun createQuiz(@RequestBody req: QuizRequest) : QuizResponse {
        val quiz = quizService.createQuiz(req)
        return QuizResponse.from(quiz)
    }

    @PostMapping("/answer")
    fun submitAnswer(@RequestBody req: AnswerRequest): AnswerResponse {
        return quizService.submitAnswer(req)
    }

    @PostMapping("/skip")
    fun skipQuestion(@RequestBody req: SkipRequest): SkipResponse {
        return quizService.skipQuestion(req)
    }

    @GetMapping("/results")
    fun getResults(): QuizResultsResponse {
        return QuizResultsResponse(quizService.getResults())
    }

    @GetMapping("/{quizId}")
    fun getQuiz(@PathVariable quizId: String): QuizReviewResponse {
        return quizService.getQuiz(quizId)
    }
}
