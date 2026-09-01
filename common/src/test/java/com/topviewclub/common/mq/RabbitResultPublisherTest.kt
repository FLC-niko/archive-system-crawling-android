package com.topviewclub.common.mq

import com.topviewclub.common.bean.GzhAutoFromBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RabbitResultPublisherTest {
    private val input = RabbitV2Envelope(
        schemaVersion = RABBIT_SCHEMA_VERSION,
        eventId = "evt-bta-1",
        workflowId = "wf-1",
        causationId = null,
        idempotencyKey = "wf-1:BTA",
        messageType = "COMMAND",
        tenantCode = "tenant-a",
        mediaType = "WECHAT_OFFICIAL_ACCOUNT",
        operation = "AUTO_CRAWL",
        occurredAt = "2026-08-10T00:00:00Z",
        producer = RabbitProducer("backend"),
        business = RabbitBusiness(123L, "run-1"),
        payload = GzhAutoBtaPayload(
            account = GzhAccount("测试公众号"),
            captureWindow = CaptureWindow("2026-08-10", "2026-08-13"),
        ),
    )

    @Test
    fun buildsOneDeduplicatedAtd() {
        val result = RabbitResultPublisher(
            RabbitMQClientManager(),
            defaultResultEventId = "evt-atd-1",
        ).buildMessage(
            input = input,
            status = "SUCCEEDED",
            seedArticles = listOf(
                SeedArticle(" "),
                SeedArticle("https://example.com/1"),
                SeedArticle("https://example.com/1"),
                SeedArticle("https://example.com/2"),
            ),
        )

        assertEquals("evt-atd-1", result.eventId)
        assertEquals("evt-bta-1", result.causationId)
        assertEquals("wf-1:ATD", result.idempotencyKey)
        assertEquals(listOf("https://example.com/1", "https://example.com/2"), result.payload.seedArticles.map { it.url })
        assertTrue(result.payload.seedArticles.none { it.url.isBlank() })
    }

    @Test
    fun buildsEmptyAtdWithoutArticles() {
        val result = RabbitResultPublisher(RabbitMQClientManager()).buildMessage(
            input = input,
            status = "EMPTY",
            eventId = "evt-atd-empty",
        )

        assertEquals("EMPTY", result.payload.status)
        assertTrue(result.payload.seedArticles.isEmpty())
    }

    @Test
    fun legacyTaskBuildsLegacyArticleListInsteadOfV2Envelope() {
        val legacy = GzhAutoFromBackend(
            queueName = "gzh-auto-BTA-queue",
            image = "base64",
            gzhName = "旧公众号",
            tempTimeStamp = arrayOf("2026-08-10", "2026-08-13"),
            url = "",
            jobId = 7L,
            correlationId = "legacy-1",
        )

        val result = buildLegacyGzhResults(
            input = legacy,
            seedArticles = listOf(SeedArticle("https://example.com/article")),
            correlationId = { "article-correlation" },
        )

        assertEquals(1, result.size)
        assertEquals("gzh-auto-BTA-queue", result.single().queueName)
        assertEquals("旧公众号", result.single().gzhName)
        assertEquals("https://example.com/article", result.single().url)
        assertEquals("article-correlation", result.single().correlationId)
    }
}
