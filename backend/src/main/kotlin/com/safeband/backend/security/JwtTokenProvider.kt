package com.safeband.backend.security

import com.safeband.backend.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

data class UserPrincipal(
    val id: String,
    val email: String,
    val role: String
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_$role"))
    }
    override fun getPassword(): String = ""
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: String, email: String, role: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenExpirationMs)
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .claim("type", "ACCESS")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun generateRefreshToken(userId: String): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.refreshTokenExpirationMs)
        return Jwts.builder()
            .subject(userId)
            .claim("type", "REFRESH")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (_: Exception) {
            false
        }
    }

    fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun getUserIdFromToken(token: String): String {
        return getClaims(token).subject
    }

    fun getUserPrincipal(token: String): UserPrincipal {
        val claims = getClaims(token)
        val userId = claims.subject
        val email = claims.get("email", String::class.java) ?: ""
        val role = claims.get("role", String::class.java) ?: "PARENT"
        return UserPrincipal(id = userId, email = email, role = role)
    }
}
