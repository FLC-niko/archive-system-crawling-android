package com.topviewclub.common.mq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RabbitTopologyTest {
    @Test
    fun xdagUsesDeployedArchiveV2Topology() {
        assertEquals("archive.new-media.v2", XDAG_RABBIT_TOPOLOGY.exchange)
        assertEquals("archive.new-media.retry.v2", XDAG_RABBIT_TOPOLOGY.retryExchange)
        assertEquals("archive.gzh.auto.bta.v2", XDAG_RABBIT_TOPOLOGY.taskQueue)
        assertEquals("archive.gzh.auto.atd.v2", XDAG_RABBIT_TOPOLOGY.resultQueue)
        assertEquals("archive.gzh.auto.atd.v2.dlq", XDAG_RABBIT_TOPOLOGY.deadLetterQueue)
        assertTrue(XDAG_RABBIT_TOPOLOGY.brokerManagedTopology)
        assertEquals(
            listOf("archive.gzh.auto.dtb.v2", "archive.gzh.auto.dtb.v2.retry"),
            XDAG_RABBIT_TOPOLOGY.auxiliaryQueues,
        )
    }

    @Test
    fun legacyVhostsKeepExistingTopology() {
        assertEquals("new-media-backend", LEGACY_RABBIT_TOPOLOGY.exchange)
        assertEquals("gzh-auto-BTA-queue", LEGACY_RABBIT_TOPOLOGY.taskQueue)
        assertEquals("gzh-auto-ATD-queue", LEGACY_RABBIT_TOPOLOGY.resultQueue)
        assertEquals(LEGACY_RABBIT_TOPOLOGY, rabbitTopologyFor(TEST_VIRTUAL_HOST))
    }

    @Test
    fun productionTasksHaveHighestRabbitPriority() {
        assertTrue(rabbitTaskPriorityFor(VIRTUAL_HOST) > rabbitTaskPriorityFor(XDAG_VIRTUAL_HOST))
        assertTrue(rabbitTaskPriorityFor(XDAG_VIRTUAL_HOST) > rabbitTaskPriorityFor(THDAG_VIRTUAL_HOST))
        assertTrue(rabbitTaskPriorityFor(THDAG_VIRTUAL_HOST) > rabbitTaskPriorityFor(TEST_VIRTUAL_HOST))
    }
}
