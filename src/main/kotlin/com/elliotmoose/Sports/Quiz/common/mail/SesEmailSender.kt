package com.elliotmoose.Sports.Quiz.common.mail

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.*

@Configuration
@EnableConfigurationProperties(EmailProperties::class)
@ConditionalOnProperty(prefix = "quiz.mail", name = ["mode"], havingValue = "ses", matchIfMissing = true)
class SesEmailConfiguration {
    @Bean(destroyMethod = "close")
    fun sesClient(properties: EmailProperties): SesV2Client = SesV2Client.builder().region(Region.of(properties.region)).build()

    @Bean
    fun emailSender(client: SesV2Client, properties: EmailProperties): EmailSender = object : EmailSender {
        override fun send(message: EmailMessage) {
            client.sendEmail(SendEmailRequest.builder().fromEmailAddress(properties.from)
                .destination { it.toAddresses(message.to) }
                .content(EmailContent.builder().simple(Message.builder()
                    .subject(Content.builder().data(message.subject).charset("UTF-8").build())
                    .body(Body.builder().text(Content.builder().data(message.text).charset("UTF-8").build()).build())
                    .build()).build()).build())
        }
    }
}
