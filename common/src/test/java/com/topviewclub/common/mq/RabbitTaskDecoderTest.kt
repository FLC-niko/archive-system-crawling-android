package com.topviewclub.common.mq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RabbitTaskDecoderTest {
    @Test
    fun decodesV2Bta() {
        val body = """
            {
              "schemaVersion":"archive.rabbit.v2",
              "eventId":"evt-bta-1",
              "workflowId":"wf-1",
              "causationId":null,
              "idempotencyKey":"wf-1:BTA",
              "messageType":"COMMAND",
              "tenantCode":"tenant-a",
              "mediaType":"WECHAT_OFFICIAL_ACCOUNT",
              "operation":"AUTO_CRAWL",
              "occurredAt":"2026-08-10T00:00:00Z",
              "producer":{"service":"backend"},
              "business":{"jobId":123,"jobRunId":"run-1"},
              "payload":{
                "account":{
                  "name":"测试公众号",
                  "qrImage":{
                    "transport":"INLINE_BASE64",
                    "dataBase64":"cXItYnl0ZXM=",
                    "sha256":"256c71a1a5c9904f339078276acc7b056c2e307b81c70f4c5d44a910f8bfb639",
                    "contentType":"image/png",
                    "sizeBytes":8
                  }
                },
                "captureWindow":{"startsAt":"2026-08-10","endsAt":"2026-08-13"}
              }
            }
        """.trimIndent()

        val decoded = RabbitTaskDecoder.decode(body.toByteArray())

        assertTrue(decoded is RabbitTaskMessage.V2)
        val task = (decoded as RabbitTaskMessage.V2).task
        assertEquals("wf-1", task.workflowId)
        assertEquals("测试公众号", task.payload.account.name)
        assertEquals(123L, task.business.jobId)
        assertNotNull(task.payload.account.qrImage)
        assertEquals("INLINE_BASE64", task.payload.account.qrImage?.transport)
        assertEquals("cXItYnl0ZXM=", task.payload.account.qrImage?.dataBase64)
        assertEquals(8L, task.payload.account.qrImage?.sizeBytes)
    }

    @Test
    fun decodesLegacyBtaForCompatibility() {
        val body = """
            {
              "queueName":"gzh-auto-BTA-queue",
              "image":"",
              "gzhName":"旧公众号",
              "tempTimeStamp":["2026-08-10","2026-08-13"],
              "url":null,
              "jobId":7,
              "correlationId":"legacy-1"
            }
        """.trimIndent()

        val decoded = RabbitTaskDecoder.decode(body.toByteArray())
        assertTrue(decoded is RabbitTaskMessage.Legacy)
        val task = decoded.asV2()
        assertEquals("legacy-1:BTA", task.idempotencyKey)
        assertEquals("旧公众号", task.payload.account.name)
    }

    @Test(expected = RabbitTaskFormatException::class)
    fun rejectsUnsupportedSchema() {
        RabbitTaskDecoder.decode(
            """{"schemaVersion":"archive.rabbit.v1","payload":{}}""".toByteArray(),
        )
    }
}
