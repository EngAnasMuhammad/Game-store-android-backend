package com.anasdev

import io.ktor.server.application.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.Date
import java.util.UUID


object JwtTokenManager{
    private lateinit var secret: String
    private lateinit var issuer: String
    private lateinit var audience: String
    lateinit var realm: String
        private set

    fun init(environment: ApplicationEnvironment){
        secret = environment.config.property("jwt.secret").getString()
        issuer = environment.config.property("jwt.domain").getString()
        audience = environment.config.property("jwt.audience").getString()
        realm = environment.config.property("jwt.realm").getString()
    }

    fun createToken(userId: UUID, role: String): String{
        val expirationMillis = System.currentTimeMillis() + 1000 * 60 * 60 * 24
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withClaim("role",role)
            .withExpiresAt(Date(expirationMillis))
            .sign(Algorithm.HMAC256(secret))
    }

    fun verifier() = JWT
        .require(Algorithm.HMAC256(secret))
        .withAudience(audience)
        .withIssuer(issuer)
        .build()
}
fun Application.configureSecurity() {
    JwtTokenManager.init(environment)

    authentication {
        jwt("auth-jwt") {
            realm = JwtTokenManager.realm
            verifier(JwtTokenManager.verifier())
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()

                if (!userId.isNullOrBlank()){
                    JWTPrincipal(credential.payload)
                }
                else null
            }
        }
    }
}