package com.anasdev.data.local

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object UsersTable : UUIDTable("users") {
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 64).nullable()
    val role = varchar("role", 10)
}

object GamesTable : UUIDTable("games") {
    val title = varchar("title", 100)
    val description = text("description").nullable()
    val imagePath = varchar("image_path", 255)
    val releaseDate = date("release_date")
    val genre = varchar("genre", 30)
    val currentPrice = decimal("current_price", 10, 2)
    val isActive = bool("is_active").default(true)
}

object GameKeysTable : UUIDTable("game_keys") {
    val gameId = reference("game_id", GamesTable.id)
    val keyCode = varchar("key_code", 50).uniqueIndex()
    val isSold = bool("is_sold").default(false)
    val lockedUntil = timestamp("locked_until").nullable()
    val lockedByUserId = reference("locked_by_user_id", UsersTable.id, ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object OrdersTable : UUIDTable("orders") {
    val userId = reference("user_id", UsersTable.id)
    val totalAmount = decimal("total_amount", 10, 2)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object OrderItemsTable : UUIDTable("order_items") {
    val orderId = reference("order_id", OrdersTable.id, ReferenceOption.CASCADE)
    val keyId = reference("key_id", GameKeysTable.id).uniqueIndex()
    val priceAtPurchase = decimal("price_at_purchase", 10, 2)
}