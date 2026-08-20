package com.topviewclub.crawling.core.control

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64.encodeToString
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.topviewclub.common.base.appContext
import com.topviewclub.common.bean.*
import com.topviewclub.common.bean.TaskCrawlingType.TYPE_OFFICIAL
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.mq.RabbitMQClient
import com.topviewclub.common.mq.room.*
import com.topviewclub.common.storage.deleteAllPhotos
import com.topviewclub.common.storage.updateQR
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.className
import com.topviewclub.common.util.getCurrentTime
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


object TaskDispatcher {

    private val taskList = mutableListOf<AAOSTask>()


    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    fun init() {

        // 注册公众号，视频号，单个视频的生产者
        RabbitMQClient.prepareRabbitProducer()
        // 注册公众号消费者，具体参数请看对接文档
        RabbitMQClient.prepareGzhAutoConsumer {
            val gzhDate = AnalysisJson.analysisGzhAutoFromBackend(it)
            kotlin.runCatching {
                val correlationId = gzhDao.selectCorrelationId(gzhDate!!.correlationId)
                if (correlationId == null) {
                    RabbitMQClient.gzhCorrelationId = gzhDate.correlationId
                    RabbitMQClient.gzhQueueName = gzhDate.queueName
                    generateAndEnqueueTask(
                        TYPE_OFFICIAL,
                        gzhDate.jobId.toString(),
                        gzhDate.gzhName,
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .parse(gzhDate.tempTimeStamp[0])!!.time,
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .parse(gzhDate.tempTimeStamp[1])!!.time,
                        gzhDate.image
                    )
                } else {
                    logRabbit(
                         "Gzh Repeat"
                    )
                    RabbitMQClient.consumerGzhFromBackend!!.ask()
                }



            }.onFailure {
                logRabbit(
                     "Gzh Exception" +
                            "Cause = ${it.cause} , Message = ${it.message}"
                )
            }
        }





//
//        // 注册视频号的消费者
//        RabbitMQClient.prepareVideoAutoConsumer {
//            logRabbit("huidiao Test")
//            val videoData = AnalysisJson.analysisVideoAutoFromBackend(it)
//            kotlin.runCatching {
//                val correlationId = videoDao.selectCorrelationId(videoData!!.correlationId)
//                if (correlationId == null) {
//
//                    RabbitMQClient.videoCorrelationId = videoData.correlationId
//                    val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//                        .parse(videoData.tempTimeStamp[0])!!.time
//                    val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
//                        .parse(videoData.tempTimeStamp[1])!!.time
//                    logRabbit( "startTime : ${videoData.tempTimeStamp[0]} , endData : ${videoData.tempTimeStamp[1]}")
//
//                    generateAndEnqueueTask(
//                        TYPE_VIDEO,
//                        videoData.jobId.toString(),
//                        videoData.videoName,
//                        startDate,
//                        endDate,
//                        videoData.image
//                    )
//                } else {
//                    logRabbit(
//                         "Gzh Repeat"
//                    )
//                    RabbitMQClient.consumerVideoFromBackend!!.ask()
//                }
//
//            }.onFailure {
//                logRabbit(
//                     "Video Exception" +
//                            "Cause = ${it.cause} , Message = ${it.message}"
//                )
//            }
//
//        }

//        // 注册单个视频的消费者
//        @OptIn(DelicateCoroutinesApi::class)
//        RabbitMQClient.prepareVideoSingleConsumer {
//            GlobalScope.launch {
//                val tc = AnalysisJson.analysisVideoSingleFromBackend(it)
//                kotlin.runCatching {
//                    val correlationId = singleVideoDao.selectCorrelationId(tc!!.correlationId)
//                    if (correlationId == null) {
//                        addSingleVideoCorrelationData(tc.correlationId)
//                        val ac = requireACVideo(tc.code)
//                        val video = VideoSingleToBackend(
//                            null,
//                            ac.title,
//                            ac.url,
//                            null,
//                            null,
//                            tc.userId,
//                            tc.categoryCodeId,
//                            UUID.randomUUID().toString()
//                        )
//                        val json = AnalysisJson.generateVideoSingleToBackend(video)
//                        logI("[ACVideo]", "Code = $tc , Url = ${ac.url}")
//                        RabbitMQClient.producerSingleVideoToBackend?.send(json)
//                    }
//
//                }.onFailure {
//                    if (tc == null) {
//                        logE(
//                            "ACVideo", "[SingleVideoCode is NULL]" +
//                                    "Cause = ${it.cause} , Message = ${it.message}"
//                        )
//                    } else {
//                        logE(
//                            "ACVideo", "[${tc.code.toStringOrEmpty()}]" +
//                                    "Cause = ${it.cause} , Message = ${it.message}"
//                        )
//                    }
//                }
//
//            }
//        }


        TaskStat.startDate = Date()
        TaskStat.addProcessingTaskListener {
            TaskStat.enqueuingTaskList.postValue(taskList.toMutableList())
            if (it.type == TaskCrawlingType.TYPE_NOTHING) {
                if (taskList.isNotEmpty()) {
                    taskList.removeFirst().dispatch()
                } else {
                    AutoChatTask().dispatch()
                }
            }
        }
    }

    /**
     * 抓取的起点
     *
     * @param type 抓取类型，详见 [TaskCrawlingType]
     * @param target 抓取的目标账号名
     * @param startDate 抓取的起始日期
     * @param endDate 抓取的结束日期
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    fun generateAndEnqueueTask(
        type: String,
        tag: String?,
        target: String?,
        startDate: Long,
        endDate: Long,
        QRBody: String?
    ) {


        when(type){
            TYPE_OFFICIAL->{
                val result = isExceededTimes(RabbitMQClient.gzhCorrelationId!!, target!!)
                if (result != null && result) {
                    logRabbit("Task IS ExceededTimes,GzhTask[$target is Error ,correlationId: ${RabbitMQClient.gzhCorrelationId} ")
                    RabbitMQClient.consumerGzhFromBackend?.ask()
                } else if (result == null) {
                    logRabbit("")
                }
            }
        }

        AAOSTask(type, tag, target, startDate, endDate, QRBody).enqueue()





    }

    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun AAOSTask.enqueue() {

        // 向注册中心回报状态
        CoroutineScope(Dispatchers.IO).launch {
            kotlin.runCatching {
                if (ServerStatusType.record != ServerStatusType.BUSY ){
                    ServerStatusType.record = ServerStatusType.BUSY
                    val serverData = ServerData(
                        status = ServerStatusType.BUSY, description = null, insertTime = getCurrentTime()
                    )
                    val json = AnalysisJson.generateStatusToServer(serverData)
                    RabbitMQClient.producerStatusToServer!!.send(json)
                    logI("Server","Accept task : send status to Server Success")
                }else{
                    logI("Server","Accept task : status had been BUSY")
                }
            }
        }
        taskList.add(this)
        //此步骤是用于监听正在执行的任务数的
        TaskStat.enqueuingTaskList.postValue(taskList.toMutableList())
        if (TaskStat.processingTask == AutoChatTask()) {
            // 正在执行 AC 服务，告知准备停止
            AutoChatOperationService.shutdownACService()
        }
    }

    @MainThread
    private fun AAOSTask.dispatch() {
        TaskStat.processingTask = this
        if (type != TaskCrawlingType.TYPE_AUTO_CHAT) {
            logI(this@TaskDispatcher.className, this@dispatch.toString())
//            sendMessageToHostErrorOnce(
//                this@TaskDispatcher.className,
//                TaskResultType.PLEASE_PUSH_PICTURE,
//                tag.toStringOrEmpty()
//            )
        }
        Crawler(this)?.let {
            Handler(Looper.getMainLooper()).postDelayed({

                // 由于现在暂无公众号服务，所以这个清楚缓存的操作先删除
//                wechatVideoCacheCaptor.removeAllVideosFromWechat()
//                appContext.updateQRCode(tag)
                appContext.deleteAllPhotos("aaos")
                appContext.updateQR(tag, QR)
                it.startCrawling(target, tag, startDate, endDate)
            }, 10000L)
        }
    }

    @Suppress("FunctionName")
    private fun Crawler(task: AAOSTask): Crawler? {
        return when (task.type) {
            TaskCrawlingType.TYPE_AUTO_CHAT -> WechatAutoChatCrawler
            TaskCrawlingType.TYPE_VIDEO -> WechatVideoCrawler
            TaskCrawlingType.TYPE_OFFICIAL -> WechatOfficialCrawler
            TaskCrawlingType.TYPE_CHECK_WECHAT_QRCODE -> WechatQRCodeCheckCrawler
            TaskCrawlingType.TYPE_XUE_XI -> XueXiCrawler
            else -> {
//                sendMessageToHostError(
//                    className,
//                    TaskResultType.UNEXPECTED_CRAWLING_TYPE,
//                    task.tag.toStringOrEmpty()
//                )
                logE(className, "AAOS:${TaskResultType.UNEXPECTED_CRAWLING_TYPE}")
                TaskStat.processingTask = NullTask()
                null
            }
        }
    }

}
