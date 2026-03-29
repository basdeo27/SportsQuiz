package com.elliotmoose.Sports.Quiz.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(private val corsProperties: QuizCorsProperties) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        if (corsProperties.allowedOrigins.isEmpty()) {
            return
        }
        registry.addMapping("/**")
            .allowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600)
    }
}
