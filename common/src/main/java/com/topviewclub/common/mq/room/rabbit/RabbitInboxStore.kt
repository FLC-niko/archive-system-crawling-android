package com.topviewclub.common.mq.room.rabbit

import com.topviewclub.common.mq.room.correlationDataBase
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val PROCESSING_LEASE_MS = 30 * 60 * 1000L

private val rabbitInboxLock = ReentrantLock()
private val rabbitInboxDao: RabbitInboxDao
    get() = correlationDataBase.rabbitInboxDao()

/**
 * 同进程内原子地领取 RabbitMQ 任务。
 *
 * COMPLETED 永久幂等；FAILED 允许下一次 broker 重试重新领取；PROCESSING 只有在
 * broker 明确重投或租约过期时才允许接管。
 */
object RabbitInboxStore {
    fun claim(message: InboxMessage): ClaimResult = rabbitInboxLock.withLock {
        val existing = rabbitInboxDao.find(message.idempotencyKey)
        if (existing == null) {
            val resultEventId = "evt_${UUID.randomUUID()}"
            val inserted = rabbitInboxDao.insert(
                RabbitInbox(
                    idempotencyKey = message.idempotencyKey,
                    eventId = message.eventId,
                    workflowId = message.workflowId,
                    resultEventId = resultEventId,
                    status = STATUS_PROCESSING,
                    attempt = 1,
                    receivedAt = message.now,
                    updatedAt = message.now,
                ),
            )
            if (inserted != -1L) {
                return@withLock ClaimResult(true, resultEventId)
            }
            val concurrent = rabbitInboxDao.find(message.idempotencyKey)
                ?: error("RabbitMQ inbox 插入后无法读取记录")
            return@withLock ClaimResult(false, concurrent.resultEventId)
        }

        if (existing.status == STATUS_COMPLETED) {
            return@withLock ClaimResult(false, existing.resultEventId)
        }

        val leaseExpired = existing.status == STATUS_PROCESSING &&
            message.now - existing.updatedAt >= PROCESSING_LEASE_MS
        val activeLease = existing.status == STATUS_PROCESSING && !leaseExpired
        if (activeLease && !message.allowProcessingTakeover) {
            return@withLock ClaimResult(false, existing.resultEventId)
        }

        rabbitInboxDao.markProcessing(
            idempotencyKey = message.idempotencyKey,
            eventId = message.eventId,
            workflowId = message.workflowId,
            attempt = existing.attempt + 1,
            updatedAt = message.now,
        )
        ClaimResult(true, existing.resultEventId)
    }

    fun markCompleted(idempotencyKey: String, now: Long = System.currentTimeMillis()) {
        rabbitInboxDao.markCompleted(idempotencyKey, now)
    }

    fun touch(idempotencyKey: String, now: Long = System.currentTimeMillis()) {
        rabbitInboxDao.touch(idempotencyKey, now)
    }

    fun markFailed(
        idempotencyKey: String,
        error: Throwable? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        rabbitInboxDao.markFailed(idempotencyKey, now, error?.message?.take(500))
    }

    data class InboxMessage(
        val idempotencyKey: String,
        val eventId: String,
        val workflowId: String,
        val now: Long = System.currentTimeMillis(),
        val allowProcessingTakeover: Boolean = false,
    )

    data class ClaimResult(
        val claimed: Boolean,
        val resultEventId: String,
    )

    private const val STATUS_PROCESSING = "PROCESSING"
    private const val STATUS_COMPLETED = "COMPLETED"
}
