package com.safeband.backend.config

import com.safeband.backend.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public Health & Info
                    .requestMatchers("/api/v1/health/**", "/actuator/**").permitAll()
                    // Public Auth Endpoints
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    // Public / Bystander Relay Mesh Observation submission
                    .requestMatchers(HttpMethod.POST, "/api/v1/relay/observations").permitAll()
                    // Public Device Configuration Retrieval for initial BLE provisioning handshake
                    .requestMatchers(HttpMethod.GET, "/api/v1/devices/*/configuration").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/devices/*/configuration/ack").permitAll()
                    // Child / Device location upload
                    .requestMatchers(HttpMethod.POST, "/api/v1/locations", "/api/v1/locations/batch").permitAll()
                    // Emergency Incidents upload (can be triggered by child phone or device)
                    .requestMatchers(HttpMethod.POST, "/api/v1/incidents", "/api/v1/geofences/incidents/exit").permitAll()
                    // WebSocket Handshake
                    .requestMatchers("/ws/**").permitAll()
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
