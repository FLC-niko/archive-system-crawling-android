package com.topviewclub.common.mq.room.rabbit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rabbitInbox",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class RabbitInbox(
    @PrimaryKey
    val idempotencyKey: String,
    val eventId: String,
    val workflowId: String,
    val resultEventId: String,
    val status: String,
    val attempt: Int,
    val receivedAt: Long,
    val updatedAt: Long,
    val lastError: String? = null,
)
