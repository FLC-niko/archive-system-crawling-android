package com.topviewclub.common.mq

import com.topviewclub.common.BuildConfig

/**
 * RabbitMQ 连接配置与 wechat-download-kt/config.yaml 对齐。
 *
 * 密码通过 -PRABBITMQ_PASSWORD=... 或 RABBITMQ_PASSWORD 注入，空密码时不会启动
 * RabbitMQ 连接，避免把凭据编译进仓库或在默认构建中误连生产环境。
 */
const val HOST_NAME = "rabbitmqamqp.topviewclub.cn"
const val PORT_NAME = 5672
const val USER_NAME = "admin"
val PASSWORD: String get() = BuildConfig.RABBITMQ_PASSWORD

const val VIRTUAL_HOST = "pro"
const val TEST_VIRTUAL_HOST = "test"
const val XDAG_VIRTUAL_HOST = "xdag"
const val THDAG_VIRTUAL_HOST = "thdag"

const val RECONNECT_INITIAL_DELAY_MS = 5_000L
const val RECONNECT_MAX_DELAY_MS = 60_000L
const val MAX_TASK_RETRY = 5
const val TASK_RETRY_DELAY_MS = 60_000L

const val NEW_MEDIA_EXCHANGE = "new-media-backend"
const val NEW_MEDIA_EXCHANGE_TYPE = "direct"
const val GZH_BTA_QUEUE = "gzh-auto-BTA-queue"
const val GZH_BTA_ROUTING_KEY = "gzh-auto-BTA-routing"
const val GZH_ATD_QUEUE = "gzh-auto-ATD-queue"
const val GZH_ATD_ROUTING_KEY = "gzh-auto-ATD-routing"
const val DEAD_MESSAGE_QUEUE = "dead-message-queue"
const val DEAD_MESSAGE_ROUTING_KEY = "dead-message-routing"
const val SERVER_STATUS_QUEUE = "server.queue.provideLog"

/** 一个 vhost 的真实业务拓扑。xdag 的整套拓扑由 broker 管理。 */
data class RabbitTopology(
    val exchange: String,
    val exchangeType: String,
    val retryExchange: String? = null,
    val taskQueue: String,
    val taskRoutingKey: String,
    val resultQueue: String,
    val resultRoutingKey: String,
    val taskRetryQueue: String? = null,
    val taskRetryRoutingKey: String? = null,
    val resultRetryQueue: String? = null,
    val resultRetryRoutingKey: String? = null,
    val deadLetterQueue: String,
    val deadLetterRoutingKey: String,
    val auxiliaryQueues: List<String> = emptyList(),
    val brokerManagedTopology: Boolean = false,
)

val LEGACY_RABBIT_TOPOLOGY = RabbitTopology(
    exchange = NEW_MEDIA_EXCHANGE,
    exchangeType = NEW_MEDIA_EXCHANGE_TYPE,
    taskQueue = GZH_BTA_QUEUE,
    taskRoutingKey = GZH_BTA_ROUTING_KEY,
    resultQueue = GZH_ATD_QUEUE,
    resultRoutingKey = GZH_ATD_ROUTING_KEY,
    deadLetterQueue = DEAD_MESSAGE_QUEUE,
    deadLetterRoutingKey = DEAD_MESSAGE_ROUTING_KEY,
)

/**
 * xdag RabbitMQ 管理台中已经部署的 archive v2 拓扑。
 * 所有队列是 broker 已部署的 durable classic queue；客户端只做 passive 校验并向
 * 已存在的 exchange 投递，不重复声明其参数，避免与管理台配置发生前置条件冲突。
 */
val XDAG_RABBIT_TOPOLOGY = RabbitTopology(
    exchange = "archive.new-media.v2",
    exchangeType = "direct",
    retryExchange = "archive.new-media.retry.v2",
    taskQueue = "archive.gzh.auto.bta.v2",
    taskRoutingKey = "archive.gzh.auto.bta.v2",
    resultQueue = "archive.gzh.auto.atd.v2",
    resultRoutingKey = "archive.gzh.auto.atd.v2",
    taskRetryQueue = "archive.gzh.auto.bta.v2.retry",
    taskRetryRoutingKey = "archive.gzh.auto.bta.v2.retry",
    resultRetryQueue = "archive.gzh.auto.atd.v2.retry",
    resultRetryRoutingKey = "archive.gzh.auto.atd.v2.retry",
    deadLetterQueue = "archive.gzh.auto.atd.v2.dlq",
    deadLetterRoutingKey = "archive.gzh.auto.atd.v2.dlq",
    auxiliaryQueues = listOf(
        "archive.gzh.auto.dtb.v2",
        "archive.gzh.auto.dtb.v2.retry",
    ),
    brokerManagedTopology = true,
)

fun rabbitTopologyFor(virtualHost: String): RabbitTopology =
    if (virtualHost == XDAG_VIRTUAL_HOST) XDAG_RABBIT_TOPOLOGY else LEGACY_RABBIT_TOPOLOGY

/** 同一台 Android 设备上的任务优先级；数值越大越先执行。 */
fun rabbitTaskPriorityFor(virtualHost: String): Int = when (virtualHost) {
    VIRTUAL_HOST -> 400
    XDAG_VIRTUAL_HOST -> 300
    THDAG_VIRTUAL_HOST -> 200
    TEST_VIRTUAL_HOST -> 100
    else -> 0
}
