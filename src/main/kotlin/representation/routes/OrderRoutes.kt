package com.anasdev.representation.routes

import com.anasdev.data.repository.OrderRepository
import com.anasdev.domain.model.LockKeyRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.orderRoutes(orderRepository: OrderRepository) {
    authenticate("auth-jwt") {
        route("/cart") {
            get{
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val items = orderRepository.getLockedCartItems(userId)
                call.respond(HttpStatusCode.OK,items)
            }

            post("/lock") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val request = call.receive<LockKeyRequest>()
                val gameUuid = runCatching {UUID.fromString(request.gameId)}.getOrNull()
                if (gameUuid == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid gameId format"))
                    return@post
                }

                val lockedItem = orderRepository.lockKeyForCart(userId, gameUuid)
                if (lockedItem == null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Game is out of stock or unavailable"))
                    return@post
                }

                call.respond(HttpStatusCode.OK,lockedItem)

            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val keyId = runCatching {UUID.fromString(call.parameters["id"])}.getOrNull()

                if (keyId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid key id format"))
                    return@delete
                }

                val removed = orderRepository.removeCartItem(userId, keyId)

                if (removed) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from cart"))
                    return@delete
                }
                else call.respond(HttpStatusCode.NotFound,mapOf("error" to "Cart item not found"))
            }

            post("/checkout") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                val receipt = orderRepository.checkout(userId)
                if (receipt == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cart is empty or reservation expired"))
                    return@post
                }
                call.respond(HttpStatusCode.OK,receipt)
            }


        }
        get("/orders") {
            val principal = call.principal<JWTPrincipal>()!!
            val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

            val orderHistory = orderRepository.getOrderHistory(userId)
            call.respond(HttpStatusCode.OK,orderHistory)
        }
    }
}

