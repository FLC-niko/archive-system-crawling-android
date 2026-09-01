package com.topviewclub.common.storage.official

import android.os.Build
import androidx.annotation.RequiresApi
import com.topviewclub.common.base.appContext
import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.GzhAutoToBigData
import com.topviewclub.common.bean.OfficialArticle
import com.topviewclub.common.bean.ServerData
import com.topviewclub.common.bean.ServerStatusType
import com.topviewclub.common.log.logI
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.mq.AtdError
import com.topviewclub.common.mq.RabbitMQClient
import com.topviewclub.common.mq.SeedArticle
import com.topviewclub.common.mq.room.addGzhCorrelationData
import com.topviewclub.common.mq.room.addVideoCorrelationData
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.defaultOutputDirectory
import com.topviewclub.common.util.getCurrentTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object OfficialArticleWriter {

    private val rabbitScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun writeOfficialArticleSet(articles: Set<OfficialArticle>, tag: String) {
        val file = File(appContext.defaultOutputDirectory(), "official_${tag}.txt")
        if (!file.exists()) file.createNewFile()
        val out = OutputStreamWriter(FileOutputStream(file))
        val sb = StringBuilder()
        articles.map {
            sb.append(it).append("\n")
        }
        out.write(sb.toString())
        out.flush()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendOfficialArticleSetToBigData(articles: Set<OfficialArticle>, aaosTask: AAOSTask) {
        if (aaosTask.rabbitTaskContext != null) {
            sendRabbitArticles(articles, aaosTask)
            return
        }

        val articleToBigData : MutableList<GzhAutoToBigData> = mutableListOf()

        articles.forEach {
            val tempTimeStamp :Array<String> = arrayOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(aaosTask.startDate)),
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(aaosTask.endDate))
            )
            val gzhData = GzhAutoToBigData(
                RabbitMQClient.gzhQueueName!!,
                aaosTask.target!!,
                tempTimeStamp,
                it.url,
                aaosTask.tag!!.toLong(),
                correlationId = UUID.randomUUID().toString()
            )
            articleToBigData.add(gzhData)
        }
        if(articleToBigData.size<=20){
            val articleJson = AnalysisJson.generateGzhAutoToBigData(articleToBigData)
            CoroutineScope(Dispatchers.IO).launch{
                kotlin.runCatching {
                    RabbitMQClient.producerGzhToBigData!!.send(articleJson)
                    addGzhCorrelationData(RabbitMQClient.gzhCorrelationId!!)
                    RabbitMQClient.consumerGzhFromBackend!!.ask()
                }.onFailure {
                    logRabbit("RabbitMQClient.producerVideoToBackend OR gzhCorreletionId is null ,fail to send")
                }
            }
        }else{
            val index = articleToBigData.size/2
            val listOne = articleToBigData.slice(0 until index)
            val listTwo = articleToBigData.slice(index until  articleToBigData.size)
            val jsonOne = AnalysisJson.generateGzhAutoToBigData(listOne)
            val jsonTwo = AnalysisJson.generateGzhAutoToBigData(listTwo)
            CoroutineScope(Dispatchers.IO).launch{
                kotlin.runCatching {
                    // 发送数据
                    RabbitMQClient.producerGzhToBigData!!.send(jsonOne)
                    RabbitMQClient.producerGzhToBigData!!.send(jsonTwo)

                    // 发送状态
                    if (ServerStatusType.record != ServerStatusType.IDLE ){
                        ServerStatusType.record = ServerStatusType.IDLE
                        val serverData = ServerData(
                            status = ServerStatusType.IDLE, description = null, insertTime = getCurrentTime()
                        )
                        val json = AnalysisJson.generateStatusToServer(serverData)
                        RabbitMQClient.producerStatusToServer!!.send(json)
                        logI("Server","End task : send status to Server Success")
                    }else{
                        logI("Server","End task : status had been IDLE")
                    }

                    addVideoCorrelationData(RabbitMQClient.gzhCorrelationId!!)
                    RabbitMQClient.consumerGzhFromBackend!!.ask()
                }.onFailure {
                    logRabbit("RabbitMQClient.producerVideoToBackend OR gzhCorreletionId is null ,fail to send")
                }
            }
        }



    }

    /**
     * RabbitMQ V2 任务只发布一条聚合 ATD；无效/空 URL 不会污染结果。
     * 发布协程失败时会让 RabbitTaskContext.completion 失败，消费者因此不会 ACK 原 BTA。
     */
    private fun sendRabbitArticles(articles: Set<OfficialArticle>, aaosTask: AAOSTask) {
        val context = aaosTask.rabbitTaskContext ?: return
        val seedArticles = articles.asSequence()
            .map { it.url.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { SeedArticle(url = it) }
            .toList()
        rabbitScope.launch {
            runCatching {
                context.publishTerminal(
                    status = if (seedArticles.isEmpty()) "EMPTY" else "SUCCEEDED",
                    seedArticles = seedArticles,
                )
            }.onFailure {
                logRabbit("V2 ATD 发布失败: ${it.message}")
            }
        }
    }

    /** RabbitMQ V2 任务的服务级失败终态。 */
    fun sendRabbitFailure(aaosTask: AAOSTask, code: String, message: String = code) {
        val context = aaosTask.rabbitTaskContext ?: return
        rabbitScope.launch {
            runCatching {
                context.publishTerminal(
                    status = "FAILED",
                    error = AtdError(
                        code = code,
                        message = message,
                        retryable = true,
                    ),
                )
            }.onFailure {
                logRabbit("V2 FAILED ATD 发布失败: ${it.message}")
            }
        }
    }

}
