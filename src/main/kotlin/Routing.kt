package com.anasdev

import com.anasdev.data.repository.GameRepository
import com.anasdev.data.repository.OrderRepository
import com.anasdev.data.repository.UserRepository
import com.anasdev.representation.routes.adminRoutes
import com.anasdev.representation.routes.authRoutes
import com.anasdev.representation.routes.orderRoutes
import com.anasdev.util.startImageCleanupJob
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File


fun Application.configureRouting() {
    val gameRepository = GameRepository()
    val userRepository = UserRepository()
    val orderRepository = OrderRepository()

    val googleWebClientId = environment.config.property("google.webClientId").getString()
    startImageCleanupJob(gameRepository)

    val supabaseUrl = environment.config.property("supabase.url").getString().trimEnd('/')
    val supabaseServiceKey = environment.config.property("supabase.serviceKey").getString()

    routing {
        authRoutes(userRepository,googleWebClientId)
        adminRoutes(gameRepository, supabaseUrl, supabaseServiceKey)
        orderRoutes(orderRepository)
        staticFiles("/uploads", File("uploads"))

        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        get("/games") {
            val gameList = gameRepository.getAllActiveGames()
            call.respond(gameList)
        }
    }
}