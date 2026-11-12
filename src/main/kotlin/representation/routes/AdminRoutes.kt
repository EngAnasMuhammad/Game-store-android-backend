package com.anasdev.representation.routes

import com.anasdev.data.repository.GameRepository
import com.anasdev.domain.model.AddKeyRequest
import com.anasdev.domain.model.CreateGameRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.adminRoutes(gameRepository: GameRepository) {
    authenticate("auth-jwt") {
        route("/admin") {

            post("/games") {
                val principal = call.principal<JWTPrincipal>()!!
                val userRole = principal.payload.getClaim("role").asString()

                if (userRole != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    return@post
                }

                val request = call.receive<CreateGameRequest>()
                val newGameId = runCatching {
                    gameRepository.createGame(request)
                }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid game data or date format (YYYY-MM-DD)"))
                    return@post
                }

                call.respond(HttpStatusCode.Created, mapOf("gameId" to newGameId))
            }

            post("/games/{id}/keys") {
                val principal = call.principal<JWTPrincipal>()!!
                val userRole = principal.payload.getClaim("role").asString()

                if (userRole != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    return@post
                }

                val gameUuid = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
                if (gameUuid == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid game ID UUID format"))
                    return@post
                }

                val request = call.receive<AddKeyRequest>()
                if (request.keys.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Keys list cannot be empty"))
                    return@post
                }

                val keyCount = runCatching {
                    gameRepository.addGameKeys(gameUuid, request.keys)
                }.getOrElse {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "One or more keys already exist in the database"))
                    return@post
                }

                if (keyCount > 0) {
                    call.respond(HttpStatusCode.Created, mapOf("insertedCount" to keyCount))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game ID not found"))
                }
            }
        }
    }
}