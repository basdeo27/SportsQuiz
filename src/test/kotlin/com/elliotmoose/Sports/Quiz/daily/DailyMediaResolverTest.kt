package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.security.MessageDigest

class DailyMediaResolverTest {
    private val path = "assets/daily/2026-10-25/hint-3.mp3"

    @Test
    fun `local development keeps backend media references`() {
        val hint = DailyHint.Audio(path, DailyHintSource.LOCAL)
        assertThat(DailyMediaResolver().resolve(hint)).isEqualTo(hint)
    }

    @Test
    fun `production uses the same full content hash as the S3 publisher`() {
        val bytes = ClassPathResource("static/$path").inputStream.use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val resolver = DailyMediaResolver("/media/daily/")
        val original = DailyHint.Audio(path, DailyHintSource.LOCAL)
        assertThat(resolver.resolve(original)).isEqualTo(DailyHint.Audio("/media/daily/$digest.mp3", DailyHintSource.REMOTE))
        assertThat(resolver.resolve(original)).isEqualTo(resolver.resolve(original))
        assertThat(original.url).isEqualTo(path)
        assertThat(resolver.resolve(DailyHint.Image(path, DailyHintSource.LOCAL)))
            .isEqualTo(DailyHint.Image("/media/daily/$digest.mp3", DailyHintSource.REMOTE))
        assertThat(DailyMediaResolver("https://cdn.example.test/daily").resolve(DailyHint.Video(path, DailyHintSource.LOCAL)))
            .isEqualTo(DailyHint.Video("https://cdn.example.test/daily/$digest.mp3", DailyHintSource.REMOTE))
    }

    @Test
    fun `remote hints and nonmedia clues are unchanged`() {
        val resolver = DailyMediaResolver("/media/daily")
        for (hint in listOf(DailyHint.Image("https://example.test/image.jpg"), DailyHint.Text("A clue"))) {
            assertThat(resolver.resolve(hint)).isEqualTo(hint)
        }
        assertThatThrownBy { resolver.resolve(DailyHint.Audio("assets/daily/../secret.mp3", DailyHintSource.LOCAL)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
