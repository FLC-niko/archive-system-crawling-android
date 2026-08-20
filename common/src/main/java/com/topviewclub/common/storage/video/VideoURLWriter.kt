package com.topviewclub.common.storage.video

import com.topviewclub.common.base.appContext
import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.Video
import com.topviewclub.common.bean.VideoAutoToBackend
import com.topviewclub.common.log.logRabbit
import com.topviewclub.common.mq.RabbitMQClient
import com.topviewclub.common.mq.room.addVideoCorrelationData
import com.topviewclub.common.util.AnalysisJson
import com.topviewclub.common.util.defaultOutputDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object VideoURLWriter {

    fun writeVideoURLList(videos: List<Video>, tag: String) {
        val file = File(appContext.defaultOutputDirectory(), "video_${tag}.txt")
        if (!file.exists()) file.createNewFile()
        val out = OutputStreamWriter(FileOutputStream(file))
        val sb = StringBuilder()
        videos.map {
            sb.append("Video(").append("releaseTime=").append(it.info.releaseTime).append(", ")
                .append("title=").append(it.info.title).append(", ")
                .append("url=").append(it.url).append(")").append("\n")
        }
        out.write(sb.toString())
        out.flush()

    }

    /**
     * 将爬到的url发送给至消息队列
     */
    fun sendVideoAutoToBackend(videos: List<Video>, aaosTask: AAOSTask) {
        //将videos编译打包成用于后台的数据
        if (videos.size <= 20) {
            val list: MutableList<VideoAutoToBackend> = mutableListOf()
            for (video in videos) {
                val tempTimeStamp: Array<String> = arrayOf(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.startDate)),
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.endDate))
                )
                val videoToBackend = VideoAutoToBackend(
                    video.info.releaseTime,
                    video.info.title,
                    video.url,
                    aaosTask.tag!!.toInt(),
                    tempTimeStamp,
                    correlationId = UUID.randomUUID().toString()
                )
                logRabbit("startTime : ${tempTimeStamp[0]} , endData : ${tempTimeStamp[1]}")
                list.add(videoToBackend)
            }

            CoroutineScope(Dispatchers.IO).launch {
                kotlin.runCatching {
                    //转化为json格式
                    val jsonData = AnalysisJson.generateVideoAutoToBackend(list)

                    RabbitMQClient.producerVideoToBackend!!.send(jsonData)
                    addVideoCorrelationData(RabbitMQClient.videoCorrelationId!!)
                    RabbitMQClient.consumerVideoFromBackend!!.ask()
                }.onFailure {
                    logRabbit(
                        "RabbitMQClient.producerVideoToBackend or videoCorrelationId is null ,fail to send"
                    )
                }
            }
        } else {
            val middleIndex = videos.size / 2
            val videosOne = videos.slice(0 until middleIndex)
            val videosTwo = videos.slice(middleIndex until videos.size)
            val listOne: MutableList<VideoAutoToBackend> = mutableListOf()
            val listTwo: MutableList<VideoAutoToBackend> = mutableListOf()
            for (video in videosOne) {
                val tempTimeStamp: Array<String> = arrayOf(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.startDate)),
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.endDate))
                )
                val videoToBackend = VideoAutoToBackend(
                    video.info.releaseTime,
                    video.info.title,
                    video.url,
                    aaosTask.tag!!.toInt(),
                    tempTimeStamp,
                    correlationId = UUID.randomUUID().toString()
                )
                listOne.add(videoToBackend)
            }
            for (video in videosTwo) {
                val tempTimeStamp: Array<String> = arrayOf(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.startDate)),
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(aaosTask.endDate))
                )
                val videoToBackend = VideoAutoToBackend(
                    video.info.releaseTime,
                    video.info.title,
                    video.url,
                    aaosTask.tag!!.toInt(),
                    tempTimeStamp,
                    correlationId = UUID.randomUUID().toString()
                )
                logRabbit("startTime : ${tempTimeStamp[0]} , endData : ${tempTimeStamp[1]}")
                listTwo.add(videoToBackend)
            }

            val jsonOne = AnalysisJson.generateVideoAutoToBackend(listOne)
            val jsonTwo = AnalysisJson.generateVideoAutoToBackend(listTwo)
            CoroutineScope(Dispatchers.IO).launch {
                kotlin.runCatching {
                    RabbitMQClient.producerVideoToBackend!!.send(jsonOne)
                    RabbitMQClient.producerVideoToBackend!!.send(jsonTwo)
                    addVideoCorrelationData(RabbitMQClient.videoCorrelationId!!)
                    RabbitMQClient.consumerVideoFromBackend!!.ask()
                }.onFailure {
                    logRabbit(
                        "RabbitMQClient.producerVideoToBackend or videoCorrelationId is null ,fail to send"
                    )
                }
            }
        }

    }

}