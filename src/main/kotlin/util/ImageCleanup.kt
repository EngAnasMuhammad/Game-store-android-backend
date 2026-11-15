package com.anasdev.util

import com.anasdev.data.repository.GameRepository
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.hours

object ImageCleanup {

    private const val ONE_HOUR_MS = 60L * 60L * 1000L

    suspend fun deleteOrphanedImages(
        gameRepository: GameRepository,
        gracePeriodMs: Long = ONE_HOUR_MS
    ): Int = withContext(Dispatchers.IO) {
        val uploadDir = File("uploads/images")
        if (!uploadDir.exists() || !uploadDir.isDirectory) return@withContext 0

        val usedPaths = gameRepository.getAllUsedImagePaths()
        val now = System.currentTimeMillis()
        var deletedCount = 0

        uploadDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach

            val dbPath = "/uploads/images/${file.name}"
            val isUnused = dbPath !in usedPaths
            val isPastGracePeriod = (now - file.lastModified()) > gracePeriodMs

            if (isUnused && isPastGracePeriod && file.delete()) {
                deletedCount++
            }
        }

        deletedCount
    }
}

fun Application.startImageCleanupJob(gameRepository: GameRepository) {
    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                val deleted = ImageCleanup.deleteOrphanedImages(gameRepository)
                if (deleted > 0) {
                    environment.log.info("ImageCleanup: deleted $deleted orphaned image(s)")
                }
            }.onFailure { error ->
                environment.log.error("ImageCleanup failed", error)
            }
            delay(6.hours)
        }
    }
}