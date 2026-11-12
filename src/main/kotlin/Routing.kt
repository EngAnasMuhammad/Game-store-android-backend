package com.anasdev

import com.anasdev.data.repository.GameRepository
import com.anasdev.data.repository.OrderRepository
import com.anasdev.data.repository.UserRepository
import com.anasdev.domain.model.GameResponse
import com.anasdev.representation.routes.adminRoutes
import com.anasdev.representation.routes.authRoutes
import com.anasdev.representation.routes.orderRoutes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    val gameRepository = GameRepository()
    val userRepository = UserRepository()
    val orderRepository = OrderRepository()

    routing {
        authRoutes(userRepository)
        adminRoutes(gameRepository)
        orderRoutes(orderRepository)

        get("/games") {
            val gameList = gameRepository.getAllActiveGames()
            call.respond(gameList)
        }
    }
}