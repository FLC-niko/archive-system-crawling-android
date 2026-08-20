package com.topviewclub.common.bean

import kotlinx.serialization.Serializable

fun emptyVideoInfo() = VideoInfo("", "", true)
@Serializable
data class Video(
    val info: VideoInfo,
    val url: String
)
@Serializable
data class VideoInfo(
    val releaseTime: String,
    val title: String,
    val video: Boolean
) {
    override fun equals(other: Any?): Boolean {
        return other != null
                && other is VideoInfo
                && other.releaseTime == releaseTime
                && other.title == title
    }

    override fun hashCode(): Int {
        var result = releaseTime.hashCode()
        result = 31 * result + title.hashCode()
        return result
    }

}