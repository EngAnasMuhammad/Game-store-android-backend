package com.anasdev.representation.routes

import com.anasdev.JwtTokenManager
import com.anasdev.data.repository.UserRepository
import com.anasdev.domain.model.AuthRequest
import com.anasdev.domain.model.AuthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.mindrot.jbcrypt.BCrypt

fun Route.authRoutes(userRepository: UserRepository) {
    route("/auth") {
        post("/register"){
            val request = call.receive<AuthRequest>()
            val cleanEmail = request.email.trim().lowercase()

            if (cleanEmail.isBlank() || cleanEmail.length < 6 || !cleanEmail.contains("@")) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "valid email and a password of at least 6 characters are required")
                    )
                return@post
            }

            val existingUser = userRepository.getByEmail(cleanEmail)
            if (existingUser != null) {
                call.respond(HttpStatusCode.Conflict, "An account with this email already exists")
                return@post
            }

            val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

            val role = "CUSTOMER"

            val newUserId = userRepository.createUser(
                email = cleanEmail,
                password = passwordHash,
                role = role
            )

            val token = JwtTokenManager.createToken(newUserId,role)

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    token = token,
                    userId = newUserId.toString(),
                    role = role
                )
            )
        }

        post("/login"){
            val request = call.receive<AuthRequest>()
            val cleanEmail = request.email.trim().lowercase()

            //Look up the user by email
            val user = userRepository.getByEmail(cleanEmail)

            // Check if the user exists and if the password's hash matches the store hash
            if (user == null || !BCrypt.checkpw(request.password,user.passwordHash)) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "invalid email or password")
                )
                return@post
            }

            val token = JwtTokenManager.createToken(user.id, user.role)

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    token = token,
                    userId = user.id.toString(),
                    role = user.role
                )
            )
        }
    }
}