package com.topviewclub.common.util

import com.google.gson.Gson
import com.topviewclub.common.bean.*
import com.topviewclub.common.log.logE
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用于解析和构造json数据
 */
object AnalysisJson {
    fun analysisGzhAutoFromBackend(responseData: String): GzhAutoFromBackend? {
        var gzhData:GzhAutoFromBackend? = null
        runCatching {
            val root = JSONObject(responseData)
            val queueName = root.getString("queueName")
            val image = root.getString("image")
            val gzhName = root.getString("gzhName")
            val tempTimeStamp = root.getJSONArray("tempTimeStamp")
            val url = root.getString("url")
            val jobId = root.getLong("jobId")
            val tempTimeStampArray = arrayOf(
                tempTimeStamp.getString(0),
                tempTimeStamp.getString(1)
            )
            //去重用的id
            val correlationId = root.getString("correlationId")
            gzhData = GzhAutoFromBackend(
                queueName,
                image,
                gzhName,
                tempTimeStampArray,
                url,
                jobId,
                correlationId
            )
        }.onFailure {
            logE(
                "Gzh", "Json Exception from GzhData  " +
                        "Cause = ${it.cause} , Message = ${it.message}"
            )
        }
        return gzhData
    }

    fun generateVideoAutoToBackend(videoList: List<VideoAutoToBackend>): String {

            val jsonArray = JSONArray()
            for (video in videoList) {
                val jsonObject = JSONObject()
                jsonObject.put("releaseTime", video.releaseTime)
                jsonObject.put("title", video.title)
                jsonObject.put("videoUrl", video.videoUrl)
                jsonObject.put("jobId", video.jobId)
                jsonObject.put("tempTimeStamp", JSONArray(video.tempTimeStamp))
                jsonObject.put("userId",video.userId?:"")
                jsonObject.put("categoryCodeId",video.categoryCodeId?:"")
                jsonObject.put("correlationId", video.correlationId)
                jsonArray.put(jsonObject)
            }
            return jsonArray.toString()

    }

    fun generateGzhAutoToBigData(articlesList: List<GzhAutoToBigData>): String {
        val json = Json { encodeDefaults = true }
        return json.encodeToString(ListSerializer(GzhAutoToBigData.serializer()),articlesList)
    }

    fun generateStatusToServer(serverData: ServerData):String{
        val jsonObject = JSONObject()
        jsonObject.put("macAddress",serverData.macAddress)
        jsonObject.put("serviceName",serverData.serviceName)
        jsonObject.put("status",serverData.status)
        jsonObject.put("description",serverData.description)
        jsonObject.put("insertTime",serverData.insertTime)
        return jsonObject.toString()
    }

    fun generateVideoSingleToBackend(video: VideoSingleToBackend): String {
        val gson = Gson()
        return gson.toJson(video)
    }
    
    fun analysisVideoAutoFromBackend(responseData: String):VideoAutoFromBackend?{
        var videoData:VideoAutoFromBackend? = null
        runCatching {
            val root = JSONObject(responseData)
            val image = root.getString("image")
            val gzhName = root.getString("gzhName")
            val tempTimeStamp = root.getJSONArray("tempTimeStamp")
            val url = root.getString("url")
            val jobId = root.getLong("jobId")
            val correlationId = root.getString("correlationId")
            val tempTimeStampArray = arrayOf(
                tempTimeStamp.getString(0),
                tempTimeStamp.getString(1)
            )
            videoData = VideoAutoFromBackend(
                image,
                gzhName,
                tempTimeStampArray,
                url,
                jobId,
                correlationId
            )
        }.onFailure {
            logE(
                "Video", "Json Exception from VideoData  " +
                        "Cause = ${it.cause} , Message = ${it.message}"
            )
        }
        return videoData
    }
    fun analysisVideoSingleFromBackend(responseData: String):VideoSingleFromBackend?{
        var videoSingleFromBackend:VideoSingleFromBackend?=null
        runCatching {
            val root = JSONObject(responseData)
            val code = root.getString("code")
            val userId = root.getLong("userId")
            val categoryCodeId = root.getLong("categoryCodeId")
            val correlationId = root.getString("correlationId")
            videoSingleFromBackend = VideoSingleFromBackend(
                code,
                userId,
                categoryCodeId,
                correlationId
            )
        }.onFailure {
            logE(
                "SingleVideo", "Json Exception from SingleVideo  " +
                        "Cause = ${it.cause} , Message = ${it.message}"
            )
        }
        return videoSingleFromBackend
    }
}