package com.anasdev.representation.routes

import com.anasdev.JwtTokenManager
import com.anasdev.data.repository.UserRepository
import com.anasdev.domain.model.AuthRequest
import com.anasdev.domain.model.AuthResponse
import com.anasdev.domain.model.GoogleAuthRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt

fun Route.authRoutes(userRepository: UserRepository,googleWebClientId: String) {

    val googleVerifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
    )
        .setAudience(listOf(googleWebClientId))
        .build()

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

            // 1. User doesn't exist at all
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid email or password"))
                return@post
            }

            // 2. User exists, but signed up with Google (no password)
            if (user.passwordHash == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Please login with Google account"))
                return@post
            }

            // 3. User has a password — now it's safe to verify with BCrypt
            if (!BCrypt.checkpw(request.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid email or password"))
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

        post("/google"){
            val request = call.receive<GoogleAuthRequest>()

            val idToken = runCatching{
                withContext(Dispatchers.IO){ googleVerifier.verify(request.idToken) }
            }.getOrNull()

            if (idToken == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid google id token"))
                return@post
            }

            val payload = idToken.payload
            val email = payload.email
            val isVerified = payload.emailVerified == true

            if (email.isNullOrBlank() || !isVerified) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid email"))
                return@post
            }

            val user = userRepository.findOrCreateGoogleUser(email)

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