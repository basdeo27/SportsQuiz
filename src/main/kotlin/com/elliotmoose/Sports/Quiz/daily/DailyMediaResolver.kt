package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.DailyHint
import com.elliotmoose.Sports.Quiz.daily.model.DailyHintSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Resolve only revealed hints; persisted attempts keep portable source references. */
@Component
class DailyMediaResolver(@Value("\${quiz.daily.media.base-url:}") private val baseUrl: String = "") {
    private val urls = ConcurrentHashMap<String, String>()

    init {
        require(baseUrl.isBlank() || (baseUrl.startsWith("/") && !baseUrl.startsWith("//")) || baseUrl.startsWith("https://")) {
            "Daily media base URL must be a same-origin path or HTTPS URL."
        }
    }

    fun resolve(hint: DailyHint): DailyHint {
        if (baseUrl.isBlank()) return hint
        return when (hint) {
            is DailyHint.Image -> if (hint.source == DailyHintSource.LOCAL) hint.copy(url = url(hint.url), source = DailyHintSource.REMOTE) else hint
            is DailyHint.Audio -> if (hint.source == DailyHintSource.LOCAL) hint.copy(url = url(hint.url), source = DailyHintSource.REMOTE) else hint
            is DailyHint.Video -> if (hint.source == DailyHintSource.LOCAL) hint.copy(url = url(hint.url), source = DailyHintSource.REMOTE) else hint
            else -> hint
        }
    }

    private fun url(path: String): String = urls.computeIfAbsent(path) {
        val relative = path.removePrefix("/")
        require(relative.startsWith("assets/daily/") && ".." !in relative.split('/')) { "Invalid daily media path." }
        val bytes = ClassPathResource("static/$relative").inputStream.use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        "${baseUrl.trimEnd('/')}/$digest.${relative.substringAfterLast('.').lowercase()}"
    }
}
