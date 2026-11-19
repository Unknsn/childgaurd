package com.safeband.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "safeband.jwt")
data class JwtProperties(
    var secret: String = "SafeBandGuardianMasterSecretKeyForHmacSha256MustBeAtLeast32BytesLong",
    var accessTokenExpirationMs: Long = 3600000L,
    var refreshTokenExpirationMs: Long = 2592000000L
)

@Configuration
@ConfigurationProperties(prefix = "safeband.google")
data class GoogleAuthProperties(
    var clientId: String = "104192847120-safeband-guardian.apps.googleusercontent.com"
)

@Configuration
@ConfigurationProperties(prefix = "safeband.firebase")
data class FirebaseProperties(
    var enabled: Boolean = false,
    var credentialsPath: String = ""
)

@Configuration
@ConfigurationProperties(prefix = "safeband.tunnel")
data class TunnelProperties(
    var publicUrl: String = "http://localhost:8080"
)
