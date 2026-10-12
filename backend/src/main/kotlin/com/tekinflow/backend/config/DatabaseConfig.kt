package com.tekinflow.backend.config

import com.tekinflow.backend.auth.PasswordHasher
import com.tekinflow.backend.model.MaterialStatus
import com.tekinflow.backend.model.PurchaseRequestsTable
import com.tekinflow.backend.model.UrgencyLevel
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.model.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.Year

object DatabaseConfig {

    private val migrationFiles = listOf(
        "V1__initial_schema.sql",
        "V2__add_is_super_admin.sql",
        "V3__add_request_items.sql",
        "V4__add_user_fcm_token.sql",
        "V5__add_user_preferences.sql",
        "V6__promote_pending_requests.sql",
        "V7__use_in_progress_for_new_requests.sql",
        "V8__split_admin_roles.sql",
        "V9__add_login_lockout_count.sql",
        "V10__hash_refresh_tokens.sql",
        "V11__add_user_token_version.sql",
        "V12__add_contact_person_and_delivery_fields.sql",
        "V13__add_audit_log.sql",
        "V14__add_user_two_factor.sql",
        "V15__idempotency_response_cache.sql",
        "V16__encrypt_two_factor_storage.sql"
    )

    fun init(config: ApplicationConfig, logger: Logger) {
        val password = config.propertyOrNull("database.password")?.getString()
            ?: error("DB_PASSWORD tanımlanmadı")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.property("database.url").getString()
            username = config.property("database.user").getString()
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.property("database.maxPoolSize").getString().toInt()
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))
        runManagedMigrations(logger)
    }

    private fun runManagedMigrations(logger: Logger) {
        org.jetbrains.exposed.sql.transactions.transaction {
            createMigrationHistoryTable()
            bootstrapMigrationHistoryForExistingSchema(logger)

            val applied = appliedMigrations()
            migrationFiles.filterNot { it in applied }.forEach { fileName ->
                migrationStatements(fileName).forEach { exec(it) }
                markMigrationApplied(fileName)
                logger.info("Applied database migration {}", fileName)
            }
        }
        logger.info("Veritabani semasi dogrulandi")
    }

    @Suppress("UNREACHABLE_CODE", "unused")
    private fun runMigrations(logger: Logger) {
        runManagedMigrations(logger)
        return
        val resource = checkNotNull(
            DatabaseConfig::class.java.classLoader.getResourceAsStream("db/migration/V1__initial_schema.sql")
        ) { "Migration dosyası bulunamadı" }

        val statements = BufferedReader(InputStreamReader(resource))
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        org.jetbrains.exposed.sql.transactions.transaction {
            statements.forEach { exec(it) }
            listOf(
                "V2__add_is_super_admin.sql",
                "V3__add_request_items.sql",
                "V4__add_user_fcm_token.sql",
                "V5__add_user_preferences.sql",
                "V6__promote_pending_requests.sql",
                "V7__use_in_progress_for_new_requests.sql",
                "V8__split_admin_roles.sql",
                "V9__add_login_lockout_count.sql",
                "V10__hash_refresh_tokens.sql"
            ).forEach { fileName ->
                migrationStatements(fileName).forEach { exec(it) }
            }
        }
        logger.info("Veritabanı şeması doğrulandı")
    }

    private fun createMigrationHistoryTable() {
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version VARCHAR(255) PRIMARY KEY,
                applied_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """.trimIndent()
        )
    }

    private fun bootstrapMigrationHistoryForExistingSchema(logger: Logger) {
        if (appliedMigrations().isNotEmpty() || !tableExists("users")) return

        val inferred = migrationFiles.filter(::migrationAlreadyPresent).toMutableSet()

        // These old data migrations changed business statuses. On an existing
        // database without migration history, never replay them silently.
        inferred += "V6__promote_pending_requests.sql"
        inferred += "V7__use_in_progress_for_new_requests.sql"

        inferred.forEach(::markMigrationApplied)
        logger.warn(
            "Existing database without schema_migrations detected; marked {} migrations as already applied",
            inferred.size
        )
    }

    private fun migrationAlreadyPresent(fileName: String): Boolean {
        return when (fileName) {
            "V1__initial_schema.sql" -> tableExists("users") && tableExists("purchase_requests") && tableExists("refresh_tokens")
            "V2__add_is_super_admin.sql" -> columnExists("users", "is_super_admin")
            "V3__add_request_items.sql" -> tableExists("purchase_request_items")
            "V4__add_user_fcm_token.sql" -> columnExists("users", "fcm_token")
            "V5__add_user_preferences.sql" -> tableExists("user_preferences")
            "V6__promote_pending_requests.sql" -> false
            "V7__use_in_progress_for_new_requests.sql" -> false
            "V8__split_admin_roles.sql" -> userRoleConstraintContains("PURCHASING_ADMIN")
            "V9__add_login_lockout_count.sql" -> columnExists("users", "lockout_count")
            "V10__hash_refresh_tokens.sql" -> columnDataType("refresh_tokens", "token") in setOf(
                "character varying",
                "varchar",
                "text"
            )
            "V11__add_user_token_version.sql" -> columnExists("users", "token_version")
            "V12__add_contact_person_and_delivery_fields.sql" -> columnExists("purchase_requests", "contact_person_name")
            "V13__add_audit_log.sql" -> tableExists("audit_log")
            "V14__add_user_two_factor.sql" -> columnExists("users", "two_factor_enabled")
            "V15__idempotency_response_cache.sql" -> columnExists("idempotency_keys", "response_json")
            "V16__encrypt_two_factor_storage.sql" -> columnDataType("users", "two_factor_secret") == "text"
            else -> false
        }
    }

    private fun appliedMigrations(): Set<String> {
        val migrations = mutableSetOf<String>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            "SELECT version FROM schema_migrations"
        ) { resultSet ->
            while (resultSet.next()) {
                migrations += resultSet.getString("version")
            }
        }
        return migrations
    }

    private fun markMigrationApplied(fileName: String) {
        val escaped = fileName.replace("'", "''")
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            "INSERT INTO schema_migrations (version) VALUES ('$escaped') ON CONFLICT (version) DO NOTHING"
        )
    }

    private fun tableExists(tableName: String): Boolean {
        val escaped = tableName.replace("'", "''")
        return org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = '$escaped'
            )
            """.trimIndent()
        ) { resultSet ->
            resultSet.next() && resultSet.getBoolean(1)
        } ?: false
    }

    private fun columnExists(tableName: String, columnName: String): Boolean {
        val escapedTable = tableName.replace("'", "''")
        val escapedColumn = columnName.replace("'", "''")
        return org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '$escapedTable'
                  AND column_name = '$escapedColumn'
            )
            """.trimIndent()
        ) { resultSet ->
            resultSet.next() && resultSet.getBoolean(1)
        } ?: false
    }

    private fun columnDataType(tableName: String, columnName: String): String? {
        val escapedTable = tableName.replace("'", "''")
        val escapedColumn = columnName.replace("'", "''")
        return org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            """
            SELECT data_type
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = '$escapedTable'
              AND column_name = '$escapedColumn'
            LIMIT 1
            """.trimIndent()
        ) { resultSet ->
            if (resultSet.next()) resultSet.getString("data_type") else null
        }
    }

    private fun userRoleConstraintContains(roleName: String): Boolean {
        val escaped = roleName.replace("'", "''")
        return org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conname = 'users_role_check'
                  AND pg_get_constraintdef(oid) LIKE '%$escaped%'
            )
            """.trimIndent()
        ) { resultSet ->
            resultSet.next() && resultSet.getBoolean(1)
        } ?: false
    }

    private fun migrationStatements(fileName: String): List<String> {
        val resource = checkNotNull(
            DatabaseConfig::class.java.classLoader.getResourceAsStream("db/migration/$fileName")
        ) { "Migration file not found: $fileName" }

        return BufferedReader(InputStreamReader(resource))
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    suspend fun seedInitialAdminIfNeeded(passwordHasher: PasswordHasher, logger: Logger) {
        newSuspendedTransaction(Dispatchers.IO) {
            val superAdminExists = UsersTable
                .selectAll()
                .where { UsersTable.isSuperAdmin eq true }
                .limit(1)
                .count() > 0
            if (superAdminExists) {
                return@newSuspendedTransaction
            }

            val initialPassword = System.getenv("ADMIN_INITIAL_PASSWORD")
            if (initialPassword.isNullOrBlank()) {
                logger.warn("ADMIN_INITIAL_PASSWORD ayarlanmadığı için süper admin kullanıcısı oluşturulamadı")
                return@newSuspendedTransaction
            }
            if (initialPassword.length < minInitialAdminPasswordLength) {
                logger.warn("ADMIN_INITIAL_PASSWORD en az {} karakter olmali", minInitialAdminPasswordLength)
                return@newSuspendedTransaction
            }

            val now = Instant.now()
            val existingAdmin = UsersTable
                .selectAll()
                .where { UsersTable.username eq "admin" }
                .singleOrNull()

            if (existingAdmin == null) {
                UsersTable.insert {
                    it[UsersTable.username] = "admin"
                    it[UsersTable.fullName] = "Sistem Yoneticisi"
                    it[UsersTable.passwordHash] = passwordHasher.hash(initialPassword)
                    it[UsersTable.role] = UserRole.ADMIN
                    it[UsersTable.licensePlate] = null
                    it[UsersTable.isActive] = true
                    it[UsersTable.isSuperAdmin] = true
                    it[UsersTable.tokenVersion] = 0
                    it[UsersTable.failedAttempts] = 0
                    it[UsersTable.lockedUntil] = null
                    it[UsersTable.createdAt] = now
                    it[UsersTable.updatedAt] = now
                }
                logger.warn("Ilk super admin kullanicisi olusturuldu: username=admin")
            } else {
                UsersTable.update({ UsersTable.id eq existingAdmin[UsersTable.id] }) {
                    it[UsersTable.passwordHash] = passwordHasher.hash(initialPassword)
                    it[UsersTable.role] = UserRole.ADMIN
                    it[UsersTable.licensePlate] = null
                    it[UsersTable.isActive] = true
                    it[UsersTable.isSuperAdmin] = true
                    it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
                    it[UsersTable.failedAttempts] = 0
                    it[UsersTable.lockedUntil] = null
                    it[UsersTable.updatedAt] = now
                }
                logger.warn("Mevcut admin kullanicisi super admin'e yukseltildi: username=admin")
            }

            logger.warn("ADMIN_INITIAL_PASSWORD env degerini ilk giristen sonra sunucudan kaldirin")
        }
    }

    suspend fun seedDemoUsersIfEnabled(passwordHasher: PasswordHasher, logger: Logger) {
        if (!System.getenv("SEED_DEMO_USERS").equals("true", ignoreCase = true)) {
            logger.info("Demo kullanici seed kapali. Acmak icin SEED_DEMO_USERS=true verin.")
            return
        }
        if (System.getenv("APP_ENV").equals("prod", ignoreCase = true) ||
            System.getenv("KTOR_ENV").equals("prod", ignoreCase = true)
        ) {
            logger.warn("Prod ortamda demo kullanici seed atlandi.")
            return
        }

        // Demo kullanici parolasi koda gomulmez; yalniz env'den okunur. Env
        // verilmezse demo seed tamamen atlanir (boylece yanlis konfigurasyonda
        // bile kaynak kodda hazir bir parola bulunmaz).
        val demoPassword = System.getenv("SEED_DEMO_USER_PASSWORD")?.takeIf { it.isNotBlank() }
        if (demoPassword == null) {
            logger.warn("SEED_DEMO_USER_PASSWORD verilmedigi icin demo kullanici seed atlandi.")
            return
        }

        newSuspendedTransaction(Dispatchers.IO) {
            val now = Instant.now()
            demoAdminUsers.forEach { seed ->
                val username = seed.username.lowercase()
                val exists = UsersTable
                    .selectAll()
                    .where { UsersTable.username eq username }
                    .singleOrNull() != null
                if (exists) return@forEach

                UsersTable.insert {
                    it[UsersTable.username] = username
                    it[UsersTable.fullName] = seed.fullName
                    it[UsersTable.passwordHash] = passwordHasher.hash(demoPassword)
                    it[UsersTable.role] = UserRole.PURCHASING_ADMIN
                    it[UsersTable.licensePlate] = null
                    it[UsersTable.isActive] = true
                    it[UsersTable.isSuperAdmin] = false
                    it[UsersTable.tokenVersion] = 0
                    it[UsersTable.failedAttempts] = 0
                    it[UsersTable.lockedUntil] = null
                    it[UsersTable.createdAt] = now
                    it[UsersTable.updatedAt] = now
                }
                logger.warn("Demo admin kullanicisi olusturuldu: username={}", seed.username)
            }
        }
    }

    suspend fun seedDemoRequestsIfNeeded(logger: Logger) {
        if (System.getenv("APP_ENV").equals("prod", true) || System.getenv("KTOR_ENV").equals("prod", true)) return
        newSuspendedTransaction(Dispatchers.IO) {
            val existingCount = PurchaseRequestsTable.selectAll().count()
            if (existingCount >= DEMO_REQUEST_COUNT) return@newSuspendedTransaction

            val ownerId = UsersTable
                .selectAll()
                .where { UsersTable.role eq UserRole.PURCHASING }
                .limit(1)
                .singleOrNull()
                ?.get(UsersTable.id)
                ?.value
                ?: UsersTable
                    .selectAll()
                    .where { UsersTable.isActive eq true }
                    .limit(1)
                    .singleOrNull()
                    ?.get(UsersTable.id)
                    ?.value
                ?: return@newSuspendedTransaction

            val now = Instant.now()
            demoRequests()
                .drop(existingCount.toInt())
                .take((DEMO_REQUEST_COUNT - existingCount).toInt())
                .forEachIndexed { index, demo ->
                    val requestNumber = nextSeedRequestNumber()
                    PurchaseRequestsTable.insert {
                        it[PurchaseRequestsTable.requestNumber] = requestNumber
                        it[PurchaseRequestsTable.itemToPurchase] = demo.item
                        it[PurchaseRequestsTable.companyName] = demo.company
                        it[PurchaseRequestsTable.companyAddress] = demo.address
                        it[PurchaseRequestsTable.contactNumber] = demo.contact
                        it[PurchaseRequestsTable.productDimensions] = demo.dimensions
                        it[PurchaseRequestsTable.productWeight] = demo.weight
                        it[PurchaseRequestsTable.urgencyLevel] = demo.urgency
                        it[PurchaseRequestsTable.purchaseDate] = LocalDate.now().plusDays((index + 1).toLong())
                        it[PurchaseRequestsTable.notes] = demo.notes
                        it[PurchaseRequestsTable.licensePlate] = null
                        it[PurchaseRequestsTable.materialStatus] = demo.status
                        it[PurchaseRequestsTable.assignedDriverId] = null
                        it[PurchaseRequestsTable.assignedPurchasingStaffId] = ownerId
                        it[PurchaseRequestsTable.cancellationReason] = null
                        it[PurchaseRequestsTable.cancelledBy] = null
                        it[PurchaseRequestsTable.objectionReason] = null
                        it[PurchaseRequestsTable.hasObjection] = false
                        it[PurchaseRequestsTable.objectionBy] = null
                        it[PurchaseRequestsTable.redirectedToRequestId] = null
                        it[PurchaseRequestsTable.createdAt] = now.minusSeconds((DEMO_REQUEST_COUNT - index).toLong() * 60)
                        it[PurchaseRequestsTable.updatedAt] = now.minusSeconds((DEMO_REQUEST_COUNT - index).toLong() * 30)
                    }
                }

            logger.info("Demo talepler hazirlandi: toplam={} hedef={}", PurchaseRequestsTable.selectAll().count(), DEMO_REQUEST_COUNT)
        }
    }

    private fun nextSeedRequestNumber(): String {
        val year = Year.now().value
        val nextSequence = org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            "SELECT nextval('request_number_seq')"
        ) { resultSet ->
            if (resultSet.next()) resultSet.getLong(1) else null
        } ?: error("Talep numarasi uretilemedi")

        return "TLP-$year-${nextSequence.toString().padStart(6, '0')}"
    }

    private data class DemoRequest(
        val item: String,
        val company: String,
        val address: String,
        val contact: String,
        val dimensions: String,
        val weight: String,
        val urgency: UrgencyLevel,
        val status: MaterialStatus,
        val notes: String
    )

    private fun demoRequests(): List<DemoRequest> = listOf(
        DemoRequest("Bağlantı elemanı", "Örnek Tedarikçi", "Örnek adres", "", "Örnek paket", "10 kg", UrgencyLevel.NORMAL, MaterialStatus.IN_PROGRESS, "Sentetik demo kaydı")
    )

    private const val DEMO_REQUEST_COUNT = 1
    private const val minInitialAdminPasswordLength = 12
    private data class DemoAdminUser(
        val username: String,
        val fullName: String
    )

    // Parolalar koda gomulmez; seedDemoUsersIfEnabled SEED_DEMO_USER_PASSWORD
    // env degerini kullanir. Bu liste yalniz demo kullanici adlarini tutar.
    private val demoAdminUsers = listOf(
        DemoAdminUser("demo_manager", "Demo Yönetici")
    )
}

private object DbBoundary : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    object Key : kotlin.coroutines.CoroutineContext.Key<DbBoundary>
}
suspend fun <T> dbQuery(block: suspend () -> T): T {
    if (kotlin.coroutines.coroutineContext[DbBoundary.Key] != null) return block()
    return newSuspendedTransaction(Dispatchers.IO) {
        kotlinx.coroutines.withContext(DbBoundary) { block() }
    }
}
