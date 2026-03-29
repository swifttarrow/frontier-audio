package com.jarvis.gateway.db

import com.jarvis.gateway.memory.MemoryChunks
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    private val testInitLock = Any()
    private var testDbConnected = false

    fun init(databaseUrl: String) {
        val (jdbcUrl, user, password) = parseUrl(databaseUrl)

        // Run Flyway migrations
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        // Connect Exposed
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            validate()
        }
        Database.connect(HikariDataSource(config))
    }

    /** H2 in-memory for unit tests; resets schema each call (inside a transaction). */
    fun initForTest() {
        synchronized(testInitLock) {
            if (!testDbConnected) {
                Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
                testDbConnected = true
            }
        }
        transaction {
            SchemaUtils.drop(MemoryChunks, ConversationTurns, DeviceSessions)
            SchemaUtils.create(DeviceSessions, ConversationTurns, MemoryChunks)
        }
    }

    private fun parseUrl(url: String): Triple<String, String, String> {
        // postgresql://user:pass@host:port/db -> jdbc:postgresql://host:port/db
        val regex = Regex("""postgresql://([^:]+):([^@]+)@(.+)""")
        val match = regex.matchEntire(url)
            ?: throw IllegalArgumentException("Invalid DATABASE_URL format. Expected: postgresql://user:pass@host:port/db")
        val (user, password, hostAndDb) = match.destructured
        return Triple("jdbc:postgresql://$hostAndDb", user, password)
    }
}
