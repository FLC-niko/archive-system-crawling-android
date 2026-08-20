package com.topviewclub.crawling.wechat.auto.room.acv

import androidx.annotation.StringDef
import androidx.room.Entity
import androidx.room.Index
import kotlin.random.Random
import kotlin.random.nextInt

@Entity(
    indices = [Index(value = ["time"])],
    primaryKeys = ["requestType", "requestCode"]
)
data class ACVideo(
    val requestCode: Long,
    @RequestWechatType
    val requestType: String,
    val numberOfWechat: String,
    val nameOfWechat: String,
    val url: String,
    val time: Long,
    val title: String
) : java.io.Serializable

const val REQUEST_TYPE_UNDEFINED = "99"

const val REQUEST_TYPE_WECHAT_VIDEO_W = "23"

const val REQUEST_TYPE_WECHAT_VIDEO_E = "05"

const val REQUEST_TYPE_WECHAT_VIDEO_C = "03"

const val REQUEST_TYPE_WECHAT_VIDEO_H = "08"

const val REQUEST_TYPE_WECHAT_VIDEO_A = "01"

const val REQUEST_TYPE_WECHAT_VIDEO_T = "20"

@RequestWechatType
fun randomWechatVideoType(): String {
    return when (Random.nextInt(0..5)) {
        0 -> REQUEST_TYPE_WECHAT_VIDEO_W
        1 -> REQUEST_TYPE_WECHAT_VIDEO_E
        2 -> REQUEST_TYPE_WECHAT_VIDEO_C
        3 -> REQUEST_TYPE_WECHAT_VIDEO_H
        4 -> REQUEST_TYPE_WECHAT_VIDEO_A
        5 -> REQUEST_TYPE_WECHAT_VIDEO_T
        else -> REQUEST_TYPE_UNDEFINED
    }
}

fun isWechatVideoType(@RequestWechatType type: String): Boolean {
    return type == REQUEST_TYPE_WECHAT_VIDEO_W ||
            type == REQUEST_TYPE_WECHAT_VIDEO_E ||
            type == REQUEST_TYPE_WECHAT_VIDEO_C ||
            type == REQUEST_TYPE_WECHAT_VIDEO_H ||
            type == REQUEST_TYPE_WECHAT_VIDEO_A ||
            type == REQUEST_TYPE_WECHAT_VIDEO_T
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@StringDef(
    value = [
        REQUEST_TYPE_UNDEFINED,
        REQUEST_TYPE_WECHAT_VIDEO_W,
        REQUEST_TYPE_WECHAT_VIDEO_E,
        REQUEST_TYPE_WECHAT_VIDEO_C,
        REQUEST_TYPE_WECHAT_VIDEO_H,
        REQUEST_TYPE_WECHAT_VIDEO_A,
        REQUEST_TYPE_WECHAT_VIDEO_T
    ]
)
annotation class RequestWechatType