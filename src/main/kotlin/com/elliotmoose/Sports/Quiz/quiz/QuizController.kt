package com.elliotmoose.Sports.Quiz.quiz

import com.elliotmoose.Sports.Quiz.model.*
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/quiz")
class QuizController(private val quizService: QuizService) {

    @PostMapping
    fun createQuiz(@Valid @RequestBody req: QuizRequest) : QuizResponse {
        val quiz = quizService.createQuiz(req)
        return QuizResponse.from(quiz)
    }

    @PostMapping("/answer")
    fun submitAnswer(@Valid @RequestBody req: AnswerRequest): AnswerResponse {
        return quizService.submitAnswer(req)
    }

    @PostMapping("/skip")
    fun skipQuestion(@Valid @RequestBody req: SkipRequest): SkipResponse {
        return quizService.skipQuestion(req)
    }

    @GetMapping("/face-teams")
    fun getFaceTeams(@RequestParam(required = false) leagues: Set<League>?): List<FaceTeamOption> {
        return quizService.getFaceTeamOptions(leagues ?: emptySet())
    }

    @GetMapping("/{quizId}")
    fun getQuiz(@PathVariable quizId: String): QuizReviewResponse {
        return quizService.getQuiz(quizId)
    }
}
