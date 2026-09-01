package com.topviewclub.common.mq

import com.google.gson.Gson
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.ReturnListener
import com.topviewclub.common.log.logRabbit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 一个 RabbitMQ vhost 的连接、拓扑、生产者和消费者管理器。
 *
 * 每个 DeliveryContext 都保存自己的 consumer channel 与 deliveryTag；所有 channel
 * 操作均串行化，避免旧实现中共享 deliveryTag 导致 ACK 错消息。
 */
class RabbitMQClientManager(
    private val defaultVirtualHost: String = VIRTUAL_HOST,
    private val configuredTopology: RabbitTopology = rabbitTopologyFor(defaultVirtualHost),
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectMutex = Mutex()
    private val channelOperationMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val gson = Gson()

    @Volatile
    private var connection: Connection? = null

    @Volatile
    private var publishChannel: Channel? = null

    @Volatile
    private var connectionSettings: ConnectionSettings? = null

    @Volatile
    private var activeTopology: RabbitTopology = configuredTopology

    /** 保存注册信息，连接断开后会在新连接上恢复消费者。 */
    private val consumerSpecs = ConcurrentHashMap<String, ConsumerSpec>()

    data class ConnectionSettings(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val virtualHost: String,
    )

    val configuredVirtualHost: String?
        get() = connectionSettings?.virtualHost

    val resultRoutingKey: String
        get() = activeTopology.resultRoutingKey

    val resultQueue: String
        get() = activeTopology.resultQueue

    suspend fun connect(
        host: String = HOST_NAME,
        port: Int = PORT_NAME,
        username: String = USER_NAME,
        password: String = PASSWORD,
        virtualHost: String = defaultVirtualHost,
    ) {
        val settings = ConnectionSettings(host, port, username, password, virtualHost)
        connectMutex.withLock {
            check(!closed.get()) { "RabbitMQ manager 已关闭，不能重新连接" }
            if (isConnected() && connectionSettings == settings) return@withLock

            connectionSettings = settings
            activeTopology = if (virtualHost == defaultVirtualHost) {
                configuredTopology
            } else {
                rabbitTopologyFor(virtualHost)
            }
            closeConnectionQuietly()

            val factory = ConnectionFactory().apply {
                this.host = settings.host
                this.port = settings.port
                this.username = settings.username
                this.password = settings.password
                this.virtualHost = settings.virtualHost
                // 重连节奏由 VhostConnectionSupervisor 统一管理。
                this.isAutomaticRecoveryEnabled = false
                this.isTopologyRecoveryEnabled = false
                this.connectionTimeout = 10_000
                this.handshakeTimeout = 10_000
            }

            val newConnection = factory.newConnection("aaos-${settings.virtualHost}")
            val newPublishChannel = try {
                newConnection.createChannel().also { channel ->
                    channel.confirmSelect()
                    declareTopology(channel, activeTopology)
                }
            } catch (e: Exception) {
                runCatching { newConnection.close() }
                throw e
            }

            newConnection.addShutdownListener { cause ->
                if (connection === newConnection) {
                    connection = null
                    publishChannel = null
                    consumerSpecs.values.forEach { it.consumer.clearRuntime() }
                    if (!closed.get()) {
                        logRabbit("vhost=${settings.virtualHost} 连接断开: ${cause.message}")
                    }
                }
            }
            connection = newConnection
            publishChannel = newPublishChannel

            try {
                consumerSpecs.values.forEach { startConsumerOnConnection(it, newConnection, activeTopology) }
            } catch (e: Exception) {
                closeConnectionQuietly()
                throw e
            }
            logRabbit("vhost=${settings.virtualHost} 连接成功")
        }
    }

    fun isConnected(): Boolean =
        connection?.isOpen == true && publishChannel?.isOpen == true

    /** 注册生产者；send() 返回前已经完成 publisher confirm。 */
    inner class Producer(
        private val exchangeName: String,
        private val exchangeType: String,
        private val routingKey: String,
        private val queueName: String,
    ) {
        suspend fun send(message: String) {
            val exchange = if (exchangeName == "server-exchange") "" else exchangeName
            val actualRoutingKey = if (exchange.isBlank()) queueName else routingKey
            publishInternal(
                exchange = exchange,
                exchangeType = exchangeType,
                routingKey = actualRoutingKey,
                queueName = queueName,
                body = message.toByteArray(Charsets.UTF_8),
                eventId = null,
                workflowId = null,
                producerService = null,
                headers = emptyMap(),
            )
        }
    }

    /**
     * 兼容旧版 ask/reject API。新链路应让回调正常返回，由 manager 按具体 DeliveryContext
     * ACK；即使旧代码调用 ask()，也不会重复确认。
     */
    inner class Consumer internal constructor(
        internal val queueName: String,
        private val prefetch: Int,
        private val onMessageReceived: suspend (String, DeliveryContext) -> Unit,
    ) {
        @Volatile
        internal var channel: Channel? = null

        @Volatile
        internal var consumerTag: String? = null

        @Volatile
        private var lastDelivery: DeliveryContext? = null

        internal fun setRuntime(channel: Channel, consumerTag: String) {
            this.channel = channel
            this.consumerTag = consumerTag
        }

        internal fun clearRuntime() {
            channel = null
            consumerTag = null
        }

        internal fun receive(delivery: Delivery, consumerTag: String, deliveryChannel: Channel) {
            val context = DeliveryContext(
                manager = this@RabbitMQClientManager,
                channel = deliveryChannel,
                consumerTag = consumerTag,
                queue = queueName,
                delivery = delivery,
            )
            lastDelivery = context
            scope.launch {
                try {
                    onMessageReceived(context.parseBodyAsString(), context)
                    acknowledge(context)
                } catch (e: CancellationException) {
                    if (!closed.get()) runCatching { reject(context, requeue = true) }
                    throw e
                } catch (e: RabbitTaskFormatException) {
                    logRabbit("queue=$queueName 消息格式非法，转入死信: ${e.message}")
                    moveToDeadLetter(context, e)
                } catch (e: Exception) {
                    logRabbit("queue=$queueName 消费失败: ${e.message}")
                    retryOrDeadLetter(context, e)
                }
            }
        }

        /** 旧接口兼容：只确认最近收到的消息，且仍绑定其真实 deliveryTag。 */
        fun ask() {
            lastDelivery?.let { context ->
                scope.launch { runCatching { acknowledge(context) } }
            }
        }

        /** 旧接口兼容：只拒绝最近收到的消息并重新入队。 */
        fun reject() {
            lastDelivery?.let { context ->
                scope.launch { runCatching { reject(context, requeue = true) } }
            }
        }

        fun close() {
            removeConsumer(this)
        }
    }

    fun registerProducer(
        exchangeName: String,
        exchangeType: String,
        routingKey: String,
        queueName: String,
    ): Producer = Producer(exchangeName, exchangeType, routingKey, queueName)

    fun registerConsumer(
        exchangeName: String,
        exchangeType: String,
        routingKey: String,
        queueName: String,
        onMessageReceived: (String) -> Unit,
    ): Consumer = registerConsumer(
        exchangeName = exchangeName,
        exchangeType = exchangeType,
        routingKey = routingKey,
        queueName = queueName,
        onMessageReceived = { body, _ -> onMessageReceived(body) },
    )

    fun registerConsumer(
        exchangeName: String,
        exchangeType: String,
        routingKey: String,
        queueName: String,
        prefetch: Int = 1,
        onMessageReceived: suspend (String, DeliveryContext) -> Unit,
    ): Consumer {
        val existing = consumerSpecs[queueName]
        if (existing != null) return existing.consumer

        val consumer = Consumer(
            queueName = queueName,
            prefetch = prefetch.coerceAtLeast(1),
            onMessageReceived = onMessageReceived,
        )
        val spec = ConsumerSpec(
            queueName = queueName,
            exchangeName = exchangeName,
            exchangeType = exchangeType,
            routingKey = routingKey,
            prefetch = prefetch.coerceAtLeast(1),
            consumer = consumer,
        )
        val raced = consumerSpecs.putIfAbsent(queueName, spec)
        if (raced != null) return raced.consumer

        if (isConnected()) {
            scope.launch {
                connectMutex.withLock {
                    if (isConnected() && consumerSpecs[queueName] === spec) {
                        runCatching {
                            startConsumerOnConnection(spec, connection!!, activeTopology)
                        }.onFailure {
                            logRabbit("queue=$queueName 启动消费者失败: ${it.message}")
                        }
                    }
                }
            }
        }
        return consumer
    }

    suspend fun publishJson(queue: String, payload: Any) {
        publish(
            routingKey = routingForQueue(queue),
            body = gson.toJson(payload).toByteArray(Charsets.UTF_8),
        )
    }

    suspend fun publishJson(
        routingKey: String,
        payload: Any,
        eventId: String,
        workflowId: String,
        producerService: String,
        headers: Map<String, Any?> = emptyMap(),
    ) {
        publish(
            routingKey = routingKey,
            body = gson.toJson(payload).toByteArray(Charsets.UTF_8),
            eventId = eventId,
            workflowId = workflowId,
            producerService = producerService,
            headers = headers,
        )
    }

    suspend fun publish(
        routingKey: String,
        body: ByteArray,
        eventId: String? = null,
        workflowId: String? = null,
        producerService: String? = null,
        headers: Map<String, Any?> = emptyMap(),
    ) {
        publishInternal(
            exchange = activeTopology.exchange,
            exchangeType = activeTopology.exchangeType,
            routingKey = routingKey,
            queueName = queueForRoutingKey(routingKey),
            body = body,
            eventId = eventId,
            workflowId = workflowId,
            producerService = producerService,
            headers = headers,
        )
    }

    private suspend fun publishInternal(
        exchange: String,
        exchangeType: String,
        routingKey: String,
        queueName: String,
        body: ByteArray,
        eventId: String?,
        workflowId: String?,
        producerService: String?,
        headers: Map<String, Any?>,
    ) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                channelOperationMutex.withLock {
                    val channel = publishChannel?.takeIf { it.isOpen }
                        ?: throw IOException("RabbitMQ publish channel 不可用")
                    if (isBrokerManagedQueue(queueName)) {
                        channel.queueDeclarePassive(queueName)
                    } else {
                        channel.queueDeclare(queueName, true, false, false, null)
                    }
                    publishAndConfirm(
                        channel = channel,
                        exchange = exchange,
                        exchangeType = exchangeType,
                        routingKey = routingKey,
                        properties = properties(eventId, workflowId, producerService, headers),
                        body = body,
                        declareExchange = !isBrokerManagedQueue(queueName),
                    )
                }
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) delay((attempt + 1) * 1_000L)
            }
        }
        throw lastError ?: IOException("RabbitMQ 发布失败")
    }

    private fun declareTopology(channel: Channel, topology: RabbitTopology) {
        if (topology.brokerManagedTopology) {
            // xdag 的 exchange、业务队列、retry 队列和 DLQ 都由 broker 统一部署；
            // 这里只做 passive 校验，不声明或重新绑定，避免客户端改变管理台中的拓扑。
            channel.exchangeDeclarePassive(topology.exchange)
            verifyBrokerManagedQueue(channel, topology.taskQueue)
            verifyBrokerManagedQueue(channel, topology.resultQueue)
            verifyBrokerManagedQueue(channel, topology.deadLetterQueue)
            topology.retryExchange?.let { retryExchange ->
                channel.exchangeDeclarePassive(retryExchange)
            }
            topology.taskRetryQueue?.let { queue ->
                verifyBrokerManagedQueue(channel, queue)
            }
            topology.resultRetryQueue?.let { queue ->
                verifyBrokerManagedQueue(channel, queue)
            }
            topology.auxiliaryQueues.forEach { queue ->
                verifyBrokerManagedQueue(channel, queue)
            }
        } else if (topology == LEGACY_RABBIT_TOPOLOGY) {
            channel.queueDeclare(SERVER_STATUS_QUEUE, true, false, false, null)
        }
    }

    private fun declareBoundQueue(channel: Channel, exchange: String, queue: String, routingKey: String) {
        channel.queueDeclare(queue, true, false, false, null)
        channel.queueBind(queue, exchange, routingKey)
    }

    private fun verifyBrokerManagedQueue(channel: Channel, queue: String) {
        channel.queueDeclarePassive(queue)
    }

    private fun startConsumerOnConnection(
        spec: ConsumerSpec,
        currentConnection: Connection,
        topology: RabbitTopology,
    ) {
        if (spec.consumer.channel?.isOpen == true && spec.consumer.consumerTag != null) return
        check(currentConnection.isOpen) { "RabbitMQ connection 已关闭" }

        val channel = currentConnection.createChannel()
        try {
            channel.basicQos(spec.prefetch)
            if (isBrokerManagedQueue(spec.queueName, topology)) {
                channel.exchangeDeclarePassive(spec.exchangeName)
                channel.queueDeclarePassive(spec.queueName)
            } else {
                channel.exchangeDeclare(spec.exchangeName, spec.exchangeType, true)
                channel.queueDeclare(spec.queueName, true, false, false, null)
                channel.queueBind(spec.queueName, spec.exchangeName, spec.routingKey)
            }
            val deliverCallback = DeliverCallback { consumerTag, delivery ->
                spec.consumer.receive(delivery, consumerTag, channel)
            }
            val cancelCallback = CancelCallback { tag ->
                spec.consumer.clearRuntime()
                if (!closed.get()) logRabbit("queue=${spec.queueName} 消费者取消: $tag")
            }
            val consumerTag = channel.basicConsume(spec.queueName, false, deliverCallback, cancelCallback)
            spec.consumer.setRuntime(channel, consumerTag)
        } catch (e: Exception) {
            runCatching { channel.close() }
            throw e
        }
    }

    private suspend fun acknowledge(delivery: DeliveryContext) {
        channelOperationMutex.withLock {
            if (!delivery.terminal.compareAndSet(false, true)) return@withLock
            try {
                check(delivery.channel.isOpen) { "RabbitMQ consumer channel 已关闭，无法 ACK" }
                delivery.channel.basicAck(delivery.deliveryTag, false)
            } catch (e: Exception) {
                delivery.terminal.set(false)
                throw e
            }
        }
    }

    private suspend fun reject(delivery: DeliveryContext, requeue: Boolean) {
        channelOperationMutex.withLock {
            if (!delivery.terminal.compareAndSet(false, true)) return@withLock
            try {
                if (delivery.channel.isOpen) {
                    delivery.channel.basicNack(delivery.deliveryTag, false, requeue)
                }
            } catch (e: Exception) {
                delivery.terminal.set(false)
                throw e
            }
        }
    }

    private suspend fun moveToDeadLetter(delivery: DeliveryContext, error: Exception) {
        try {
            channelOperationMutex.withLock {
                val channel = publishChannel?.takeIf { it.isOpen }
                    ?: throw IOException("RabbitMQ publish channel 不可用，无法转死信")
                val topology = activeTopology
                val headers = delivery.headers.toMutableMap().apply {
                    put("x-last-error", error.javaClass.simpleName)
                    put("x-original-queue", delivery.queue)
                }
                publishAndConfirm(
                    channel = channel,
                    exchange = topology.exchange,
                    exchangeType = topology.exchangeType,
                    routingKey = topology.deadLetterRoutingKey,
                    properties = delivery.delivery.properties.builder().headers(headers).build(),
                    body = delivery.body,
                    declareExchange = !topology.brokerManagedTopology,
                )
                acknowledgeLocked(delivery)
            }
        } catch (e: Exception) {
            logRabbit("消息转死信失败，重新入队: ${e.message}")
            runCatching { reject(delivery, requeue = true) }
        }
    }

    private suspend fun retryOrDeadLetter(delivery: DeliveryContext, error: Exception) {
        try {
            val topology = activeTopology
            val retryCount = delivery.retryCount
            if (!topology.brokerManagedTopology &&
                retryCount < MAX_TASK_RETRY &&
                TASK_RETRY_DELAY_MS > 0
            ) {
                delay(TASK_RETRY_DELAY_MS)
            }
            channelOperationMutex.withLock {
                val channel = publishChannel?.takeIf { it.isOpen }
                    ?: throw IOException("RabbitMQ publish channel 不可用，无法安排重试")
                val headers = delivery.headers.toMutableMap().apply {
                    put("x-last-error", error.javaClass.simpleName)
                }
                if (retryCount < MAX_TASK_RETRY && topology.taskRetryQueue != null) {
                    headers["x-retry-count"] = retryCount + 1
                    val retryExchange = topology.retryExchange ?: topology.exchange
                    val retryRoutingKey = topology.taskRetryRoutingKey ?: topology.taskRetryQueue
                    publishAndConfirm(
                        channel = channel,
                        exchange = retryExchange,
                        exchangeType = topology.exchangeType,
                        routingKey = retryRoutingKey,
                        properties = delivery.delivery.properties.builder().headers(headers).build(),
                        body = delivery.body,
                        declareExchange = !topology.brokerManagedTopology,
                    )
                    acknowledgeLocked(delivery)
                    logRabbit("queue=${delivery.queue} 已安排第 ${retryCount + 1} 次重试")
                } else if (retryCount < MAX_TASK_RETRY) {
                    headers["x-retry-count"] = retryCount + 1
                    publishAndConfirm(
                        channel = channel,
                        exchange = topology.exchange,
                        exchangeType = topology.exchangeType,
                        routingKey = topology.taskRoutingKey,
                        properties = delivery.delivery.properties.builder().headers(headers).build(),
                        body = delivery.body,
                        declareExchange = !topology.brokerManagedTopology,
                    )
                    acknowledgeLocked(delivery)
                    logRabbit("queue=${delivery.queue} 已安排第 ${retryCount + 1} 次重试")
                } else {
                    headers["x-original-queue"] = delivery.queue
                    publishAndConfirm(
                        channel = channel,
                        exchange = topology.exchange,
                        exchangeType = topology.exchangeType,
                        routingKey = topology.deadLetterRoutingKey,
                        properties = delivery.delivery.properties.builder().headers(headers).build(),
                        body = delivery.body,
                        declareExchange = !topology.brokerManagedTopology,
                    )
                    acknowledgeLocked(delivery)
                    logRabbit("queue=${delivery.queue} 重试次数耗尽，已转入 ${topology.deadLetterQueue}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logRabbit("失败消息替代发布失败，重新入队: ${e.message}")
            runCatching { reject(delivery, requeue = true) }
        }
    }

    private fun acknowledgeLocked(delivery: DeliveryContext) {
        if (!delivery.terminal.compareAndSet(false, true)) return
        try {
            check(delivery.channel.isOpen) { "RabbitMQ consumer channel 已关闭，无法 ACK" }
            delivery.channel.basicAck(delivery.deliveryTag, false)
        } catch (e: Exception) {
            delivery.terminal.set(false)
            throw e
        }
    }

    private fun properties(
        eventId: String?,
        workflowId: String?,
        producerService: String?,
        headers: Map<String, Any?>,
    ): AMQP.BasicProperties {
        val normalizedHeaders = linkedMapOf<String, Any>("x-schema-version" to RABBIT_SCHEMA_VERSION)
        if (producerService != null) normalizedHeaders["x-producer-service"] = producerService
        headers.forEach { (key, value) -> if (value != null) normalizedHeaders[key] = value }
        return AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .contentEncoding("utf-8")
            .type(RABBIT_SCHEMA_VERSION)
            .messageId(eventId)
            .correlationId(workflowId)
            .headers(normalizedHeaders)
            .deliveryMode(2)
            .build()
    }

    private fun publishAndConfirm(
        channel: Channel,
        exchange: String,
        exchangeType: String,
        routingKey: String,
        properties: AMQP.BasicProperties,
        body: ByteArray,
        declareExchange: Boolean = true,
    ) {
        if (declareExchange && exchange.isNotBlank()) {
            channel.exchangeDeclare(exchange, exchangeType, true)
        }
        val returned = AtomicReference<String?>(null)
        val returnListener = object : ReturnListener {
            override fun handleReturn(
                replyCode: Int,
                replyText: String,
                exchange: String,
                routingKey: String,
                properties: AMQP.BasicProperties,
                body: ByteArray,
            ) {
                returned.set("replyCode=$replyCode,text=$replyText,routingKey=$routingKey")
            }
        }
        channel.addReturnListener(returnListener)
        try {
            channel.basicPublish(exchange, routingKey, true, properties, body)
            channel.waitForConfirmsOrDie(5_000)
            returned.get()?.let { throw IOException("RabbitMQ returned message: $it") }
        } finally {
            channel.removeReturnListener(returnListener)
        }
    }

    private fun removeConsumer(consumer: Consumer) {
        val spec = consumerSpecs[consumer.queueName]
        if (spec?.consumer === consumer) consumerSpecs.remove(consumer.queueName, spec)
        consumer.clearRuntime()
    }

    private fun closeConnectionQuietly() {
        consumerSpecs.values.forEach { it.consumer.clearRuntime() }
        runCatching { publishChannel?.close() }
        runCatching { connection?.close() }
        publishChannel = null
        connection = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        consumerSpecs.values.forEach { it.consumer.clearRuntime() }
        consumerSpecs.clear()
        scope.cancel()
        closeConnectionQuietly()
    }

    private fun routingForQueue(queue: String): String = when (queue) {
        activeTopology.taskQueue -> activeTopology.taskRoutingKey
        activeTopology.resultQueue -> activeTopology.resultRoutingKey
        activeTopology.deadLetterQueue -> activeTopology.deadLetterRoutingKey
        else -> queue
    }

    private fun isBrokerManagedQueue(
        queue: String,
        topology: RabbitTopology = activeTopology,
    ): Boolean {
        if (!topology.brokerManagedTopology) return false
        return queue == topology.taskQueue ||
            queue == topology.resultQueue ||
            queue == topology.deadLetterQueue ||
            queue == topology.taskRetryQueue ||
            queue == topology.resultRetryQueue ||
            topology.auxiliaryQueues.any { it == queue }
    }

    private fun queueForRoutingKey(routingKey: String): String = when (routingKey) {
        activeTopology.taskRoutingKey -> activeTopology.taskQueue
        activeTopology.resultRoutingKey -> activeTopology.resultQueue
        activeTopology.deadLetterRoutingKey -> activeTopology.deadLetterQueue
        else -> routingKey
    }

    private data class ConsumerSpec(
        val queueName: String,
        val exchangeName: String,
        val exchangeType: String,
        val routingKey: String,
        val prefetch: Int,
        val consumer: Consumer,
    )

    class DeliveryContext internal constructor(
        private val manager: RabbitMQClientManager,
        internal val channel: Channel,
        val consumerTag: String,
        val queue: String,
        internal val delivery: Delivery,
    ) {
        internal val terminal = AtomicBoolean(false)
        val body: ByteArray get() = delivery.body
        val headers: Map<String, Any> get() = delivery.properties.headers ?: emptyMap()
        val correlationId: String? get() = delivery.properties.correlationId
        val deliveryTag: Long get() = delivery.envelope.deliveryTag
        val isRedeliver: Boolean get() = delivery.envelope.isRedeliver
        val sourceVirtualHost: String get() = manager.configuredVirtualHost ?: ""
        val retryCount: Int
            get() = headers["x-retry-count"]?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        fun parseBodyAsString(): String = body.toString(Charsets.UTF_8)

        suspend fun ack() = manager.acknowledge(this)

        suspend fun nack(requeue: Boolean = true) = manager.reject(this, requeue)
    }
}
