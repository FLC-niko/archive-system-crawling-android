package com.topviewclub.common.bean

import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/**
 * 返回一个类型为 [TaskCrawlingType.TYPE_AUTO_CHAT] 的 [AAOSTask]
 * */
@Suppress("FunctionName")
fun AutoChatTask() = autoChatTask

/**
 * 返回一个类型为 [TaskCrawlingType.TYPE_NOTHING] 的 [AAOSTask]
 * */
@Suppress("FunctionName")
fun NullTask() = nullTask

private val autoChatTask = AAOSTask(TaskCrawlingType.TYPE_AUTO_CHAT, "AutoChat")

private val nullTask = AAOSTask(TaskCrawlingType.TYPE_NOTHING, "Null")


data class AAOSTask(
    val type: String,
    val tag: String?,
    val target: String? = null,
    val startDate: Long = Long.MIN_VALUE,
    val endDate: Long = Long.MIN_VALUE,
    val QR: String? = null
)

