package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.QuizResult
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "quiz.results", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalResultRepository : ResultRepository {

    private val results = ConcurrentHashMap<String, QuizResult>()

    override fun saveResult(result: QuizResult) {
        results[result.quizId] = result
    }

    override fun getResults(): List<QuizResult> {
        return results.values.sortedByDescending { it.completedAtMillis }
    }
}
