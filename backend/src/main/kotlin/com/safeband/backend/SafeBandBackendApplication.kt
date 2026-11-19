package com.safeband.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SafeBandBackendApplication

fun main(args: Array<String>) {
    runApplication<SafeBandBackendApplication>(*args)
}
