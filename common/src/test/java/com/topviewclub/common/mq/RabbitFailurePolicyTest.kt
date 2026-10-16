package com.topviewclub.common.mq

import org.junit.Assert.assertEquals
import org.junit.Test

class RabbitFailurePolicyTest {
    @Test
    fun `dead letter retries park after retry budget is exhausted`() {
        assertEquals(
            RabbitFailureDisposition.PARK,
            rabbitFailureDisposition(
                topology = LEGACY_RABBIT_TOPOLOGY,
                deliveryQueue = LEGACY_RABBIT_TOPOLOGY.deadLetterQueue,
                retryCount = MAX_TASK_RETRY,
            ),
        )
    }

    @Test
    fun `ordinary task failures still enter dead letter queue`() {
        assertEquals(
            RabbitFailureDisposition.DEAD_LETTER,
            rabbitFailureDisposition(
                topology = LEGACY_RABBIT_TOPOLOGY,
                deliveryQueue = LEGACY_RABBIT_TOPOLOGY.taskQueue,
                retryCount = MAX_TASK_RETRY,
            ),
        )
    }

    @Test
    fun `retry budget is applied before parking`() {
        assertEquals(
            RabbitFailureDisposition.RETRY,
            rabbitFailureDisposition(
                topology = LEGACY_RABBIT_TOPOLOGY,
                deliveryQueue = LEGACY_RABBIT_TOPOLOGY.deadLetterQueue,
                retryCount = MAX_TASK_RETRY - 1,
            ),
        )
    }

    @Test
    fun `broker managed topology keeps broker dead letter routing`() {
        assertEquals(
            RabbitFailureDisposition.DEAD_LETTER,
            rabbitFailureDisposition(
                topology = XDAG_RABBIT_TOPOLOGY,
                deliveryQueue = XDAG_RABBIT_TOPOLOGY.deadLetterQueue,
                retryCount = MAX_TASK_RETRY,
            ),
        )
    }
}
