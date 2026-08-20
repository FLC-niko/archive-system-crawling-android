package com.topviewclub.crawling.core.broadcast

import android.content.Intent
import com.topviewclub.common.base.appContext
import com.topviewclub.common.base.wechatVideoCacheCaptor
import com.topviewclub.common.bean.TaskStat
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.log.logE
import com.topviewclub.common.log.logI
import com.topviewclub.common.network.sendACVideoToHostAC
import com.topviewclub.common.network.sendHeartBeatToHostHeartbeatOnce
import com.topviewclub.common.network.setIp
import com.topviewclub.common.storage.updateQRCode
import com.topviewclub.common.util.toStringOrEmpty
import com.topviewclub.common.wirebare.prepareProxy
import com.topviewclub.crawling.core.control.TaskDispatcher
import com.topviewclub.crawling.core.ui.CrawlingActivity
import com.topviewclub.crawling.wechat.auto.room.requireACVideo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.github.kokomi.wirebare.common.WireBare
import org.github.kokomi.wirebare.common.WireBare.prepareProxy
import java.text.SimpleDateFormat
import java.util.*

/**
 * 所有广播对应的操作
 * */
sealed class Option(
    val key: String,
    val operation: (Intent) -> Unit
) {

    companion object {
        operator fun get(key: String): Option {
            return optionMap[key]!!
        }

        private val optionMap = hashMapOf(
            StartAAOS.key to StartAAOS,
            UpdateImage.key to UpdateImage,
            Reset.key to Reset,
            IP.key to IP,
            TaskTag.key to TaskTag,
            Target.key to Target,
            StartDate.key to StartDate,
            EndDate.key to EndDate,
            StartCrawling.key to StartCrawling,
            ClearAll.key to ClearAll,
            RequireVideo.key to RequireVideo
        )

        private var target: String? = null
        private var tag: String? = null
        private var startDate: Long = Long.MIN_VALUE
        private var endDate: Long = Long.MAX_VALUE

        const val BROADCAST_START_AAOS = "com.topviewclub.crawling.broadcast.START_AAOS"

        const val BROADCAST_UPDATE_QR_CODE = "com.topviewclub.crawling.broadcast.UPDATE_IMAGE"
        const val BROADCAST_RESET = "com.topviewclub.crawling.broadcast.RESET"
        const val BROADCAST_IP = "com.topviewclub.crawling.broadcast.IP"
        const val BROADCAST_TASK_TAG = "com.topviewclub.crawling.broadcast.TASK_TAG"
        const val BROADCAST_TARGET = "com.topviewclub.crawling.broadcast.TARGET"
        const val BROADCAST_START_DATE = "com.topviewclub.crawling.broadcast.START_DATE"
        const val BROADCAST_END_DATE = "com.topviewclub.crawling.broadcast.END_DATE"
        const val BROADCAST_START_CRAWLING = "com.topviewclub.crawling.broadcast.START_CRAWLING"
        const val BROADCAST_CLEAR_CACHE = "com.topviewclub.crawling.broadcast.CLEAR_WECHAT_CACHE"
        const val BROADCAST_REQUIRE_VIDEO = "com.topviewclub.crawling.broadcast.REQUIRE_VIDEO"
    }

    private object StartAAOS : Option(
        BROADCAST_START_AAOS, {
            TaskStat.clearProcessingTaskListener()
            prepareProxy(CrawlingActivity.activity!!,2222)
            TaskDispatcher.init()
            logI("AAOS Initializer", "AAOS Start Success.")
        }
    )

    /**
     * 更新二维码图片
     * */
    private object UpdateImage : Option(
        BROADCAST_UPDATE_QR_CODE, {
            appContext.updateQRCode()
        }
    )

    /**
     * 重置所有参数
     * */
    private object Reset : Option(
        BROADCAST_RESET, {
            target = null
            startDate = Long.MIN_VALUE
            endDate = Long.MAX_VALUE
        }
    )

    /**
     * 配置主机 IP 地址
     * */
    private object IP : Option(
        BROADCAST_IP, { intent ->
            setIp(intent.getStringExtra("ip")!!)
            sendHeartBeatToHostHeartbeatOnce()
        }
    )

    private object TaskTag : Option(
        BROADCAST_TASK_TAG, { intent ->
            tag = intent.getStringExtra("tag")
        }
    )

    /**
     * 设置抓取的目标账号名
     * */
    private object Target : Option(
        BROADCAST_TARGET, { intent ->
            target = intent.getStringExtra("target")
        }
    )

    /**
     * 设置抓取的起始日期
     * */
    private object StartDate : Option(
        BROADCAST_START_DATE, { intent ->
            startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .parse(intent.getStringExtra("date")!!)!!.time
        }
    )

    /**
     * 设置抓取的结束日期
     * */
    private object EndDate : Option(
        BROADCAST_END_DATE, { intent ->
            endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .parse(intent.getStringExtra("date")!!)!!.time
        }
    )

    /**
     * 开始抓取
     * */
    private object StartCrawling : Option(
        BROADCAST_START_CRAWLING, { intent ->
            val type = intent.getStringExtra("type")!!
//            TaskDispatcher.generateAndEnqueueTask(type, tag, target, startDate, endDate)
        }
    )

    /**
     * 清除所有缓存文件
     * */
    private object ClearAll : Option(
        BROADCAST_CLEAR_CACHE, {
            wechatVideoCacheCaptor.removeAllVideosFromWechat()
        }
    )

    /**
     * 根据提取码请求视频
     * */
    @OptIn(DelicateCoroutinesApi::class)
    private object RequireVideo : Option(
        BROADCAST_REQUIRE_VIDEO, { intent ->
            // 只要存活就返回
            GlobalScope.launch {
                var tc: String? = null
                runCatching {
                    tc = intent.getStringExtra("code")
                    val ac = requireACVideo(tc!!)
                    logI("[ACVideo]", "Code = $tc , Url = ${ac.url}")
                    sendACVideoToHostAC("{url=${ac.url}, title=${ac.title}}")
                }.onFailure {
                    sendACVideoToHostAC(TaskResultType.REQUEST_CODE_EXCEPTION)
                    logE(
                        "ACVideo", "[${tc.toStringOrEmpty()}]" +
                                "Cause = ${it.cause} , Message = ${it.message}"
                    )
                }
            }
        }
    )

}