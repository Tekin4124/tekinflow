package com.tekinflow.backend.util

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Year

class RequestNumberGenerator {

    suspend fun nextRequestNumber(): String = newSuspendedTransaction(Dispatchers.IO) {
        val year = Year.now().value
        val nextSequence = exec("SELECT nextval('request_number_seq')") { resultSet ->
            if (resultSet.next()) resultSet.getLong(1) else null
        } ?: throw IllegalStateException("Talep numarası üretilemedi")

        "TLP-$year-${nextSequence.toString().padStart(6, '0')}"
    }
}
