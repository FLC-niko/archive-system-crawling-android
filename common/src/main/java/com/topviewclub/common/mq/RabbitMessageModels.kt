package com.topviewclub.common.mq

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

const val RABBIT_SCHEMA_VERSION = "archive.rabbit.v2"

data class RabbitV2Envelope<T>(
    val schemaVersion: String,
    val eventId: String,
    val workflowId: String,
    val causationId: String?,
    val idempotencyKey: String,
    val messageType: String,
    val tenantCode: String,
    val mediaType: String,
    val operation: String,
    val occurredAt: String,
    val producer: RabbitProducer,
    val business: RabbitBusiness,
    val payload: T,
)

data class RabbitProducer(
    val service: String,
)

data class RabbitBusiness(
    val jobId: Long,
    val jobRunId: String,
)

data class CaptureWindow(
    val startsAt: String,
    val endsAt: String,
)

data class CollectionOptions(
    val includeText: Boolean = false,
    val includeImages: Boolean = false,
    val includeVideos: Boolean = false,
    val includeAudios: Boolean = false,
)

data class QrImageRef(
    val objectKey: String,
    val sha256: String,
    val contentType: String,
    val sizeBytes: Long,
)

data class GzhAccount(
    val name: String,
    val entryUrl: String? = null,
    val qrImage: QrImageRef? = null,
)

data class GzhAutoBtaPayload(
    val account: GzhAccount,
    val captureWindow: CaptureWindow,
    val collectionOptions: CollectionOptions = CollectionOptions(),
)

data class GzhAutoAtdPayload(
    val accountName: String,
    val status: String,
    val captureWindow: CaptureWindow,
    val seedArticles: List<SeedArticle>,
    val error: AtdError? = null,
)

data class SeedArticle(
    val url: String,
    val title: String? = null,
    val publishedAt: String? = null,
)

data class AtdError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

typealias GzhAutoBta = RabbitV2Envelope<GzhAutoBtaPayload>
typealias GzhAutoAtd = RabbitV2Envelope<GzhAutoAtdPayload>

/**
 * 一条公众号任务从 MQ 到 Android 无障碍流程的上下文。
 * completion 只有在 ATD 成功确认发布后才完成，消费者随后才会 ACK BTA。
 */
class RabbitTaskContext(
    val input: GzhAutoBta,
    val sourceVirtualHost: String,
    private val publishResult: suspend (
        status: String,
        seedArticles: List<SeedArticle>,
        error: AtdError?,
    ) -> Unit,
) {
    val completion = CompletableDeferred<Unit>()

    private val terminalClaimed = AtomicBoolean(false)

    suspend fun publishTerminal(
        status: String,
        seedArticles: List<SeedArticle> = emptyList(),
        error: AtdError? = null,
    ) {
        if (!terminalClaimed.compareAndSet(false, true)) {
            // 另一个结束路径正在发布同一任务的终态；等待它完成，避免调用方提前 ACK。
            completion.await()
            return
        }

        try {
            publishResult(status, seedArticles, error)
            completion.complete(Unit)
        } catch (t: Throwable) {
            completion.completeExceptionally(t)
            throw t
        }
    }
}
