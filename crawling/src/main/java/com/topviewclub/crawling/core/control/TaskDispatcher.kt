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
import com.topviewclub.common.mq.RabbitMQClientManager
import com.topviewclub.common.mq.RabbitTaskContext
import com.topviewclub.common.mq.RabbitTaskDecoder
import com.topviewclub.common.mq.RabbitTaskMessage
import com.topviewclub.common.mq.AtdError
import com.topviewclub.common.mq.RabbitQrResolutionException
import com.topviewclub.common.mq.buildLegacyGzhResults
import com.topviewclub.common.mq.rabbitTaskPriorityFor
import com.topviewclub.common.mq.resolveRabbitQrImage
import com.topviewclub.common.mq.room.*
import com.topviewclub.common.mq.room.rabbit.RabbitInboxStore
import com.topviewclub.common.storage.deleteAllPhotos
import com.topviewclub.common.storage.updateQR
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.className
import com.topviewclub.common.util.getCurrentTime
import com.topviewclub.crawling.wechat.auto.AutoChatOperationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.*
import java.util.concurrent.ConcurrentHashMap


object TaskDispatcher {

    private val taskList = mutableListOf<AAOSTask>()
    private val rabbitClaimMutex = Mutex()
    private val activeRabbitTasks = ConcurrentHashMap<String, RabbitTaskContext>()

    private val processingTaskListener: (AAOSTask) -> Unit = {
        TaskStat.enqueuingTaskList.postValue(taskList.toMutableList())
        if (it.type == TaskCrawlingType.TYPE_NOTHING) {
            if (taskList.isNotEmpty()) {
                taskList.removeAt(nextTaskIndex()).dispatch()
            } else {
                AutoChatTask().dispatch()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    fun init() {

        // 注册公众号，视频号，单个视频的生产者
        RabbitMQClient.prepareRabbitProducer()
        // V2/legacy 公众号消费者：回调挂起直到 Android 抓取完成且 ATD 已 confirm。
        RabbitMQClient.prepareGzhAutoConsumer(::handleRabbitGzhMessage)





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
        TaskStat.addProcessingTaskListener(processingTaskListener)
    }

    /**
     * 处理一条 RabbitMQ BTA。Room inbox 负责进程重启/重复投递幂等，completion 负责把
     * broker ACK 推迟到 UI 抓取和 V2 ATD publisher confirm 之后。
     */
    private suspend fun handleRabbitGzhMessage(
        body: String,
        delivery: RabbitMQClientManager.DeliveryContext,
    ) {
        val message = RabbitTaskDecoder.decode(body.toByteArray(Charsets.UTF_8))
        val input = message.asV2()
        var ownsTask = false
        val rabbitTaskContext = rabbitClaimMutex.withLock {
            activeRabbitTasks[message.idempotencyKey]?.let { active ->
                logRabbit("Rabbit redelivery waits for active task: ${message.idempotencyKey}")
                return@withLock active
            }

            val claim = RabbitInboxStore.claim(
                RabbitInboxStore.InboxMessage(
                    idempotencyKey = message.idempotencyKey,
                    eventId = message.eventId,
                    workflowId = message.workflowId,
                    allowProcessingTakeover = delivery.isRedeliver,
                ),
            )
            if (!claim.claimed) {
                logRabbit("Rabbit duplicate ignored: ${message.idempotencyKey}")
                return@withLock null
            }

            val resultPublisher = RabbitMQClient.resultPublisher(
                virtualHost = delivery.sourceVirtualHost,
                resultEventId = claim.resultEventId,
            )
            val legacyTask = (message as? RabbitTaskMessage.Legacy)?.task
            RabbitTaskContext(
                input = input,
                sourceVirtualHost = delivery.sourceVirtualHost,
            ) { status, seedArticles, error ->
                if (legacyTask != null) {
                    check(status != "FAILED") {
                        "Legacy 公众号任务失败，保留 BTA 重试: ${error?.code ?: "UNKNOWN"}"
                    }
                    val legacyArticles = buildLegacyGzhResults(legacyTask, seedArticles)
                    RabbitMQClient.publishLegacyGzhResult(
                        virtualHost = delivery.sourceVirtualHost,
                        articles = legacyArticles,
                    )
                } else {
                    resultPublisher.publish(
                        input = input,
                        status = status,
                        seedArticles = seedArticles,
                        error = error,
                        eventId = claim.resultEventId,
                    )
                }
            }.also { context ->
                activeRabbitTasks[message.idempotencyKey] = context
                ownsTask = true
            }
        }
        if (rabbitTaskContext == null) return
        if (!ownsTask) {
            rabbitTaskContext.completion.await()
            return
        }

        try {
            coroutineScope {
                val heartbeat: Job = launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(30_000L)
                        runCatching { RabbitInboxStore.touch(message.idempotencyKey) }
                            .onFailure { logRabbit("Inbox lease touch failed: ${it.message}") }
                    }
                }
                try {
                    val startDate = parseCaptureDate(input.payload.captureWindow.startsAt, endOfDay = false)
                    val endDate = parseCaptureDate(input.payload.captureWindow.endsAt, endOfDay = true)
                    if (startDate == null || endDate == null || startDate > endDate) {
                        rabbitTaskContext.publishTerminal(
                            status = "FAILED",
                            error = AtdError(
                                code = "INVALID_CAPTURE_WINDOW",
                                message = "采集时间窗口格式非法或起止时间反向",
                                retryable = false,
                            ),
                        )
                    } else {
                        val qrBody = try {
                            when (message) {
                                is RabbitTaskMessage.Legacy -> message.task.image
                                is RabbitTaskMessage.V2 -> resolveRabbitQrImage(input.payload.account.qrImage)
                            }
                        } catch (e: RabbitQrResolutionException) {
                            rabbitTaskContext.publishTerminal(
                                status = "FAILED",
                                error = AtdError(
                                    code = e.code,
                                    message = e.message ?: e.code,
                                    retryable = e.retryable,
                                ),
                            )
                            null
                        }
                        if (qrBody != null) {
                            withContext(Dispatchers.Main) {
                                generateAndEnqueueTask(
                                    type = TYPE_OFFICIAL,
                                    tag = input.business.jobId.toString(),
                                    target = input.payload.account.name,
                                    startDate = startDate,
                                    endDate = endDate,
                                    QRBody = qrBody,
                                    rabbitTaskContext = rabbitTaskContext,
                                )
                            }
                            rabbitTaskContext.completion.await()
                        }
                    }
                } finally {
                    heartbeat.cancelAndJoin()
                }
            }
            RabbitInboxStore.markCompleted(message.idempotencyKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RabbitInboxStore.markFailed(message.idempotencyKey, e)
            throw e
        } finally {
            activeRabbitTasks.remove(message.idempotencyKey, rabbitTaskContext)
        }
    }

    /** 手动任务最高；RabbitMQ 任务按 pro > xdag > thdag > test，同级保持 FIFO。 */
    private fun nextTaskIndex(): Int = taskList.indices.maxByOrNull { index ->
        taskList[index].rabbitTaskContext?.sourceVirtualHost
            ?.let(::rabbitTaskPriorityFor)
            ?: Int.MAX_VALUE
    } ?: 0

    private fun parseCaptureDate(value: String, endOfDay: Boolean): Long? {
        val text = value.trim()
        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
        isoFormats.forEach { pattern ->
            val parsed = parseFully(
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false },
                text,
            )
            if (parsed != null) return parsed
        }

        val date = parseFully(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false },
            text,
        ) ?: return null
        if (!endOfDay) return date

        return Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun parseFully(format: SimpleDateFormat, value: String): Long? {
        val position = ParsePosition(0)
        val parsed = format.parse(value, position)
        return if (parsed != null && position.index == value.length) parsed.time else null
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
        QRBody: String?,
        rabbitTaskContext: RabbitTaskContext? = null,
    ) {


        when(type){
            TYPE_OFFICIAL -> if (rabbitTaskContext == null) {
                val correlationId = RabbitMQClient.gzhCorrelationId
                val result = if (correlationId != null && target != null) {
                    isExceededTimes(correlationId, target)
                } else {
                    null
                }
                if (result != null && result) {
                    logRabbit("Task IS ExceededTimes,GzhTask[$target is Error ,correlationId: ${RabbitMQClient.gzhCorrelationId} ")
                    RabbitMQClient.consumerGzhFromBackend?.ask()
                } else if (result == null) {
                    logRabbit("")
                }
            }
        }

        AAOSTask(
            type = type,
            tag = tag,
            target = target,
            startDate = startDate,
            endDate = endDate,
            QR = QRBody,
            rabbitTaskContext = rabbitTaskContext,
        ).enqueue()





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
                // 任务进入队列前已完成二维码解析与校验，此处始终替换为本任务二维码。
                appContext.deleteAllPhotos("aaos")
                appContext.updateQR(tag, QR)
                it.startCrawling(target, tag, startDate, endDate, rabbitTaskContext)
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
