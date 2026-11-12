package com.anasdev.data.repository

import com.anasdev.data.local.DatabaseFactory.dbQuery
import com.anasdev.data.local.UsersTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val role: String
)
class UserRepository {

    suspend fun getByEmail(email: String): UserRecord?= dbQuery {
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
}