package com.tekinflow.backend.repository
import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.*
import com.tekinflow.backend.util.ConflictException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.*

private object Counters:Table("test_counter") {val id=integer("id");val value=integer("value");override val primaryKey=PrimaryKey(id)}
class IdempotencyIntegrationTest {
    private fun setup():UUID {
        Database.connect("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=KEY,VALUE",driver="org.h2.Driver")
        val user=UUID.randomUUID()
        transaction {
            SchemaUtils.create(UsersTable,IdempotencyKeysTable,Counters)
            UsersTable.insert {it[id]=user;it[username]="demo";it[fullName]="Demo";it[passwordHash]="test-only";it[role]=UserRole.PURCHASING;it[createdAt]=Instant.now();it[updatedAt]=Instant.now()}
            Counters.insert {it[id]=1;it[value]=0}
        }
        return user
    }
    @Test fun successfulReplayReturnsTheSavedResultAndDoesNotMutateTwice()=runBlocking {
        val user=setup();val repo=IdempotencyRepository();val key=UUID.randomUUID().toString();var runs=0
        val first=repo.execute(key,user,"POST /requests","body",String.serializer()){runs++;"created"}
        val second=repo.execute(key,user,"POST /requests","body",String.serializer()){runs++;"duplicate"}
        assertEquals("created",first);assertEquals(first,second);assertEquals(1,runs)
    }
    @Test fun nestedMutationAndReservationRollBackTogetherOnFailure()=runBlocking {
        val user=setup();val repo=IdempotencyRepository();val key=UUID.randomUUID().toString()
        assertFailsWith<IllegalStateException> {
            repo.execute(key,user,"POST /requests","body",String.serializer()) {
                dbQuery { Counters.update({Counters.id eq 1}) { it[value]=1 } }
                error("transient failure")
            }
        }
        transaction { assertEquals(0,Counters.selectAll().single()[Counters.value]);assertEquals(0L,IdempotencyKeysTable.selectAll().count()) }
        assertEquals("retried",repo.execute(key,user,"POST /requests","body",String.serializer()){"retried"})
    }
    @Test fun sameKeyCannotReplayDifferentBodiesOrPaths()=runBlocking {
        val user=setup();val repo=IdempotencyRepository();val key=UUID.randomUUID().toString()
        repo.execute(key,user,"POST /requests","body",String.serializer()){"created"}
        assertFailsWith<ConflictException>{repo.execute(key,user,"POST /requests","different",String.serializer()){"bad"}}
        assertFailsWith<ConflictException>{repo.execute(key,user,"PUT /requests","body",String.serializer()){"bad"}}
        Unit
    }
}
