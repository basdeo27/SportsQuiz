package com.elliotmoose.Sports.Quiz.api

import com.elliotmoose.Sports.Quiz.quiz.AnswerRequest
import com.elliotmoose.Sports.Quiz.quiz.AnswerResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizRequest
import com.elliotmoose.Sports.Quiz.quiz.QuizResponse
import com.elliotmoose.Sports.Quiz.quiz.QuizService
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
}
