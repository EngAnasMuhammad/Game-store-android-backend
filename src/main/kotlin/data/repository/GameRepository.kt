package com.anasdev.data.repository

import com.anasdev.data.local.DatabaseFactory.dbQuery
import com.anasdev.data.local.GameKeysTable
import com.anasdev.data.local.GamesTable
import com.anasdev.domain.model.CreateGameRequest
import com.anasdev.domain.model.GameResponse
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GameRepository{

    suspend fun getAllActiveGames(): List<GameResponse> = dbQuery {
        val now = Instant.now()
        val stockCountExpr = GameKeysTable.id.count()

        GamesTable.join(
            otherTable = GameKeysTable,
            joinType = JoinType.LEFT,
            onColumn = GamesTable.id,
            otherColumn = GameKeysTable.gameId,
            additionalConstraint = {
                (GameKeysTable.isSold eq false) and ((GameKeysTable.lockedUntil.isNull()) or ( GameKeysTable.lockedUntil less now))
            }
        )
            .select(GamesTable.columns + stockCountExpr)
            .where(GamesTable.isActive eq true)
            .groupBy(GamesTable.id)
            .map{ row ->
                GameResponse(
                    id = row[GamesTable.id].value.toString(),
                    title = row[GamesTable.title],
                    description = row[GamesTable.description],
                    imagePath = row[GamesTable.imagePath],
                    genre = row[GamesTable.genre],
                    releaseDate = row[GamesTable.releaseDate].toString(),
                    currentPrice = row[GamesTable.currentPrice].toDouble(),
                    availableStock = row[stockCountExpr].toInt(),
                )

            }
    }

    suspend fun createGame(createRequest: CreateGameRequest): String = dbQuery {

        GamesTable.insertAndGetId { row ->
            row[title] = createRequest.title
            row[description] = createRequest.description
            row[imagePath] = createRequest.imagePath
            row[genre] = createRequest.genre
            row[releaseDate] = LocalDate.parse(createRequest.releaseDate)
            row[currentPrice] = createRequest.currentPrice.toBigDecimal()
        }.value.toString()
    }

    suspend fun addGameKeys(gameId: UUID, keyCodes: List<String>): Int = dbQuery {
        val gameExists = !GamesTable.selectAll().where{GamesTable.id eq gameId}.empty()
        if (!gameExists) return@dbQuery 0

        GameKeysTable.batchInsert(keyCodes){code ->
            this[GameKeysTable.gameId] = gameId
            this[GameKeysTable.keyCode] = code
        }.count()
    }
}