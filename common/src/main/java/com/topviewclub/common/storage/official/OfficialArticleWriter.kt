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
import com.topviewclub.common.mq.RabbitMQClient
import com.topviewclub.common.mq.room.addGzhCorrelationData
import com.topviewclub.common.mq.room.addVideoCorrelationData
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.defaultOutputDirectory
import com.topviewclub.common.util.getCurrentTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object OfficialArticleWriter {

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

}