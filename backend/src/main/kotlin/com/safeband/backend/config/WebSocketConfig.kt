package com.safeband.backend.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SafeBandWebSocketHandler : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(SafeBandWebSocketHandler::class.java)

    // Maps parentUserId -> active WebSocket sessions
    private val userSessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()
    private val allSessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        allSessions.add(session)
        val query = session.uri?.query ?: ""
        val userId = extractParam(query, "userId")
        if (!userId.isNullOrBlank()) {
            userSessions.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(session)
            log.info("WebSocket connected for user: $userId (Session: ${session.id})")
        } else {
            log.info("WebSocket connected anonymously (Session: ${session.id})")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        allSessions.remove(session)
        val query = session.uri?.query ?: ""
        val userId = extractParam(query, "userId")
        if (!userId.isNullOrBlank()) {
            userSessions[userId]?.remove(session)
        }
        log.info("WebSocket closed: ${session.id}, status: $status")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Echo / Ping-Pong heartbeat
        if (message.payload.contains("PING", ignoreCase = true)) {
            session.sendMessage(TextMessage("{\"type\":\"PONG\",\"timestamp\":${System.currentTimeMillis()}}"))
        }
    }

    fun broadcastToUser(userId: String, jsonPayload: String) {
        val sessions = userSessions[userId] ?: return
        val msg = TextMessage(jsonPayload)
        sessions.forEach { sess ->
            if (sess.isOpen) {
                try {
                    synchronized(sess) {
                        sess.sendMessage(msg)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to send WS message to session ${sess.id}: ${e.message}")
                }
            }
        }
    }

    fun broadcastToAll(jsonPayload: String) {
        val msg = TextMessage(jsonPayload)
        allSessions.forEach { sess ->
            if (sess.isOpen) {
                try {
                    synchronized(sess) {
                        sess.sendMessage(msg)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to broadcast WS message: ${e.message}")
                }
            }
        }
    }

    private fun extractParam(query: String, name: String): String? {
        return query.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val webSocketHandler: SafeBandWebSocketHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(webSocketHandler, "/ws/parent")
            .setAllowedOrigins("*")
    }
}
