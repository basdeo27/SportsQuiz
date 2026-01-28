package com.elliotmoose.Sports.Quiz.api

import com.elliotmoose.Sports.Quiz.model.AnswerRequest
import com.elliotmoose.Sports.Quiz.model.AnswerResponse
import com.elliotmoose.Sports.Quiz.model.HintRequest
import com.elliotmoose.Sports.Quiz.model.HintResponse
import com.elliotmoose.Sports.Quiz.model.QuizRequest
import com.elliotmoose.Sports.Quiz.model.QuizResponse
import com.elliotmoose.Sports.Quiz.model.QuizReviewResponse
import com.elliotmoose.Sports.Quiz.model.QuizResultsResponse
import com.elliotmoose.Sports.Quiz.model.SkipRequest
import com.elliotmoose.Sports.Quiz.model.SkipResponse
import com.elliotmoose.Sports.Quiz.service.QuizService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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

    @PostMapping("/hint")
    fun hintQuestion(@RequestBody req: HintRequest): HintResponse {
        return quizService.hintQuestion(req)
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
