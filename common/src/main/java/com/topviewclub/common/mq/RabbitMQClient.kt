package com.topviewclub.common.mq

import com.topviewclub.common.bean.GzhAutoToBigData
import com.topviewclub.common.log.logRabbit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 端 RabbitMQ 入口。
 *
 * pro/test/thdag 保留旧 new-media-backend 拓扑兼容；xdag 使用 broker 已部署的
 * archive.new-media.v2 / archive.new-media.retry.v2 拓扑，并同样消费 BTA、回传 ATD。
 */
object RabbitMQClient {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val managers = linkedMapOf(
        VIRTUAL_HOST to RabbitMQClientManager(VIRTUAL_HOST, LEGACY_RABBIT_TOPOLOGY),
        TEST_VIRTUAL_HOST to RabbitMQClientManager(TEST_VIRTUAL_HOST, LEGACY_RABBIT_TOPOLOGY),
        XDAG_VIRTUAL_HOST to RabbitMQClientManager(XDAG_VIRTUAL_HOST, XDAG_RABBIT_TOPOLOGY),
        THDAG_VIRTUAL_HOST to RabbitMQClientManager(THDAG_VIRTUAL_HOST, LEGACY_RABBIT_TOPOLOGY),
    )

    private val gzhConsumers = ConcurrentHashMap<String, RabbitMQClientManager.Consumer>()
    private var supervisor: VhostConnectionSupervisor? = null
    private var started = false

    // 旧调用方兼容字段；新公众号链路不依赖这些全局变量。
    var videoCorrelationId: String? = " "
    var gzhCorrelationId: String? = " "
    var gzhQueueName: String? = " "

    var consumerVideoFromBackend: RabbitMQClientManager.Consumer? = null
    var consumerSingleVideoFromBackend: RabbitMQClientManager.Consumer? = null
    var consumerGzhFromBackend: RabbitMQClientManager.Consumer? = null

    var producerGzhToBigData: RabbitMQClientManager.Producer? = null
    var producerVideoToBackend: RabbitMQClientManager.Producer? = null
    var producerSingleVideoToBackend: RabbitMQClientManager.Producer? = null
    var producerStatusToServer: RabbitMQClientManager.Producer? = null

    @Synchronized
    private fun ensureStarted() {
        if (started) return
        started = true

        if (PASSWORD.isBlank()) {
            logRabbit("未注入 RABBITMQ_PASSWORD，跳过 RabbitMQ 连接；请使用 -PRABBITMQ_PASSWORD=... 构建")
            return
        }

        supervisor = VhostConnectionSupervisor(
            bindings = managers.map { (vhost, manager) ->
                VhostConnectionSupervisor.VhostBinding(
                    name = vhost,
                    virtualHost = vhost,
                    manager = manager,
                )
            },
            initialRetryIntervalMs = RECONNECT_INITIAL_DELAY_MS,
            maxRetryIntervalMs = RECONNECT_MAX_DELAY_MS,
        ).also { it.start(scope) }
        logRabbit("RabbitMQ supervisor 已启动: ${managers.keys}")
    }

    private fun managerFor(virtualHost: String): RabbitMQClientManager =
        managers[virtualHost]
            ?: throw IllegalArgumentException("未知 RabbitMQ vhost: $virtualHost")

    /** 新公众号结果发布器：必须传入接收任务的同一 vhost。 */
    fun resultPublisher(virtualHost: String, resultEventId: String): RabbitResultPublisher =
        RabbitResultPublisher(managerFor(virtualHost), resultEventId)

    /**
     * 旧版 BTA 必须继续向原 vhost 的旧 ATD 队列发布 List<GzhAutoToBigData>，不能把
     * archive.rabbit.v2 envelope 混入旧队列。超过 20 条时保持原实现的二段发布行为。
     */
    suspend fun publishLegacyGzhResult(
        virtualHost: String,
        articles: List<GzhAutoToBigData>,
    ) {
        val manager = managerFor(virtualHost)
        if (articles.size <= 20) {
            manager.publishJson(manager.resultQueue, articles)
            return
        }

        val middle = articles.size / 2
        manager.publishJson(manager.resultQueue, articles.subList(0, middle))
        manager.publishJson(manager.resultQueue, articles.subList(middle, articles.size))
    }

    /** 初始化旧版 producer 字段，保留视频/状态接口兼容。 */
    fun prepareRabbitProducer() {
        ensureStarted()
        val manager = managerFor(VIRTUAL_HOST)
        producerGzhToBigData = manager.registerProducer(
            NEW_MEDIA_EXCHANGE,
            NEW_MEDIA_EXCHANGE_TYPE,
            GZH_ATD_ROUTING_KEY,
            GZH_ATD_QUEUE,
        )
        producerStatusToServer = manager.registerProducer(
            "",
            NEW_MEDIA_EXCHANGE_TYPE,
            SERVER_STATUS_QUEUE,
            SERVER_STATUS_QUEUE,
        )
    }

    /**
     * 注册 V2/legacy 公众号消费者到所有已配置 vhost。每个回调都收到自己的
     * DeliveryContext，回调正常返回后 manager 才会 ACK。
     */
    fun prepareGzhAutoConsumer(
        onMessageReceive: suspend (String, RabbitMQClientManager.DeliveryContext) -> Unit,
    ) {
        ensureStarted()
        managers.forEach { (vhost, manager) ->
            val topology = rabbitTopologyFor(vhost)
            val consumer = manager.registerConsumer(
                exchangeName = topology.exchange,
                exchangeType = topology.exchangeType,
                routingKey = topology.taskRoutingKey,
                queueName = topology.taskQueue,
                prefetch = 1,
                onMessageReceived = onMessageReceive,
            )
            gzhConsumers[vhost] = consumer
        }
        consumerGzhFromBackend = gzhConsumers[VIRTUAL_HOST]
    }

    /** 旧版单参数回调兼容入口。 */
    fun prepareGzhAutoConsumer(onMessageReceive: (String) -> Unit) {
        prepareGzhAutoConsumer { body, _ -> onMessageReceive(body) }
    }

    // 以下视频接口暂时继续使用 pro vhost 的旧拓扑，避免影响已有手动视频流程。
    fun prepareVideoAutoConsumer(onMessageReceive: (String) -> Unit) {
        ensureStarted()
        val topology = LEGACY_RABBIT_TOPOLOGY
        consumerVideoFromBackend = managerFor(VIRTUAL_HOST).registerConsumer(
            topology.exchange,
            topology.exchangeType,
            "video-auto-BTA-routing",
            "video-auto-BTA-queue",
            onMessageReceive,
        )
    }

    fun prepareVideoSingleConsumer(onMessageReceive: (String) -> Unit) {
        ensureStarted()
        val topology = LEGACY_RABBIT_TOPOLOGY
        consumerSingleVideoFromBackend = managerFor(VIRTUAL_HOST).registerConsumer(
            topology.exchange,
            topology.exchangeType,
            "video-single-BTA-routing",
            "video-single-BTA-queue",
            onMessageReceive,
        )
    }

    @Synchronized
    fun closeRabbitConfiguration() {
        supervisor?.stop()
        supervisor = null
        managers.values.forEach { it.close() }
        gzhConsumers.clear()
        consumerGzhFromBackend = null
        started = false
    }
}
