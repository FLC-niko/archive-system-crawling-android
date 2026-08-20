package com.topviewclub.crawling.wechat.video

import android.os.SystemClock
import com.topviewclub.common.bean.AAOSTask
import com.topviewclub.common.bean.TaskCrawlingType
import com.topviewclub.common.bean.TaskResultType
import com.topviewclub.common.bean.VideoInfo
import com.topviewclub.common.log.logE
import com.topviewclub.common.wirebare.startWechatVideoProxy
import com.topviewclub.common.wirebare.stopWireBareProxy
import com.topviewclub.crawling.service.action.ActionException
import com.topviewclub.crawling.service.wechat.WechatOperationService
import com.topviewclub.crawling.wechat.video.action.*

class VideoOperationService : WechatOperationService() {

    companion object {
        private var tag: String? = null
        private var targetStartDate = Long.MIN_VALUE
        private var targetAccount: String? = null

        /**
         * 开启服务前调用此函数初始化参数
         * */
        fun prepare(
            serviceTag: String?,
            startDate: Long,
            account: String?
        ) {
            tag = serviceTag
            targetStartDate = startDate
            targetAccount = account
            videoURL.clear()
            videoInfoSetInternal.clear()
        }

        private val videoURL = hashMapOf<String, Pair<String, MutableList<Long>>>()

        val videoInfoList: List<VideoInfo> get() = videoInfoSetInternal.toList()
        val videoUrlList: List<String>
            get() {
                val temp = mutableListOf<Pair<String, Double>>()
                videoURL.values.map {
                    temp.add(it.first to it.second.average())
                }
                temp.sortWith { o1, o2 ->
                    (o1.second - o2.second).toInt()
                }
                val res = mutableListOf<String>()
                temp.map { res.add(it.first) }
                return res
            }
    }

    override val crawlServiceType: String = TaskCrawlingType.TYPE_VIDEO

    override val aaosTask: AAOSTask = AAOSTask(
        TaskCrawlingType.TYPE_VIDEO,
        tag,
        targetAccount,
        startDate,
        endDate
    )

    override val target: String
        get() {
            val value = targetAccount
            value ?: throw ActionException(TaskResultType.TARGET_IS_NULL)
            return value
        }

    override val serviceTag: String? get() = tag

    override val firstlyTargetActionName: String = "EnterVideoPreviewList"

    override val wechatChain = listOf(
        EnterVideoPreviewList(),
        EnterVideoList(),
        GetVideoInfo(),
        ScrollVideoList(),
        WriteVideoInfo(),
        ExitVideoList(),
        EnterWechatLauncher()
    )

    override val startDate: Long get() = targetStartDate

    override fun onCreate() {
        super.onCreate()
        addOnServiceDestroyListener {
            stopWireBareProxy()
        }
        startWechatVideoProxy(::insertKV)
    }

    private fun insertKV(urlPair: Pair<String, String>) {
        with(urlPair) {
            val time = SystemClock.uptimeMillis()
            val value = videoURL[first]
            if (value == null) {
                videoURL[first] = second to mutableListOf(time)
            } else if (value.second.size < 5) {
                // 认为前 5 个请求的请求时间才有意义，后续不认为有意义
                // 因为有些视频很长，要多次请求，取太多就不准了
                videoURL[first] = second to value.second.apply { add(time) }
            }
        }
    }

}