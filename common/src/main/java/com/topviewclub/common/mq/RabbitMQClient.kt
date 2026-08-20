package com.topviewclub.common.mq

import android.os.Build
import androidx.annotation.RequiresApi

object RabbitMQClient {
    // Rabbit客户端管理器，便于注册和管理消费者，生产者
    private var rabbitMQClientManager = RabbitMQClientManager()


    // 视频号的消费者
    var consumerVideoFromBackend: RabbitMQClientManager.Consumer? = null

    // 单个视频消费者
    var consumerSingleVideoFromBackend: RabbitMQClientManager.Consumer? = null

    // 公众号消费者
    var consumerGzhFromBackend: RabbitMQClientManager.Consumer? = null

    // 公众号生产者
    var producerGzhToBigData: RabbitMQClientManager.Producer? = null

    // 视频号生产者
    var producerVideoToBackend: RabbitMQClientManager.Producer? = null
    //单个视频生产者
    var producerSingleVideoToBackend: RabbitMQClientManager.Producer? = null


    // 注册中心状态回报生产者
    var producerStatusToServer :RabbitMQClientManager.Producer? = null

    // 暴露给外部的全局变量，用于监听每条信息的唯一表示，便于去重，但是实现的很丑陋
    var videoCorrelationId:String? = " "
    var gzhCorrelationId:String? = " "
    var gzhQueueName:String? = " "


    @RequiresApi(Build.VERSION_CODES.O)
    fun prepareRabbitProducer( ) {
        // 完成对公众号的初始化
        producerGzhToBigData = rabbitMQClientManager.registerProducer(
            "new-media-backend",
            "direct",
            "gzh-auto-ATD-routing",
            "gzh-auto-ATD-queue"
        )
//        producerVideoToBackend = rabbitMQClientManager.registerProducer(
//            "new-media-backend",
//            "direct",
//            "video-auto-ATB-routing",
//            "video-auto-ATB-queue"
//        )
//        producerSingleVideoToBackend = rabbitMQClientManager.registerProducer(
//            "new-media-backend",
//            "direct",
//            "video-single-ATB-routing",
//            "video-single-ATB-queue"
//        )

        // 完成对注册中心状态回报的生产者初始化
        producerStatusToServer = rabbitMQClientManager.registerProducer(
            "server-exchange",
            "direct",
            "server-routing",
            "server.queue.provideLog",
        )


    }

    fun prepareVideoAutoConsumer(onMessageReceive: (String) -> Unit) {
        consumerVideoFromBackend = rabbitMQClientManager.registerConsumer(
            "new-media-backend",
            "direct",
            "video-auto-BTA-routing",
            "video-auto-BTA-queue",
            onMessageReceive
        )
    }

    fun prepareVideoSingleConsumer(onMessageReceive: (String) -> Unit) {
        consumerSingleVideoFromBackend = rabbitMQClientManager.registerConsumer(
            "new-media-backend",
            "direct",
            "video-single-BTA-routing",
            "video-single-BTA-queue",
            onMessageReceive
        )
    }

    fun prepareGzhAutoConsumer(onMessageReceive: (String) -> Unit) {
        consumerGzhFromBackend = rabbitMQClientManager.registerConsumer(
            "new-media-backend",
            "direct",
            "gzh-auto-BTA-routing",
            "gzh-auto-BTA-queue",
            onMessageReceive
        )
    }
    fun closeRabbitConfiguration(){
        rabbitMQClientManager.close()
    }
}