package com.anasdev.data.repository

import com.anasdev.data.local.DatabaseFactory.dbQuery
import com.anasdev.data.local.UsersTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.util.*

data class UserRecord(
    val id: UUID,
    val email: String,
    val passwordHash: String?,
    val role: String
)
class UserRepository {

    suspend fun getByEmail(email: String): UserRecord? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .map { row ->
                UserRecord(
                    id = row[UsersTable.id].value,
                    email = row[UsersTable.email],
                    passwordHash = row[UsersTable.passwordHash],
                    role = row[UsersTable.role]
                )
            }
            .singleOrNull()
    }

    suspend fun createUser(email: String, password: String, role: String = "CUSTOMER"): UUID = dbQuery {
        UsersTable.insertAndGetId { row->
            row[UsersTable.email] = email
            row[UsersTable.passwordHash] = password
            row[UsersTable.role] = role
        }.value
    }

    suspend fun findOrCreateGoogleUser(email: String): UserRecord = dbQuery {
        val cleanEmail = email.trim().lowercase()

        val existingUser = UsersTable.selectAll()
            .where { UsersTable.email eq cleanEmail }
            .map { row ->
                UserRecord(
                    id = row[UsersTable.id].value,
                    email = row[UsersTable.email],
                    passwordHash = row[UsersTable.passwordHash],
                    role = row[UsersTable.role]
                )
            }
            .singleOrNull()

        if (existingUser != null) {
            return@dbQuery existingUser
        }

        val newId = UsersTable.insertAndGetId {
            it[UsersTable.email] = cleanEmail
            it[passwordHash] = null
            it[role] = "CUSTOMER"
        }

        UserRecord(
            id = newId.value,
            email = cleanEmail,
            passwordHash = null,
            role = "CUSTOMER"
        )
    }
}