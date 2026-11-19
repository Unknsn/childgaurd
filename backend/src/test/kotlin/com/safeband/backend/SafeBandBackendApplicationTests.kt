package com.safeband.backend

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("standalone")
class SafeBandBackendApplicationTests {

    @Test
    fun contextLoads() {
        // Verifies Spring context, Flyway migration, and H2 database boot up cleanly
    }
}
