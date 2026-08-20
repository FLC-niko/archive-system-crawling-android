package com.topviewclub.crawling.xuexi

import java.text.SimpleDateFormat
import java.util.*

private val formatterYMD by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

internal fun xueXiTimeFormat(s: String) =
    formatterYMD.parse(s.trim())?.time ?: System.currentTimeMillis()