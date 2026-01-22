package com.elliotmoose.Sports.Quiz.api

import com.elliotmoose.Sports.Quiz.quiz.AnswerRequest
import com.elliotmoose.Sports.Quiz.quiz.AnswerResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizRequest
import com.elliotmoose.Sports.Quiz.quiz.QuizResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizReviewResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizService
import com.elliotmoose.Sports.Quiz.quiz.HintRequest
import com.elliotmoose.Sports.Quiz.quiz.HintResponse
import com.elliotmoose.Sports.Quiz.quiz.SkipRequest
import com.elliotmoose.Sports.Quiz.quiz.SkipResponse
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

    @GetMapping("/{quizId}")
    fun getQuiz(@PathVariable quizId: String): QuizReviewResponse {
        return quizService.getQuiz(quizId)
    }
}
