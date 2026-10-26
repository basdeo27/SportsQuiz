package com.elliotmoose.Sports.Quiz.quiz

import com.elliotmoose.Sports.Quiz.quiz.model.*
import jakarta.validation.Valid
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentityProvider
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/quiz")
class QuizController(private val quizService: QuizService, private val identities: UserIdentityProvider) {

    @PostMapping
    fun createQuiz(@Valid @RequestBody req: QuizRequest, @RequestHeader("Authorization", required = false) authorization: String?) : QuizResponse {
        val quiz = quizService.createQuiz(req.copy(accountId = identities.resolve(authorization)?.id))
        return QuizResponse.from(quiz)
    }

    @PostMapping("/answer")
    fun submitAnswer(@Valid @RequestBody req: AnswerRequest, @RequestHeader("Authorization", required = false) authorization: String?): AnswerResponse {
        quizService.requireOwner(req.quizId, identities.resolve(authorization)?.id)
        return quizService.submitAnswer(req)
    }

    @PostMapping("/skip")
    fun skipQuestion(@Valid @RequestBody req: SkipRequest, @RequestHeader("Authorization", required = false) authorization: String?): SkipResponse {
        quizService.requireOwner(req.quizId, identities.resolve(authorization)?.id)
        return quizService.skipQuestion(req)
    }

    @GetMapping("/teams")
    fun getTeams(@RequestParam(required = false) leagues: Set<League>?): List<FaceTeamOption> {
        return quizService.getFaceTeamOptions(leagues ?: emptySet())
    }

    @GetMapping("/leagues")
    fun getAvailableLeagues(): Map<QuizType, List<League>> {
        return quizService.getAvailableLeagues()
    }

    @GetMapping("/{quizId}")
    fun getQuiz(@PathVariable quizId: String, @RequestHeader("Authorization", required = false) authorization: String?): QuizReviewResponse {
        quizService.requireOwner(quizId, identities.resolve(authorization)?.id)
        return quizService.getQuiz(quizId)
    }
}
