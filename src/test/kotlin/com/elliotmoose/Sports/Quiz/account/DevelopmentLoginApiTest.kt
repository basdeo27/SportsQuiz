package com.elliotmoose.Sports.Quiz.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["quiz.storage=local", "rate-limit.auth.enabled=false"])
@AutoConfigureMockMvc
class DevelopmentLoginApiTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `local user user login still works without email verification`() {
        val response = mvc.perform(post("/v0/account/login").header("X-Session-Mode", "cookie")
            .contentType(MediaType.APPLICATION_JSON).content("""{"email":"user","password":"user"}"""))
            .andExpect(status().isOk).andReturn().response

        assertThat(response.contentAsString).contains("Dev User", "\"emailVerified\":true")
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure;")
    }
}
