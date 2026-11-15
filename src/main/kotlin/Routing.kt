package com.anasdev

import com.anasdev.data.repository.GameRepository
import com.anasdev.data.repository.OrderRepository
import com.anasdev.data.repository.UserRepository
import com.anasdev.domain.model.GameResponse
import com.anasdev.representation.routes.adminRoutes
import com.anasdev.representation.routes.authRoutes
import com.anasdev.representation.routes.orderRoutes
import com.anasdev.util.startImageCleanupJob
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.logging.FileHandler


fun Application.configureRouting() {
    val gameRepository = GameRepository()
    val userRepository = UserRepository()
    val orderRepository = OrderRepository()
    val googleWebClientId = environment.config.property("google.webClientId").getString()
    startImageCleanupJob(gameRepository)

    routing {
        authRoutes(userRepository,googleWebClientId)
        adminRoutes(gameRepository)
        orderRoutes(orderRepository)
        staticFiles("/uploads", File("uploads"))

        get("/games") {
            val gameList = gameRepository.getAllActiveGames()
            call.respond(gameList)
        }
    }
}