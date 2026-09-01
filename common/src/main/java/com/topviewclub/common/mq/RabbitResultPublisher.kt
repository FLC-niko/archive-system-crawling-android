package com.topviewclub.common.mq

import com.topviewclub.common.bean.GzhAutoFromBackend
import com.topviewclub.common.bean.GzhAutoToBigData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 将一条 BTA 的公众号采集结果聚合为一条 V2 ATD。
 * 结果发布使用接收 BTA 的 manager，因此不会跨 vhost 回传。
 */
class RabbitResultPublisher(
    private val clientManager: RabbitMQClientManager,
    private val defaultResultEventId: String? = null,
) {
    suspend fun publish(
        input: GzhAutoBta,
        status: String,
        seedArticles: List<SeedArticle> = emptyList(),
        error: AtdError? = null,
        eventId: String = defaultResultEventId ?: "evt_${UUID.randomUUID()}",
    ) {
        val message = buildMessage(input, status, seedArticles, error, eventId)
        clientManager.publishJson(
            routingKey = clientManager.resultRoutingKey,
            payload = message,
            eventId = message.eventId,
            workflowId = message.workflowId,
            producerService = "android-worker",
        )
    }

    internal fun buildMessage(
        input: GzhAutoBta,
        status: String,
        seedArticles: List<SeedArticle> = emptyList(),
        error: AtdError? = null,
        eventId: String = defaultResultEventId ?: "evt_${UUID.randomUUID()}",
    ): GzhAutoAtd {
        require(status in setOf("SUCCEEDED", "EMPTY", "FAILED")) {
            "不支持的 Android ATD 状态: $status"
        }
        require(status != "SUCCEEDED" || seedArticles.isNotEmpty()) {
            "SUCCEEDED ATD 必须至少包含一篇种子文章"
        }
        require(status != "EMPTY" || seedArticles.isEmpty()) {
            "EMPTY ATD 不允许包含种子文章"
        }
        require(status != "FAILED" || error != null) {
            "FAILED ATD 必须包含错误信息"
        }

        val deduplicated = seedArticles
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
        val normalizedStatus = if (status == "SUCCEEDED" && deduplicated.isEmpty()) "EMPTY" else status
        require(normalizedStatus != "SUCCEEDED" || deduplicated.isNotEmpty()) {
            "SUCCEEDED ATD 必须至少包含一篇有效种子文章"
        }

        return RabbitV2Envelope(
            schemaVersion = RABBIT_SCHEMA_VERSION,
            eventId = eventId,
            workflowId = input.workflowId,
            causationId = input.eventId,
            idempotencyKey = "${input.workflowId}:ATD",
            messageType = "RESULT",
            tenantCode = input.tenantCode,
            mediaType = input.mediaType,
            operation = input.operation,
            occurredAt = nowIsoUtc(),
            producer = RabbitProducer("android-worker"),
            business = input.business,
            payload = GzhAutoAtdPayload(
                accountName = input.payload.account.name,
                status = normalizedStatus,
                captureWindow = input.payload.captureWindow,
                seedArticles = deduplicated,
                error = error,
            ),
        )
    }

    private fun nowIsoUtc(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        Locale.US,
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}

fun buildLegacyGzhResults(
    input: GzhAutoFromBackend,
    seedArticles: List<SeedArticle>,
    correlationId: () -> String = { UUID.randomUUID().toString() },
): List<GzhAutoToBigData> = seedArticles.map { article ->
    GzhAutoToBigData(
        queueName = input.queueName,
        gzhName = input.gzhName,
        tempTimeStamp = input.tempTimeStamp.copyOf(),
        url = article.url,
        jobId = input.jobId,
        correlationId = correlationId(),
    )
}
