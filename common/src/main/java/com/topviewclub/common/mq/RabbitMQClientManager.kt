package com.topviewclub.common.mq

import android.os.Build
import androidx.annotation.RequiresApi
import com.rabbitmq.client.*
import com.topviewclub.common.bean.ServerData
import com.topviewclub.common.bean.ServerStatusType
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.getCurrentTime
import kotlinx.coroutines.*

class RabbitMQClientManager {

    /**
     * 以上参数可以在常数列表中更改
     */
    // 主机ip（必需）
    private var mHostName: String = HOST_NAME

    // 端口号（必需）
    private var mPortName: Int = PORT_NAME

    // 用户名（必需）
    private var mUserName: String = USER_NAME

    // 用户密码（必需）
    private var mPassword: String = PASSWORD

    // 虚拟端口名（必需）
    private var mVirtualHost: String = VIRTUAL_HOST

    private var connection: Connection? = null
    private var channel: Channel? = null

    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    // 生产者列表
    private val producers = mutableListOf<Producer>()

    // 消费者列表
    private val consumers = mutableListOf<Consumer?>()


    /**
     * 定义生产者
     *
     * 参数：
     * queueName:队列名称
     * exchangeName：交换机名称
     * exchangeType：交换机类型
     * routingKey：路由密钥
     */
    @RequiresApi(Build.VERSION_CODES.O)
    inner class Producer(
        private val queueName: String,
        private val exchangeName: String,
        private val exchangeType: String,
        private val routingKey: String,
    ) {
        private var producerChannel: Channel? = null
        private var mMessage: String? = null


        init {
            coroutineScope.launch {
                try {
                    // 创建连接和信道
                    val conn = getConnection()
                    val ch = conn.createChannel()


                    //开启消息确认机制
                    ch.confirmSelect()
                    ch.addConfirmListener(object : ConfirmListener {
                        override fun handleAck(deliveryTag: Long, multiple: Boolean) {
                            logRabbit("Producer Send Success")
                        }

                        override fun handleNack(deliveryTag: Long, multiple: Boolean) {
                            logRabbit("Message Send Again")
                            CoroutineScope(Dispatchers.IO).launch {
                                send(mMessage!!)
                            }
                        }
                    })
                    // 声明交换器和队列
                    // 不声明队列，声明了反而发不上去
//                    ch.exchangeDeclare(exchangeName, exchangeType, true)
                    ch.queueDeclare(queueName, true, false, false, null)
//                    ch.queueBind(queueName, exchangeName, queueName)
                    // 将信道保存到生产者对象中
                    producerChannel = ch

                    // 通知注册中心服务启动(之所以放这里是因为这个需要在一开始就发送一次，但是其需要与生产者注册在同一线程
                    kotlin.runCatching {
                        if (ServerStatusType.record != ServerStatusType.IDLE) {
                            ServerStatusType.record = ServerStatusType.IDLE
                            val serverData = ServerData(
                                status = ServerStatusType.IDLE,
                                description = "Sever Start",
                                insertTime = getCurrentTime()
                            )
                            val json = AnalysisJson.generateStatusToServer(serverData)
                            RabbitMQClient.producerStatusToServer!!.send(json)
                            logI("Server", "Start time : send status to Server Success")
                        } else {
                            logI("Server", "Start time : status had been IDLE")
                        }

                    }


                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        suspend fun send(message: String) {
            // 使用保存的信道发送消息
            withContext(coroutineScope.coroutineContext) {
                kotlin.runCatching {
                    mMessage = message
                    producerChannel!!.basicPublish(
                        "",
                        queueName,
                        null,
                        message.toByteArray()
                    )

                    logRabbit("QueueName : $queueName ,Send Success $mMessage")
                }.onFailure {
                    logRabbit("QueueName : ${queueName}, Send UnSuccess $mMessage")
                    it.printStackTrace()
                }
            }

        }
    }

    // 定义消费者
    inner class Consumer(
        private val queueName: String,
        private val exchangeName: String,
        private val exchangeType: String,
        private val routingKey: String,
        onMessageReceived: (String) -> Unit
    ) {
        private var consumerChannel: Channel? = null
        private var onlyIdSet = mutableSetOf<Int>()
        private var deliveryTag: Long? = 0

        init {
            coroutineScope.launch {
                try {
                    // 创建连接和信道
                    val conn = getConnection()
                    val ch = conn.createChannel()

                    // 限流,每次只取1条
                    ch.basicQos(1)
                    // 声明交换器和队列
                    ch.exchangeDeclare(exchangeName, exchangeType, true)
                    ch.queueDeclare(queueName, true, false, false, null)
                    ch.queueBind(queueName, exchangeName, routingKey)
                    // 定义消费者并将信道保存到消费者对象中
                    val consumer = object : DefaultConsumer(ch) {
                        override fun handleDelivery(
                            consumerTag: String?,
                            envelope: Envelope?,
                            properties: AMQP.BasicProperties?,
                            body: ByteArray?
                        ) {
                            kotlin.runCatching {
                                val message = body!!.toString(Charsets.UTF_8)
//                                ch.basicAck(envelope!!.deliveryTag,false)
                                deliveryTag = envelope?.deliveryTag
                                onMessageReceived(message)
                            }.onFailure {
                                logRabbit("GET BODY IS NULL FROM MQ")
                            }

                        }
                    }

                    // 关于参数 autoAck：这个参数用于确认是否进行消息确认，ture代表无需确认，队列发完即可删除，false代表需要确认
                    ch.basicConsume(queueName, false, consumer)

                    consumerChannel = ch
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun add(id: Int) {
            onlyIdSet.add(id)
        }

        fun ask() {
            consumerChannel?.basicAck(deliveryTag!!, false)
        }

        /**
         * 拒绝当前接受到的消息，表示无法处理并返回给队列交由下一个人处理
         */
        fun reject() {
            consumerChannel?.basicReject(deliveryTag!!, true)
        }

        fun close() {
            consumerChannel?.close()
        }
    }

    // 连接RabbitMQ服务器
    private suspend fun getConnection(): Connection {
        return connection ?: withContext(coroutineScope.coroutineContext) {
            // 在此完成对工厂的初始化
            val factory = ConnectionFactory()
            factory.host = mHostName
            factory.port = mPortName
            factory.username = mUserName
            factory.password = mPassword
            factory.virtualHost = mVirtualHost
            val conn = factory.newConnection()
            connection = conn
            conn
        }
    }

    // 注册生产者
    @RequiresApi(Build.VERSION_CODES.O)
    fun registerProducer(
        exchangeName: String,
        exchangeType: String,
        routingKey: String,
        queueName: String
    ): Producer {
        val producer = Producer(queueName, exchangeName, exchangeType, routingKey)
        producers.add(producer)
        return producer
    }

    // 注册消费者
    fun registerConsumer(
        exchangeName: String,
        exchangeType: String,
        routingKey: String,
        queueName: String,
        onMessageReceived: (String) -> Unit
    ): Consumer {
        val consumer =
            Consumer(queueName, exchangeName, exchangeType, routingKey, onMessageReceived)
        consumers.add(consumer)
        return consumer
    }

    // 关闭连接和信道
    fun close() {
        coroutineScope.launch {
            producers.clear()
            consumers.forEach { it?.close() }
            consumers.clear()
            channel?.close()
            connection?.close()
        }
        job.cancel()
    }
}