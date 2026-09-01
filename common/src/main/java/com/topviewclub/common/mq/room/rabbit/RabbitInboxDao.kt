package com.topviewclub.common.mq.room.rabbit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RabbitInboxDao {
    @Query("SELECT * FROM rabbitInbox WHERE idempotencyKey = :idempotencyKey")
    fun find(idempotencyKey: String): RabbitInbox?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(message: RabbitInbox): Long

    @Query(
        "UPDATE rabbitInbox SET eventId = :eventId, workflowId = :workflowId, " +
            "status = 'PROCESSING', attempt = :attempt, updatedAt = :updatedAt, lastError = NULL " +
            "WHERE idempotencyKey = :idempotencyKey",
    )
    fun markProcessing(
        idempotencyKey: String,
        eventId: String,
        workflowId: String,
        attempt: Int,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE rabbitInbox SET status = 'COMPLETED', updatedAt = :updatedAt, lastError = NULL " +
            "WHERE idempotencyKey = :idempotencyKey AND status = 'PROCESSING'",
    )
    fun markCompleted(idempotencyKey: String, updatedAt: Long): Int

    @Query(
        "UPDATE rabbitInbox SET updatedAt = :updatedAt " +
            "WHERE idempotencyKey = :idempotencyKey AND status = 'PROCESSING'",
    )
    fun touch(idempotencyKey: String, updatedAt: Long): Int

    @Query(
        "UPDATE rabbitInbox SET status = 'FAILED', updatedAt = :updatedAt, lastError = :lastError " +
            "WHERE idempotencyKey = :idempotencyKey AND status = 'PROCESSING'",
    )
    fun markFailed(idempotencyKey: String, updatedAt: Long, lastError: String?): Int
}
