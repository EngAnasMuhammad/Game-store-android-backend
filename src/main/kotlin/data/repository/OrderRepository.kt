package com.anasdev.data.repository

import com.anasdev.data.local.DatabaseFactory.dbQuery
import com.anasdev.data.local.GameKeysTable
import com.anasdev.data.local.GamesTable
import com.anasdev.data.local.OrderItemsTable
import com.anasdev.data.local.OrdersTable
import com.anasdev.domain.model.LockedCartItemResponse
import com.anasdev.domain.model.OrderItemReceiptDto
import com.anasdev.domain.model.OrderReceiptResponse
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit

class OrderRepository{

    suspend fun lockKeyForCart(userId: UUID, gameId: UUID): LockedCartItemResponse? = dbQuery {
        val now = Instant.now()
        val lockExpiration = now.plus(15, ChronoUnit.MINUTES)

        val gameRow = GamesTable.selectAll()
            .where {
                (GamesTable.id eq gameId) and (GamesTable.isActive eq true)
            }
            .singleOrNull() ?: return@dbQuery null


        val availableKeyRow = GameKeysTable.selectAll()
            .where {
                (GameKeysTable.gameId eq gameId) and
                (GameKeysTable.isSold eq false) and
                ((GameKeysTable.lockedUntil.isNull()) or (GameKeysTable.lockedUntil less now))
            }
            .limit(1)
            .forUpdate() // prevents two people grabing the same key simultaneously
            .singleOrNull() ?: return@dbQuery null

        val targetKeyId = availableKeyRow[GameKeysTable.id].value

        GameKeysTable.update({ GameKeysTable.id eq targetKeyId }) { row ->
            row[lockedByUserId] = userId
            row[lockedUntil] = lockExpiration
        }

        LockedCartItemResponse(
            keyId = targetKeyId.toString(),
            gameId = gameId.toString(),
            gameTitle = gameRow[GamesTable.title],
            price = gameRow[GamesTable.currentPrice].toDouble(),
            lockedUntil = lockExpiration.toString()
        )
    }

    suspend fun getLockedCartItems(userId: UUID): List<LockedCartItemResponse> = dbQuery {
        val now = Instant.now()

        (GameKeysTable innerJoin GamesTable)
            .selectAll()
            .where{
                (GameKeysTable.lockedByUserId eq userId)and
                        (GameKeysTable.isSold eq false) and
                        (GameKeysTable.lockedUntil greater now)
            }
            .map{row ->
                LockedCartItemResponse(
                    row[GameKeysTable.id].value.toString(),
                    row[GamesTable.id].value.toString(),
                    row[GamesTable.title],
                    row[GamesTable.currentPrice].toDouble(),
                    row[GameKeysTable.lockedUntil].toString()
                )
            }
    }

    suspend fun checkout(userId: UUID): OrderReceiptResponse? = dbQuery {
        val now = Instant.now()

        val lockedKeys = (GameKeysTable innerJoin GamesTable)
            .selectAll()
            .where{
                (GameKeysTable.lockedByUserId eq userId) and
                        (GameKeysTable.isSold eq false) and
                        (GameKeysTable.lockedUntil greater now)
            }
            .toList()

        if(lockedKeys.isEmpty()){
            return@dbQuery null
        }

        val total = lockedKeys.sumOf { it[GamesTable.currentPrice].toDouble() }

        val newOrderId = OrdersTable.insertAndGetId {
            it[this.userId] = userId
            it[totalAmount] = total.toBigDecimal()
        }.value

        val receiptItems = lockedKeys.map { row ->
            val currentKeyId = row[GameKeysTable.id].value
            val currentPrice = row[GamesTable.currentPrice]
            val secretKeyCode = row[GameKeysTable.keyCode]

            GameKeysTable.update({ GameKeysTable.id eq currentKeyId}){
                it[isSold] = true
                it[lockedUntil] = null
            }

            OrderItemsTable.insert {
                it[orderId] = newOrderId
                it[keyId] = currentKeyId
                it[priceAtPurchase] = currentPrice
            }

            OrderItemReceiptDto(
                gameId = row[GamesTable.id].value.toString(),
                gameTitle = row[GamesTable.title],
                priceAtPurchase = currentPrice.toDouble(),
                keyCode = secretKeyCode
            )
        }

        OrderReceiptResponse(
            orderId = newOrderId.toString(),
            totalAmount = total,
            createdAt = now.toString(),
            items = receiptItems
        )
    }

    suspend fun getOrderHistory(userId: UUID): List<OrderReceiptResponse> = dbQuery {
        val orders = OrdersTable
            .selectAll()
            .where { OrdersTable.userId eq userId }
            .orderBy(OrdersTable.createdAt to SortOrder.DESC)


        orders.map { current ->
            val currentOrderId = current[OrdersTable.id].value

            val orderItems = (OrderItemsTable innerJoin GameKeysTable innerJoin GamesTable)
                .selectAll()
                .where{
                    OrderItemsTable.orderId eq currentOrderId
                }
                .map { row ->
                    OrderItemReceiptDto(
                        gameId = row[GamesTable.id].value.toString(),
                        gameTitle = row[GamesTable.title],
                        priceAtPurchase = row[OrderItemsTable.priceAtPurchase].toDouble(),
                        keyCode = row[GameKeysTable.keyCode]
                    )
                }

            OrderReceiptResponse(
                orderId = currentOrderId.toString(),
                totalAmount = current[OrdersTable.totalAmount].toDouble(),
                createdAt = current[OrdersTable.createdAt].toString(),
                items = orderItems
            )
        }

    }

    suspend fun removeCartItem(userId: UUID, keyId: UUID): Boolean = dbQuery {
        val updatedRows = GameKeysTable.update({
            (GameKeysTable.id eq keyId) and
                    (GameKeysTable.lockedByUserId eq userId) and
                    (GameKeysTable.lockedUntil.isNotNull()) and
                    (GameKeysTable.isSold eq false)
        }) {
            it[lockedByUserId] = null
            it[lockedUntil] = null
        }
        updatedRows > 0
    }

}