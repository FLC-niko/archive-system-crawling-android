package com.topviewclub.common.bean

import kotlinx.serialization.Serializable

data class VideoSingleFromBackend(
    val code: String,
    val userId: Long,
    val categoryCodeId: Long,
    val correlationId:String
)

data class VideoAutoFromBackend(
    val image: String,
    val videoName: String,
    val tempTimeStamp: Array<String>,
    val url: String,
    val jobId: Long,
    val correlationId:String
)

@Serializable
data class VideoAutoToBackend(
    val releaseTime :String,
    val title :String,
    val videoUrl : String,
    val jobId: Int,
    val tempTimeStamp: Array<String>,
    val userId: Int? = null,
    val categoryCodeId: Int? = null,
    val correlationId:String
)
@Serializable
data class VideoSingleToBackend(
    val releaseTime :String?,
    val title :String,
    val videoUrl : String,
    val jobId: Int?,
    val tempTimeStamp: Array<String?>?,
    val userId: Long,
    val categoryCodeId: Long,
    val correlationId:String
)

@Serializable
data class GzhAutoToBigData(
    val queueName: String,
    val gzhName: String,
    val tempTimeStamp: Array<String>,
    val url: String,
    val jobId: Long,
    val correlationId:String
)

data class GzhAutoFromBackend(
    val queueName :String,
    val image: String,
    val gzhName: String,
    val tempTimeStamp: Array<String>,
    val url: String,
    val jobId: Long,
    val correlationId:String
)

