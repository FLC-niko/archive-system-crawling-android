package com.topviewclub.crawling.wechat.official

import java.text.SimpleDateFormat
import java.util.*

private val formatterYMD by lazy { SimpleDateFormat("yyyy年M月d日HH:mm:ss.SSS", Locale.getDefault()) }

private val todayLong: Long
    get() {
        // 今天的日期
        val todayStr = "${
            formatterYMD.format(Date()).run {
                substring(0, indexOf("日") + 1)
            }
        }00:00:00.000"
        // 获取今天的第一毫秒，返回空则获取当前时间
        return formatterYMD.parse(todayStr)?.time ?: System.currentTimeMillis()
    }

private val todayWeek by lazy {
    val calendar = Calendar.getInstance()
    calendar.get(Calendar.DAY_OF_WEEK) - 1
}

private const val A_DAY_LONG = 24 * 60 * 60 * 1000L

internal fun officialTimeFormat(s: String): Long {
    var str = s.trim()
    when (str) {
        "今天" -> return todayLong
        "昨天" -> return todayLong - A_DAY_LONG
        "周一" -> return todayLong - ((todayWeek + 6) % 7) * A_DAY_LONG
        "周二" -> return todayLong - ((todayWeek + 5) % 7) * A_DAY_LONG
        "周三" -> return todayLong - ((todayWeek + 4) % 7) * A_DAY_LONG
        "周四" -> return todayLong - ((todayWeek + 3) % 7) * A_DAY_LONG
        "周五" -> return todayLong - ((todayWeek + 2) % 7) * A_DAY_LONG
        "周六" -> return todayLong - ((todayWeek + 1) % 7) * A_DAY_LONG
        "周日" -> return todayLong - todayWeek * A_DAY_LONG
        else -> {
            if (!str.contains("年")) {
                str = "${Calendar.getInstance().get(Calendar.YEAR)}年$str"
            }
            str = "${str}00:00:00.000"
            return formatterYMD.parse(str)?.time ?: System.currentTimeMillis()
        }
    }
}