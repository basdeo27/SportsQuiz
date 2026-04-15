package com.elliotmoose.Sports.Quiz.util

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class PingController {

    @GetMapping("/ping")
    fun ping(): String {
        return "PONG"
    }
}
