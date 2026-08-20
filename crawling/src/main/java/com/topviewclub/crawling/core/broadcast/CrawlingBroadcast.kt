package com.topviewclub.crawling.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.network.sendMessageToHostError
import com.topviewclub.common.util.className

/**
 * 用于与主机通信的广播，主要功能见自述文件 README.md 的广播部分
 *
 * @see [Option]
 * */
class CrawlingBroadcast : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            Option[intent.action!!].operation(intent)
        }.onFailure {
            it.printStackTrace()
            // 发生错误时把错误信息发送回主机
            sendMessageToHostError(
                className,
                TaskResultType.PROCESSING_BROADCAST_EXCEPTION,
                "",
                it
            )
        }
    }

}