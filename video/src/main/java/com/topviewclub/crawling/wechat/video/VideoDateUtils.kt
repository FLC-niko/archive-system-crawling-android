package com.topviewclub.crawling.wechat.video

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

private const val A_DAY_LONG = 24 * 60 * 60 * 1000L

internal fun videoTimeFormat(s: String): Long {
    var str = s.trim()
    when (val day = str.lastIndexOf("天")) {
        -1 -> {
            if (str.contains("小时")) {
                return todayLong
            }
            if (str.contains("分钟前")){
                return todayLong
            }
            if (!str.contains("年")) {
                str = "${Calendar.getInstance().get(Calendar.YEAR)}年$str"
            }
            str = "${str}00:00:00.000"
            return formatterYMD.parse(str)?.time ?: System.currentTimeMillis()
        }
        else -> {
            return todayLong - str.substring(0, day).toInt() * A_DAY_LONG
        }
    }
}