package com.anasdev.representation.routes

import com.anasdev.data.repository.GameRepository
import com.anasdev.domain.model.AddKeyRequest
import com.anasdev.domain.model.CreateGameRequest
import java.net.http.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO

fun Route.adminRoutes(
    gameRepository: GameRepository,
    supabaseUrl: String,
    supabaseServiceKey: String
) {
    val httpClient = HttpClient.newHttpClient()
    val bucketName = "game-covers"

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

                when {
                    request.title.isBlank() || request.title.length > 100 -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Title must be between 1 and 100 characters"))
                        return@post
                    }
                    request.genre.isBlank() || request.genre.length > 30 -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Genre must be between 1 and 30 characters"))
                        return@post
                    }
                    request.currentPrice < 0.0 -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Price cannot be negative"))
                        return@post
                    }
                    runCatching { java.time.LocalDate.parse(request.releaseDate) }.isFailure -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Release date must be in YYYY-MM-DD format"))
                        return@post
                    }
                }

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

            post("/upload-image") {
                val principal = call.principal<JWTPrincipal>()!!
                val userRole = principal.payload.getClaim("role").asString()
                if (userRole != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    return@post
                }

                var savedImagePath: String? = null
                var errorStatus: HttpStatusCode? = null
                var errorMessage: String? = null
                val allowedExtensions = setOf("png", "jpg", "jpeg")
                val maxFileSizeBytes = 10 * 1024 * 1024 // 10 MB

                call.receiveMultipart().forEachPart { part ->
                    try {
                        if (part is PartData.FileItem && part.name == "image" && savedImagePath == null && errorMessage == null) {
                            val fileExtension = part.originalFileName
                                ?.substringAfterLast('.', "")
                                ?.lowercase()

                            if (fileExtension !in allowedExtensions) {
                                errorStatus = HttpStatusCode.BadRequest
                                errorMessage = "Unsupported format. Allowed: PNG, JPG, JPEG"
                                return@forEachPart
                            }

                            val rawBytes = part.provider().toByteArray()
                            if (rawBytes.size > maxFileSizeBytes) {
                                errorStatus = HttpStatusCode.PayloadTooLarge
                                errorMessage = "Image file is too large (max 10 MB)"
                                return@forEachPart
                            }

                            withContext(Dispatchers.IO) {
                                val bufferedImage = ImageIO.read(rawBytes.inputStream())
                                if (bufferedImage == null) {
                                    errorStatus = HttpStatusCode.BadRequest
                                    errorMessage = "Invalid or corrupted image file"
                                    return@withContext
                                }

                                val outputStream = ByteArrayOutputStream()
                                val thumbnailBuilder = if (bufferedImage.width > 800 || bufferedImage.height > 800) {
                                    Thumbnails.of(bufferedImage).size(800, 800)
                                } else {
                                    Thumbnails.of(bufferedImage).scale(1.0)
                                }

                                thumbnailBuilder
                                    .imageType(BufferedImage.TYPE_INT_RGB)
                                    .outputFormat("jpg")
                                    .outputQuality(0.80)
                                    .toOutputStream(outputStream)

                                val uniqueFilename = "${UUID.randomUUID()}.jpg"
                                val uploadUrl = "$supabaseUrl/storage/v1/object/$bucketName/$uniqueFilename"

                                val uploadRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(uploadUrl))
                                    .header("Authorization", "Bearer $supabaseServiceKey")
                                    .header("apikey", supabaseServiceKey)
                                    .header("Content-Type", "image/jpeg")
                                    .header("x-upsert", "true")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(outputStream.toByteArray()))
                                    .build()

                                val response = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString())

                                if (response.statusCode() in 200..299) {
                                    savedImagePath = "$supabaseUrl/storage/v1/object/public/$bucketName/$uniqueFilename"
                                } else {
                                    errorStatus = HttpStatusCode.InternalServerError
                                    errorMessage = "Storage upload failed (${response.statusCode()})"
                                }
                            }
                        }
                    } finally {
                        part.release()
                    }
                }

                when {
                    savedImagePath != null -> {
                        call.respond(HttpStatusCode.Created, mapOf("imagePath" to savedImagePath))
                    }
                    errorMessage != null -> {
                        call.respond(errorStatus ?: HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
                    }
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing image file"))
                    }
                }
            }
        }
    }
}