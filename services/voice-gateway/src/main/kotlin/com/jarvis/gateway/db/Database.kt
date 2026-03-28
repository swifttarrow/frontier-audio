package com.jarvis.gateway.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {

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

    /** For tests with H2 or in-memory DB */
    fun initForTest() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        org.jetbrains.exposed.sql.SchemaUtils.create(DeviceSessions, ConversationTurns)
        try {
            org.jetbrains.exposed.sql.SchemaUtils.create(com.jarvis.gateway.memory.MemoryChunks)
        } catch (_: Exception) {
            // Table may already exist
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
