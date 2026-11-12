package com.anasdev.data.local

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcUrl = config.property("storage.jdbcURL").getString()
        val user = config.property("storage.user").getString()
        val password = config.property("storage.password").getString()
        val maxPoolSize = config.property("storage.maximumPoolSize").getString().toInt()

        val hikariConfig = HikariConfig().apply {
            this.driverClassName = driverClassName
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.maximumPoolSize = maxPoolSize
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}