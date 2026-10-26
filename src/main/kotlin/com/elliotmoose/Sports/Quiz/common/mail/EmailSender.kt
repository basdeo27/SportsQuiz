package com.elliotmoose.Sports.Quiz.common.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

data class EmailMessage(val to: String, val subject: String, val text: String)
interface EmailSender { fun send(message: EmailMessage) }

@ConfigurationProperties("quiz.mail")
data class EmailProperties(val mode: String = "ses", val from: String = "noreply@funsportsquiz.com", val region: String = "eu-west-2")

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "quiz.mail", name = ["mode"], havingValue = "local")
@EnableConfigurationProperties(EmailProperties::class)
class LocalEmailSender : EmailSender {
    private val messages = ConcurrentHashMap<String, EmailMessage>()
    override fun send(message: EmailMessage) { messages[message.to.lowercase()] = message }
    fun latest(email: String): EmailMessage? = messages[email.lowercase()]
}
